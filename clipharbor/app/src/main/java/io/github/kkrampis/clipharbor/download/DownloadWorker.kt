package io.github.kkrampis.clipharbor.download

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import io.github.kkrampis.clipharbor.App
import io.github.kkrampis.clipharbor.container
import io.github.kkrampis.clipharbor.data.DownloadStatus
import io.github.kkrampis.clipharbor.yt.Quality
import io.github.kkrampis.clipharbor.yt.QualityMode
import io.github.kkrampis.clipharbor.yt.YouTube
import kotlinx.coroutines.CancellationException
import java.io.File

class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, -1L)
        val c = applicationContext.container
        val dao = c.db.downloads()
        val item = dao.get(id) ?: return Result.failure()
        if (item.status == DownloadStatus.CANCELLED || item.status == DownloadStatus.DONE) return Result.success()

        runCatching { setForeground(foregroundInfo(id, item.title, 0)) }
        dao.setStatus(id, DownloadStatus.RUNNING, null)
        dao.setProgress(id, DownloadStatus.RUNNING, 0f)

        val workDir = File(applicationContext.cacheDir, "dl-$id").apply { mkdirs() }
        return try {
            // Stream URLs expire after a few hours, so they are looked up fresh on every attempt.
            val details = YouTube.stream(item.pageUrl)
            val option = Quality.match(Quality.options(details), item.mode, item.videoItag, item.height)
                ?: error("This video has no downloadable stream at the chosen quality")

            val parts = buildList {
                option.video?.let { add("video" to it.content) }
                if (option.mode != QualityMode.MUXED) option.audio?.let { add("audio" to it.content) }
            }
            val sizes = parts.map { HttpFetch.contentLength(c.http, it.second) }
            val total = sizes.sum().takeIf { sizes.all { s -> s > 0 } } ?: -1L

            var done = 0L
            var lastUpdate = 0L
            val files = mutableMapOf<String, File>()
            for ((name, url) in parts) {
                val file = File(workDir, "$name.part")
                files[name] = file
                HttpFetch.download(c.http, url, file) { n ->
                    done += n
                    val now = System.currentTimeMillis()
                    if (now - lastUpdate > 700 && total > 0) {
                        lastUpdate = now
                        val p = (done.toFloat() / total).coerceIn(0f, 0.99f)
                        notifyProgress(id, item.title, (p * 100).toInt())
                    }
                }
                if (total > 0) {
                    dao.setProgress(id, DownloadStatus.RUNNING, (done.toFloat() / total).coerceIn(0f, 0.99f))
                }
            }

            dao.setProgress(id, DownloadStatus.PROCESSING, 1f)
            val finalFile = when (option.mode) {
                QualityMode.MERGE -> File(workDir, "merged.mp4").also {
                    it.delete()
                    Muxer.merge(files.getValue("video"), files.getValue("audio"), it)
                }
                QualityMode.MUXED -> files.getValue("video")
                QualityMode.AUDIO -> files.getValue("audio")
            }

            val uri = Storage.save(
                applicationContext,
                finalFile,
                item.title,
                item.folder,
                audio = option.mode == QualityMode.AUDIO,
            )
            val latest = dao.get(id) ?: item
            dao.update(
                latest.copy(
                    status = DownloadStatus.DONE,
                    progress = 1f,
                    fileUri = uri.toString(),
                    error = null,
                    qualityLabel = option.label,
                ),
            )
            workDir.deleteRecursively()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            dao.setStatus(id, DownloadStatus.FAILED, e.message ?: e.javaClass.simpleName)
            Result.failure()
        }
    }

    private var lastProgressWrite = 0L

    private suspend fun notifyProgress(id: Long, title: String, percent: Int) {
        runCatching { setForeground(foregroundInfo(id, title, percent)) }
        val now = System.currentTimeMillis()
        if (now - lastProgressWrite > 1500) {
            lastProgressWrite = now
            applicationContext.container.db.downloads()
                .setProgress(id, DownloadStatus.RUNNING, percent / 100f)
        }
    }

    private fun foregroundInfo(id: Long, title: String, percent: Int): ForegroundInfo {
        val notification: Notification = NotificationCompat.Builder(applicationContext, App.DOWNLOAD_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText("Downloading · $percent%")
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        val notificationId = (id % Int.MAX_VALUE).toInt() + 1000
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    companion object {
        const val KEY_ID = "download_id"
        const val TAG = "downloads"
    }
}
