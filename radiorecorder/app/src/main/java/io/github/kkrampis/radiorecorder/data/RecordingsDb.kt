package io.github.kkrampis.radiorecorder.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class RecStatus { RECORDING, DONE, UPLOADING, UPLOADED, FAILED, DELETED }

data class TitleMark(val atSeconds: Double, val title: String)

data class Recording(
    val id: Long,
    val stationId: String,
    val stationName: String,
    val path: String,
    val startMs: Long,
    val endMs: Long?,
    /** ISO time in the station's zone. */
    val startLocal: String,
    val program: String?,
    val titles: List<TitleMark>,
    val format: String?,
    val bytes: Long,
    val splitReason: String?,
    val status: RecStatus,
    val iaIdentifier: String?,
    val iaUrl: String?,
    val uploadAttempts: Int,
    val lastAttemptMs: Long?,
    val lastError: String?,
) {
    val file: File get() = File(path)
    val hasLocalFile: Boolean get() = status != RecStatus.DELETED && status != RecStatus.RECORDING && file.exists()
}

/** Catalogue of recordings and their upload state (same columns as the desktop app). */
class RecordingsDb(context: Context) : SQLiteOpenHelper(context, "recordings.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE recordings (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              station_id TEXT NOT NULL,
              station_name TEXT NOT NULL,
              path TEXT NOT NULL,
              start_ms INTEGER NOT NULL,
              end_ms INTEGER,
              start_local TEXT NOT NULL,
              program TEXT,
              titles TEXT NOT NULL DEFAULT '[]',
              format TEXT,
              bytes INTEGER NOT NULL DEFAULT 0,
              split_reason TEXT,
              status TEXT NOT NULL,
              ia_identifier TEXT,
              ia_url TEXT,
              upload_attempts INTEGER NOT NULL DEFAULT 0,
              last_attempt_ms INTEGER,
              last_error TEXT,
              deleted_ms INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_rec_status ON recordings(status)")
        db.execSQL("CREATE TABLE kv (k TEXT PRIMARY KEY, v TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized
    fun insertStart(stationId: String, stationName: String, path: String, startMs: Long, startLocal: String, program: String?, format: String): Long {
        val v = ContentValues().apply {
            put("station_id", stationId); put("station_name", stationName); put("path", path)
            put("start_ms", startMs); put("start_local", startLocal); put("program", program)
            put("format", format); put("status", RecStatus.RECORDING.name)
        }
        return writableDatabase.insert("recordings", null, v)
    }

    @Synchronized
    fun finish(id: Long, endMs: Long, bytes: Long, titles: List<TitleMark>, reason: String, program: String?) {
        val arr = JSONArray(titles.map { JSONObject().put("t", it.atSeconds).put("title", it.title) })
        update(id) {
            put("end_ms", endMs); put("bytes", bytes); put("titles", arr.toString())
            put("split_reason", reason); put("program", program); put("status", RecStatus.DONE.name)
        }
    }

    @Synchronized
    fun update(id: Long, fill: ContentValues.() -> Unit) {
        writableDatabase.update("recordings", ContentValues().apply(fill), "id=?", arrayOf(id.toString()))
    }

    fun setStatus(id: Long, status: RecStatus, error: String? = null) = update(id) {
        put("status", status.name); put("last_error", error)
    }

    @Synchronized
    fun delete(id: Long) {
        writableDatabase.delete("recordings", "id=?", arrayOf(id.toString()))
    }

    fun get(id: Long): Recording? = query("id=?", arrayOf(id.toString())).firstOrNull()

    fun recent(limit: Int = 300, stationId: String? = null): List<Recording> =
        if (stationId == null) query(null, null, "start_ms DESC", limit)
        else query("station_id=?", arrayOf(stationId), "start_ms DESC", limit)

    fun byStatus(vararg s: RecStatus): List<Recording> =
        query("status IN (${s.joinToString(",") { "?" }})", s.map { it.name }.toTypedArray(), "start_ms ASC")

    /** Finished recordings waiting for upload, honouring a back-off for failed ones. */
    fun pendingUploads(maxAttempts: Int = 8): List<Recording> {
        val now = System.currentTimeMillis()
        return byStatus(RecStatus.DONE, RecStatus.FAILED).filter { r ->
            if (r.status == RecStatus.DONE) return@filter true
            if (r.uploadAttempts >= maxAttempts) return@filter false
            val backoffMs = minOf(3600_000L, 60_000L shl (r.uploadAttempts - 1).coerceIn(0, 10))
            r.lastAttemptMs == null || now - r.lastAttemptMs >= backoffMs
        }
    }

    /** Recordings cut off by a crash or the app being killed: keep them if they have audio. */
    @Synchronized
    fun recoverInterrupted() {
        for (r in byStatus(RecStatus.RECORDING, RecStatus.UPLOADING)) {
            val f = r.file
            if (f.exists() && f.length() > 2048) {
                update(r.id) {
                    put("status", RecStatus.DONE.name)
                    if (r.endMs == null) put("end_ms", f.lastModified())
                    put("bytes", f.length())
                    if (r.splitReason == null) put("split_reason", "interrupted")
                }
            } else {
                f.delete()
                delete(r.id)
            }
        }
    }

    fun counts(): Map<RecStatus, Pair<Int, Long>> {
        val out = HashMap<RecStatus, Pair<Int, Long>>()
        readableDatabase.rawQuery("SELECT status, COUNT(*), COALESCE(SUM(bytes),0) FROM recordings GROUP BY status", null).use { c ->
            while (c.moveToNext()) {
                runCatching { RecStatus.valueOf(c.getString(0)) }.getOrNull()?.let { out[it] = c.getInt(1) to c.getLong(2) }
            }
        }
        return out
    }

    fun kvGet(k: String): String? =
        readableDatabase.rawQuery("SELECT v FROM kv WHERE k=?", arrayOf(k)).use { if (it.moveToFirst()) it.getString(0) else null }

    @Synchronized
    fun kvSet(k: String, v: String) {
        writableDatabase.insertWithOnConflict("kv", null, ContentValues().apply { put("k", k); put("v", v) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun query(where: String?, args: Array<String>?, order: String = "start_ms DESC", limit: Int? = null): List<Recording> =
        readableDatabase.query("recordings", null, where, args, null, null, order, limit?.toString()).use { c ->
            val out = ArrayList<Recording>(c.count)
            while (c.moveToNext()) out += row(c)
            out
        }

    private fun row(c: Cursor): Recording {
        fun s(n: String) = c.getColumnIndexOrThrow(n).let { if (c.isNull(it)) null else c.getString(it) }
        fun l(n: String) = c.getColumnIndexOrThrow(n).let { if (c.isNull(it)) null else c.getLong(it) }
        val titles = try {
            val a = JSONArray(s("titles") ?: "[]")
            (0 until a.length()).map { i -> a.getJSONObject(i).let { TitleMark(it.optDouble("t", 0.0), it.optString("title")) } }
        } catch (e: Exception) {
            emptyList()
        }
        return Recording(
            id = l("id")!!, stationId = s("station_id")!!, stationName = s("station_name")!!, path = s("path")!!,
            startMs = l("start_ms")!!, endMs = l("end_ms"), startLocal = s("start_local")!!, program = s("program"),
            titles = titles, format = s("format"), bytes = l("bytes") ?: 0, splitReason = s("split_reason"),
            status = runCatching { RecStatus.valueOf(s("status")!!) }.getOrDefault(RecStatus.DONE),
            iaIdentifier = s("ia_identifier"), iaUrl = s("ia_url"), uploadAttempts = (l("upload_attempts") ?: 0).toInt(),
            lastAttemptMs = l("last_attempt_ms"), lastError = s("last_error"),
        )
    }
}
