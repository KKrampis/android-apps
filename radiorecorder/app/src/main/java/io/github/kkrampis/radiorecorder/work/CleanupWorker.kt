package io.github.kkrampis.radiorecorder.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.github.kkrampis.radiorecorder.engine.Engine
import java.util.concurrent.TimeUnit

/**
 * Backup for when the recording service isn't running (e.g. every station is disabled):
 * uploads anything pending and runs the cleanup when it's due.
 */
class CleanupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val engine = Engine.get(applicationContext)
        return try {
            engine.uploader.processPending()
            engine.cleaner.tick()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<CleanupWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("cleanup", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
