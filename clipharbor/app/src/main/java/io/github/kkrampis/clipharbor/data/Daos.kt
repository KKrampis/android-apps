package io.github.kkrampis.clipharbor.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVideo(video: VideoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaved(saved: SavedEntity)

    @Query("DELETE FROM saved WHERE url = :url")
    suspend fun deleteSaved(url: String)

    @Query("SELECT EXISTS(SELECT 1 FROM saved WHERE url = :url)")
    fun isSaved(url: String): Flow<Boolean>

    @Query("SELECT v.* FROM videos v INNER JOIN saved s ON s.url = v.url ORDER BY s.savedAt DESC")
    fun savedVideos(): Flow<List<VideoEntity>>

    @Query("SELECT v.* FROM videos v INNER JOIN saved s ON s.url = v.url ORDER BY s.savedAt DESC")
    suspend fun savedVideosOnce(): List<VideoEntity>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String, now: Long)

    @Query("UPDATE playlists SET updatedAt = :now WHERE id = :id")
    suspend fun touchPlaylist(id: Long, now: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun playlist(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE name = :name LIMIT 1")
    suspend fun playlistByName(name: String): PlaylistEntity?

    @Query("SELECT * FROM playlists ORDER BY createdAt")
    suspend fun playlistsOnce(): List<PlaylistEntity>

    @Query(
        """
        SELECT p.id, p.name, p.createdAt, p.updatedAt,
            (SELECT COUNT(*) FROM playlist_items i WHERE i.playlistId = p.id) AS count,
            (SELECT v.thumbnail FROM playlist_items i INNER JOIN videos v ON v.url = i.url
                WHERE i.playlistId = p.id ORDER BY i.position LIMIT 1) AS thumbnail
        FROM playlists p ORDER BY p.updatedAt DESC
        """,
    )
    fun playlistSummaries(): Flow<List<PlaylistSummary>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_items WHERE playlistId = :id")
    suspend fun maxPosition(id: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: PlaylistItemEntity): Long

    @Query("DELETE FROM playlist_items WHERE playlistId = :id AND url = :url")
    suspend fun deleteItem(id: Long, url: String)

    @Query("SELECT playlistId FROM playlist_items WHERE url = :url")
    fun playlistIdsContaining(url: String): Flow<List<Long>>

    @Query(
        """
        SELECT v.* FROM videos v INNER JOIN playlist_items i ON i.url = v.url
        WHERE i.playlistId = :id ORDER BY i.position
        """,
    )
    fun playlistVideos(id: Long): Flow<List<VideoEntity>>

    @Query(
        """
        SELECT v.* FROM videos v INNER JOIN playlist_items i ON i.url = v.url
        WHERE i.playlistId = :id ORDER BY i.position
        """,
    )
    suspend fun playlistVideosOnce(id: Long): List<VideoEntity>
}

@Dao
interface DownloadDao {
    @Insert
    suspend fun insert(download: DownloadEntity): Long

    @Update
    suspend fun update(download: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun get(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE id = :id")
    fun observe(id: Long): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads ORDER BY folder COLLATE NOCASE, createdAt DESC")
    fun all(): Flow<List<DownloadEntity>>

    @Query("SELECT DISTINCT folder FROM downloads ORDER BY folder COLLATE NOCASE")
    fun folders(): Flow<List<String>>

    @Query("UPDATE downloads SET status = :status, progress = :progress WHERE id = :id")
    suspend fun setProgress(id: Long, status: String, progress: Float)

    @Query("UPDATE downloads SET status = :status, error = :error WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, error: String?)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM downloads WHERE pageUrl = :url AND status = 'done' AND fileUri IS NOT NULL ORDER BY createdAt DESC LIMIT 1")
    suspend fun finishedFor(url: String): DownloadEntity?
}

@Dao
interface TranscriptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(t: TranscriptEntity)

    @Query("SELECT * FROM transcripts WHERE url = :url")
    suspend fun get(url: String): TranscriptEntity?

    @Query("SELECT * FROM transcripts WHERE url = :url")
    fun observe(url: String): Flow<TranscriptEntity?>

    @Query("UPDATE transcripts SET status = :status, progress = :progress WHERE url = :url")
    suspend fun setProgress(url: String, status: String, progress: Float)

    @Query("UPDATE transcripts SET status = :status, error = :error WHERE url = :url")
    suspend fun setStatus(url: String, status: String, error: String?)

    @Query("DELETE FROM transcripts WHERE url = :url")
    suspend fun delete(url: String)
}
