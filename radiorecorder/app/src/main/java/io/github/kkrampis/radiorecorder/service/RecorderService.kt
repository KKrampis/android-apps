package io.github.kkrampis.radiorecorder.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.kkrampis.radiorecorder.MainActivity
import io.github.kkrampis.radiorecorder.R
import io.github.kkrampis.radiorecorder.engine.Engine
import io.github.kkrampis.radiorecorder.engine.EngineStatus
import io.github.kkrampis.radiorecorder.engine.RecState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Keeps recording while the app is closed and the screen is off: a foreground service with a
 * persistent notification, a CPU wake lock and a Wi-Fi lock while any station is recording, and an
 * exact alarm for the next scheduled start so recordings begin on time even in Doze.
 */
class RecorderService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var engine: Engine
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var lastAlarmAt: Long = -1
    private var lastText = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        engine = Engine.get(this)
        createChannel(this)
        goForeground("Starting…")
        engine.start()
        scope.launch {
            engine.status.collectLatest { st -> onStatus(st) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                engine.config.update { it.copy(paused = true) }
                stopSelf()
                return START_NOT_STICKY
            }
            else -> engine.poke()
        }
        if (!engine.hasEnabledStations() || engine.paused) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        releaseLocks()
        // Stopping joins the station threads (closing their files); keep it off the main thread.
        Thread({ engine.stop() }, "engine-stop").start()
        super.onDestroy()
    }

    private fun onStatus(st: EngineStatus) {
        val recording = st.stations.values.filter { it.state == RecState.RECORDING }
        if (recording.isNotEmpty()) acquireLocks() else releaseLocks()
        scheduleNextAlarm()

        val names = engine.config.current.stations.associate { it.id to it.name }
        val text = when {
            recording.isNotEmpty() -> st.stations.filter { it.value.state == RecState.RECORDING }.entries.joinToString(" · ") { (id, s) ->
                listOfNotNull(names[id], s.program ?: s.title).joinToString(": ")
            }
            else -> engine.nextScheduledStart()?.let {
                "Waiting. Next recording " + it.withZoneSameInstant(java.time.ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
            } ?: st.stations.values.firstOrNull { it.state == RecState.RETRYING }?.let { "Reconnecting: ${it.error}" }
                ?: "Idle"
        }
        val title = if (recording.isEmpty()) "Radio Recorder" else "Recording ${recording.size} station" + if (recording.size > 1) "s" else ""
        if (text + title != lastText) {
            lastText = text + title
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(title, text))
        }
    }

    private fun goForeground(text: String) {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification("Radio Recorder", text), type)
    }

    private fun notification(title: String, text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RecorderService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, "Stop recording", stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun acquireLocks() {
        if (wakeLock?.isHeld != true) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RadioRecorder:recording").apply {
                    setReferenceCounted(false)
                    acquire()
                }
        }
        if (wifiLock?.isHeld != true) {
            @Suppress("DEPRECATION")
            val mode = if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_HIGH_PERF else WifiManager.WIFI_MODE_FULL
            wifiLock = applicationContext.getSystemService(WifiManager::class.java)
                ?.createWifiLock(mode, "RadioRecorder:recording")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
        }
    }

    private fun releaseLocks() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
    }

    private fun scheduleNextAlarm() {
        val next = engine.nextScheduledStart()?.toInstant()?.toEpochMilli() ?: 0L
        if (next == lastAlarmAt) return
        lastAlarmAt = next
        Alarms.schedule(this, next)
    }

    companion object {
        const val CHANNEL = "recording"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "io.github.kkrampis.radiorecorder.STOP"

        fun createChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Recording", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows what is being recorded"
                    setShowBadge(false)
                },
            )
        }

        /** Starts the service if there's anything to record. */
        fun ensure(context: Context) {
            val engine = Engine.get(context)
            if (engine.hasEnabledStations() && !engine.paused) {
                try {
                    ContextCompat.startForegroundService(context, Intent(context, RecorderService::class.java))
                } catch (_: Exception) {
                    // Not allowed from this background context; the alarm or next app launch retries.
                }
            } else {
                context.stopService(Intent(context, RecorderService::class.java))
            }
        }
    }
}

object Alarms {
    /** Exact alarm at [atMs] (0 = cancel) that starts the service for a scheduled recording. */
    fun schedule(context: Context, atMs: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context, 0, Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        am.cancel(pi)
        if (atMs <= 0) return
        val exactOk = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exactOk) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
        }
    }
}
