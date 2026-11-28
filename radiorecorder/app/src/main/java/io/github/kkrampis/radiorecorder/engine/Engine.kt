package io.github.kkrampis.radiorecorder.engine

import android.content.Context
import android.os.Environment
import io.github.kkrampis.radiorecorder.core.Schedule
import io.github.kkrampis.radiorecorder.data.ConfigStore
import io.github.kkrampis.radiorecorder.data.RecordingsDb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import java.io.File
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class EngineStatus(
    val running: Boolean = false,
    val stations: Map<String, RecorderStatus> = emptyMap(),
    val upload: UploadProgress? = null,
    val uploadError: String? = null,
    val freeBytes: Long = 0,
    val lastCleanupMs: Long = 0,
    val nextCleanupMs: Long = 0,
    val lastCleanup: CleanupResult? = null,
    val tick: Long = 0,
) {
    val recordingCount: Int get() = stations.values.count { it.state == RecState.RECORDING }
}

/**
 * Process-wide recorder. [RecorderService] starts it and keeps the process alive in the
 * foreground; the UI only reads [status] and changes settings through [config].
 */
class Engine private constructor(private val app: Context) {
    val config = ConfigStore(app)
    val db = RecordingsDb(app)
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    val recordingsDir: File =
        File(app.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: File(app.filesDir, "Music"), "RadioRecorder").apply { mkdirs() }
    val uploader = Uploader(this, app)
    val cleaner = Cleaner(this)

    private val recorders = ConcurrentHashMap<String, StationRecorder>()
    private val running = AtomicBoolean(false)
    private val _status = MutableStateFlow(EngineStatus())
    val status: StateFlow<EngineStatus> = _status
    private var ticker: Thread? = null
    private var background: Thread? = null
    private val bgWake = Object()

    val paused: Boolean get() = config.current.paused

    init {
        db.recoverInterrupted()
        refreshStatus()
    }

    @Synchronized
    fun start() {
        if (!running.compareAndSet(false, true)) return
        syncRecorders()
        ticker = Thread({
            var n = 0L
            while (running.get()) {
                syncRecorders()
                refreshStatus(++n)
                try {
                    Thread.sleep(1000)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }, "engine-ticker").apply { isDaemon = true; start() }
        background = Thread({
            // Uploads as recordings finish, retries, and the periodic cleanup.
            while (running.get()) {
                try {
                    if (config.current.archive.uploadOnFinish) uploader.processPending()
                    cleaner.tick()
                } catch (_: Exception) {
                }
                synchronized(bgWake) {
                    try {
                        bgWake.wait(5 * 60_000L)
                    } catch (_: InterruptedException) {
                    }
                }
            }
        }, "engine-background").apply { isDaemon = true; start() }
    }

    @Synchronized
    fun stop() {
        if (!running.compareAndSet(true, false)) return
        recorders.values.forEach { it.shutdown() }
        recorders.values.forEach { it.join(10_000) }
        recorders.clear()
        ticker?.interrupt()
        background?.interrupt()
        refreshStatus()
    }

    val isRunning: Boolean get() = running.get()

    /** Starts, updates or stops station threads to match the settings. */
    @Synchronized
    fun syncRecorders() {
        if (!running.get()) return
        val stations = config.current.stations.associateBy { it.id }
        for ((id, rec) in recorders) {
            val st = stations[id]
            if (st == null) {
                rec.shutdown()
                recorders.remove(id)
            } else if (st != rec.station) {
                rec.updateStation(st)
            }
        }
        for ((id, st) in stations) {
            if (!recorders.containsKey(id)) recorders[id] = StationRecorder(this, st).also { it.start() }
        }
    }

    /** Wake every station thread (after an alarm, resume, or a settings change). */
    fun poke() {
        recorders.values.forEach { it.poke() }
        pokeBackground()
    }

    fun pokeBackground() = synchronized(bgWake) { bgWake.notifyAll() }

    fun splitNow(stationId: String) = recorders[stationId]?.splitNow()

    fun onSegmentFinished() {
        if (config.current.archive.uploadOnFinish) pokeBackground()
    }

    /** Earliest upcoming scheduled start among stations that are waiting. */
    fun nextScheduledStart(): ZonedDateTime? {
        if (paused) return null
        return config.current.stations.filter { it.enabled }
            .mapNotNull { st -> Schedule.evaluate(st).takeIf { !it.record }?.boundary }
            .minByOrNull { it.toInstant() }
    }

    fun hasEnabledStations(): Boolean = config.current.stations.any { it.enabled }

    fun refreshStatus(tick: Long = _status.value.tick) {
        _status.value = EngineStatus(
            running = running.get(),
            stations = recorders.mapValues { it.value.status },
            upload = uploader.current,
            uploadError = uploader.lastError,
            freeBytes = cleaner.freeBytes(),
            lastCleanupMs = cleaner.lastRunMs,
            nextCleanupMs = cleaner.nextRunMs(),
            lastCleanup = cleaner.lastResult(),
            tick = tick,
        )
    }

    companion object {
        @Volatile private var instance: Engine? = null

        fun get(context: Context): Engine =
            instance ?: synchronized(this) { instance ?: Engine(context.applicationContext).also { instance = it } }
    }
}
