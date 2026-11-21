package io.github.kkrampis.clipharbor.download

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.kkrampis.clipharbor.data.AppDatabase
import io.github.kkrampis.clipharbor.data.DownloadEntity
import io.github.kkrampis.clipharbor.data.DownloadStatus
import io.github.kkrampis.clipharbor.yt.QualityOption
import io.github.kkrampis.clipharbor.yt.VideoItem
import kotlinx.coroutines.flow.Flow
import java.io.File

class DownloadRepository(private val context: Context, db: AppDatabase) {
    private val dao = db.downloads()
    private val work get() = WorkManager.getInstance(context)

    val all: Flow<List<DownloadEntity>> = dao.all()
    val folders: Flow<List<String>> = dao.folders()

    fun observe(id: Long): Flow<DownloadEntity?> = dao.observe(id)

    suspend fun enqueue(video: VideoItem, option: QualityOption, folder: String): Long {
        val entity = DownloadEntity(
            pageUrl = video.url,
            title = video.title,
            uploader = video.uploader,
            thumbnail = video.thumbnail,
            folder = Storage.cleanFolder(folder),
            mode = option.mode.name,
            videoItag = option.videoItag,
            audioItag = option.audioItag,
            height = option.height,
            qualityLabel = option.label,
            status = DownloadStatus.QUEUED,
            progress = 0f,
            fileUri = null,
            error = null,
            createdAt = System.currentTimeMillis(),
        )
        val id = dao.insert(entity)
        start(id)
        return id
    }

    private fun start(id: Long) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_ID to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(DownloadWorker.TAG)
            .build()
        work.enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request)
    }

    suspend fun retry(id: Long) {
        dao.setStatus(id, DownloadStatus.QUEUED, null)
        dao.setProgress(id, DownloadStatus.QUEUED, 0f)
        start(id)
    }

    suspend fun cancel(id: Long) {
        dao.setStatus(id, DownloadStatus.CANCELLED, null)
        work.cancelUniqueWork(workName(id))
        File(context.cacheDir, "dl-$id").deleteRecursively()
    }

    /** Removes the entry and the saved file. */
    suspend fun delete(item: DownloadEntity) {
        work.cancelUniqueWork(workName(item.id))
        File(context.cacheDir, "dl-${item.id}").deleteRecursively()
        item.fileUri?.let { Storage.delete(context, Uri.parse(it)) }
        dao.delete(item.id)
    }

    companion object {
        fun workName(id: Long) = "download-$id"
    }
}
