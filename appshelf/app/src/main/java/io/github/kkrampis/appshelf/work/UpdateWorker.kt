package io.github.kkrampis.appshelf.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.kkrampis.appshelf.MainActivity
import io.github.kkrampis.appshelf.R
import io.github.kkrampis.appshelf.data.Store
import java.util.concurrent.TimeUnit

/** Checks every repository for new versions of installed apps and posts one notification. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = Store(applicationContext)
        val states = store.load()
        val updates = store.rows(states).filter { it.hasUpdate }
        val already = store.settings.notified
        val tags = updates.map { "${it.app.id}@${it.app.latest.version}" }.toSet()
        val fresh = updates.filter { "${it.app.id}@${it.app.latest.version}" !in already }
        // Forget announced versions once they are installed, so the set stays small.
        store.settings.notified = already.intersect(tags) + tags

        if (fresh.isNotEmpty()) {
            val names = updates.joinToString(", ") { "${it.app.name} ${it.app.latest.version}" }
            notify(
                applicationContext,
                title = if (updates.size == 1) "Update available" else "${updates.size} updates available",
                text = names,
            )
        }
        return if (states.all { it.error != null } && states.isNotEmpty()) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK = "update-check"
        private const val CHANNEL = "updates"
        const val EXTRA_SHOW_UPDATES = "io.github.kkrampis.appshelf.SHOW_UPDATES"

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(WORK)
                return
            }
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val channel = NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT)
                channel.description = "New versions of apps installed from your GitHub repositories"
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            }
        }

        private fun notify(context: Context, title: String, text: String) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java)
                    .putExtra(EXTRA_SHOW_UPDATES, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(1, n)
            } catch (e: SecurityException) {
                // Notifications were turned off; the Updates filter in the app still shows them.
            }
        }
    }
}
