package io.github.kkrampis.radiorecorder.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import io.github.kkrampis.radiorecorder.engine.Engine

/** Restarts recording after the phone reboots or the app is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                RecorderService.ensure(context)
        }
    }
}

/** Fires at the next scheduled start: makes sure the service runs and wakes the station threads. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Keep the CPU awake long enough for the stream to connect; once recording starts the
        // service holds its own wake lock.
        context.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RadioRecorder:alarm")
            ?.acquire(90_000L)
        RecorderService.ensure(context)
        Engine.get(context).poke()
    }
}
