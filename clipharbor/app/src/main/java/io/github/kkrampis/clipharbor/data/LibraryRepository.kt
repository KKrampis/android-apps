package io.github.kkrampis.clipharbor.data

import androidx.room.withTransaction
import io.github.kkrampis.clipharbor.yt.VideoItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/** Saved videos and playlists, plus the JSON format used for sync. */
class LibraryRepository(
    private val db: AppDatabase,
    private val onChanged: () -> Unit,
) {
    private val dao = db.library()

    val saved: Flow<List<VideoItem>> = dao.savedVideos().map { list -> list.map { it.toItem() } }
    val playlists: Flow<List<PlaylistSummary>> = dao.playlistSummaries()

    fun isSaved(url: String): Flow<Boolean> = dao.isSaved(url)
    fun playlist(id: Long): Flow<PlaylistEntity?> = dao.playlist(id)
    fun playlistVideos(id: Long): Flow<List<VideoItem>> =
        dao.playlistVideos(id).map { list -> list.map { it.toItem() } }
    fun playlistIdsContaining(url: String): Flow<List<Long>> = dao.playlistIdsContaining(url)

    suspend fun setSaved(video: VideoItem, saved: Boolean) {
        if (saved) {
            dao.upsertVideo(video.toEntity())
            dao.insertSaved(SavedEntity(video.url, System.currentTimeMillis()))
        } else {
            dao.deleteSaved(video.url)
        }
        onChanged()
    }

    suspend fun createPlaylist(name: String): Long {
        val now = System.currentTimeMillis()
        val id = dao.insertPlaylist(PlaylistEntity(name = name.trim(), createdAt = now, updatedAt = now))
        onChanged()
        return id
    }

    suspend fun renamePlaylist(id: Long, name: String) {
        dao.renamePlaylist(id, name.trim(), System.currentTimeMillis())
        onChanged()
    }

    suspend fun deletePlaylist(id: Long) {
        dao.deletePlaylist(id)
        onChanged()
    }

    suspend fun addToPlaylist(id: Long, video: VideoItem) {
        db.withTransaction {
            dao.upsertVideo(video.toEntity())
            val pos = dao.maxPosition(id) + 1
            dao.insertItem(PlaylistItemEntity(id, video.url, pos, System.currentTimeMillis()))
            dao.touchPlaylist(id, System.currentTimeMillis())
        }
        onChanged()
    }

    suspend fun removeFromPlaylist(id: Long, url: String) {
        dao.deleteItem(id, url)
        dao.touchPlaylist(id, System.currentTimeMillis())
        onChanged()
    }

    /** Everything the user curated, as a self-describing JSON document. */
    suspend fun exportJson(): JSONObject {
        val playlists = JSONArray()
        for (p in dao.playlistsOnce()) {
            val videos = JSONArray()
            dao.playlistVideosOnce(p.id).forEach { videos.put(it.toJson()) }
            playlists.put(
                JSONObject()
                    .put("name", p.name)
                    .put("createdAt", p.createdAt)
                    .put("updatedAt", p.updatedAt)
                    .put("videos", videos),
            )
        }
        val saved = JSONArray()
        dao.savedVideosOnce().forEach { saved.put(it.toJson()) }
        return JSONObject()
            .put("app", "ClipHarbor")
            .put("formatVersion", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("playlists", playlists)
            .put("saved", saved)
    }

    /**
     * Merges a document produced by [exportJson]. Playlists are matched by name;
     * videos are added if missing, nothing is deleted.
     */
    suspend fun importJson(doc: JSONObject): Int {
        var added = 0
        db.withTransaction {
            val now = System.currentTimeMillis()
            val playlists = doc.optJSONArray("playlists") ?: JSONArray()
            for (i in 0 until playlists.length()) {
                val p = playlists.getJSONObject(i)
                val name = p.optString("name").trim()
                if (name.isEmpty()) continue
                val id = dao.playlistByName(name)?.id
                    ?: dao.insertPlaylist(
                        PlaylistEntity(name = name, createdAt = p.optLong("createdAt", now), updatedAt = now),
                    )
                val videos = p.optJSONArray("videos") ?: JSONArray()
                var pos = dao.maxPosition(id) + 1
                for (j in 0 until videos.length()) {
                    val v = videoFromJson(videos.getJSONObject(j)) ?: continue
                    dao.upsertVideo(v)
                    if (dao.insertItem(PlaylistItemEntity(id, v.url, pos, now)) != -1L) {
                        pos++
                        added++
                    }
                }
            }
            val saved = doc.optJSONArray("saved") ?: JSONArray()
            for (j in 0 until saved.length()) {
                val v = videoFromJson(saved.getJSONObject(j)) ?: continue
                dao.upsertVideo(v)
                dao.insertSaved(SavedEntity(v.url, now))
            }
        }
        return added
    }

    private fun VideoEntity.toJson() = JSONObject()
        .put("url", url)
        .put("title", title)
        .put("uploader", uploader)
        .put("uploaderUrl", uploaderUrl ?: JSONObject.NULL)
        .put("thumbnail", thumbnail ?: JSONObject.NULL)
        .put("durationSec", durationSec)

    private fun videoFromJson(o: JSONObject): VideoEntity? {
        val url = o.optString("url").ifBlank { return null }
        return VideoEntity(
            url = url,
            title = o.optString("title"),
            uploader = o.optString("uploader"),
            uploaderUrl = o.optString("uploaderUrl").takeIf { it.isNotBlank() && it != "null" },
            thumbnail = o.optString("thumbnail").takeIf { it.isNotBlank() && it != "null" },
            durationSec = o.optLong("durationSec", -1),
        )
    }
}

fun VideoEntity.toItem() = VideoItem(
    url = url,
    title = title,
    uploader = uploader,
    uploaderUrl = uploaderUrl,
    thumbnail = thumbnail,
    durationSec = durationSec,
)

fun VideoItem.toEntity() = VideoEntity(
    url = url,
    title = title,
    uploader = uploader,
    uploaderUrl = uploaderUrl,
    thumbnail = thumbnail,
    durationSec = durationSec,
)
