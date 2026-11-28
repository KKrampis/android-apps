package io.github.kkrampis.radiorecorder.engine

import android.util.Log
import io.github.kkrampis.radiorecorder.data.RecStatus
import io.github.kkrampis.radiorecorder.data.Recording
import org.json.JSONObject
import java.io.File

data class CleanupResult(
    val finishedMs: Long,
    val uploaded: Int,
    val uploadFailed: Int,
    val deleted: Int,
    val freedBytes: Long,
    val keptUnuploaded: Int,
    val emergencyDeleted: Int,
)

/**
 * Keeps local storage in check: first uploads everything pending, then deletes local copies
 * older than the retention time that are safely on archive.org (or all old ones when uploading
 * is off). A free-space guard deletes the oldest uploaded files early when storage runs low.
 */
class Cleaner(private val engine: Engine) {
    private val lock = Any()

    val lastRunMs: Long get() = engine.db.kvGet("cleanup_last_run")?.toLongOrNull() ?: 0L

    fun nextRunMs(): Long {
        val last = lastRunMs.takeIf { it > 0 } ?: System.currentTimeMillis()
        return last + (engine.config.current.cleanup.intervalHours * 3600_000).toLong()
    }

    fun lastResult(): CleanupResult? = engine.db.kvGet("cleanup_last_result")?.let {
        runCatching {
            val o = JSONObject(it)
            CleanupResult(
                o.getLong("finished"), o.getInt("uploaded"), o.getInt("upload_failed"), o.getInt("deleted"),
                o.getLong("freed_bytes"), o.getInt("kept_unuploaded"), o.optInt("emergency_deleted"),
            )
        }.getOrNull()
    }

    fun freeBytes(): Long = engine.recordingsDir.usableSpace

    /** Runs the full cleanup if it's due (or [force]). Called every few minutes by the engine. */
    fun tick(force: Boolean = false) {
        if (lastRunMs == 0L) engine.db.kvSet("cleanup_last_run", System.currentTimeMillis().toString())
        if (force || System.currentTimeMillis() >= nextRunMs()) run() else diskGuard()
    }

    fun run(): CleanupResult = synchronized(lock) {
        val c = engine.config.current.cleanup
        val archiveOn = engine.uploader.enabled()
        // 1. Upload first, so nothing that could be archived is deleted.
        val up = if (archiveOn) engine.uploader.processPending() else Uploader.Result(0, 0, 0)
        val now = System.currentTimeMillis()
        val cutoff = now - (c.retentionHours * 3600_000).toLong()
        val unuploadedCutoff = if (c.deleteUnuploadedAfterHours > 0) now - (c.deleteUnuploadedAfterHours * 3600_000).toLong() else null
        var deleted = 0
        var freed = 0L
        var kept = 0
        for (r in engine.db.byStatus(RecStatus.UPLOADED, RecStatus.DONE, RecStatus.FAILED)) {
            val end = r.endMs ?: r.startMs
            if (end > cutoff) continue
            val st = engine.config.station(r.stationId)
            val stationUploads = archiveOn && (st == null || st.archive.enabled)
            when {
                r.status == RecStatus.UPLOADED || !stationUploads -> {
                    freed += delete(r, "retention"); deleted++
                }
                unuploadedCutoff != null && end < unuploadedCutoff -> {
                    freed += delete(r, "upload kept failing"); deleted++
                }
                else -> kept++
            }
        }
        val emergency = diskGuard()
        val result = CleanupResult(System.currentTimeMillis(), up.uploaded, up.failed, deleted, freed, kept, emergency)
        engine.db.kvSet("cleanup_last_run", System.currentTimeMillis().toString())
        engine.db.kvSet(
            "cleanup_last_result",
            JSONObject().put("finished", result.finishedMs).put("uploaded", result.uploaded).put("upload_failed", result.uploadFailed)
                .put("deleted", result.deleted).put("freed_bytes", result.freedBytes).put("kept_unuploaded", result.keptUnuploaded)
                .put("emergency_deleted", result.emergencyDeleted).toString(),
        )
        Log.i(TAG, "cleanup: $result")
        result
    }

    /** Deletes the oldest files that are safe to delete while free space is below the minimum. */
    fun diskGuard(): Int {
        val minFree = (engine.config.current.cleanup.minFreeGb * 1e9).toLong()
        if (minFree <= 0 || freeBytes() >= minFree) return 0
        val candidates = engine.db.byStatus(RecStatus.UPLOADED).toMutableList()
        if (!engine.uploader.enabled()) candidates += engine.db.byStatus(RecStatus.DONE, RecStatus.FAILED)
        var n = 0
        for (r in candidates.sortedBy { it.startMs }) {
            if (freeBytes() >= minFree) break
            delete(r, "low storage")
            n++
        }
        return n
    }

    private fun delete(r: Recording, why: String): Long {
        val f = r.file
        val size = if (f.exists()) f.length() else 0L
        if (f.exists() && !f.delete()) return 0
        engine.db.update(r.id) {
            put("status", RecStatus.DELETED.name)
            put("deleted_ms", System.currentTimeMillis())
            if (r.status != RecStatus.UPLOADED) put("last_error", "deleted locally ($why) without upload")
        }
        prune(f.parentFile)
        return size
    }

    private fun prune(dir: File?) {
        var d = dir ?: return
        val root = engine.recordingsDir.canonicalFile
        while (d.canonicalFile != root && d.canonicalPath.startsWith(root.canonicalPath) && (d.list()?.isEmpty() == true)) {
            d.delete()
            d = d.parentFile ?: return
        }
    }

    private companion object {
        const val TAG = "RadioRecorder"
    }
}
