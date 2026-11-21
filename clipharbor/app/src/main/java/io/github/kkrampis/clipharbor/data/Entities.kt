package io.github.kkrampis.clipharbor.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val url: String,
    val title: String,
    val uploader: String,
    val uploaderUrl: String?,
    val thumbnail: String?,
    val durationSec: Long,
)

@Entity(tableName = "saved")
data class SavedEntity(
    @PrimaryKey val url: String,
    val savedAt: Long,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "url"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId")],
)
data class PlaylistItemEntity(
    val playlistId: Long,
    val url: String,
    val position: Int,
    val addedAt: Long,
)

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val count: Int,
    val thumbnail: String?,
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pageUrl: String,
    val title: String,
    val uploader: String,
    val thumbnail: String?,
    val folder: String,
    /** One of [io.github.kkrampis.clipharbor.yt.QualityMode] names. */
    val mode: String,
    val videoItag: Int,
    val audioItag: Int,
    val height: Int,
    val qualityLabel: String,
    val status: String,
    val progress: Float,
    val fileUri: String?,
    val error: String?,
    val createdAt: Long,
)

object DownloadStatus {
    const val QUEUED = "queued"
    const val RUNNING = "running"
    const val PROCESSING = "processing"
    const val DONE = "done"
    const val FAILED = "failed"
    const val CANCELLED = "cancelled"
}
