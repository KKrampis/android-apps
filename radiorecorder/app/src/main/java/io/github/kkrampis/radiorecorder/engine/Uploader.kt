package io.github.kkrampis.radiorecorder.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import io.github.kkrampis.radiorecorder.core.ArchiveMeta
import io.github.kkrampis.radiorecorder.core.RecordingInfo
import io.github.kkrampis.radiorecorder.data.RecStatus
import io.github.kkrampis.radiorecorder.data.Recording
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.time.Instant
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit

data class UploadProgress(val recordingId: Long, val fileName: String, val sent: Long, val size: Long)

/** Pushes finished recordings to the Internet Archive through its S3-like API. */
class Uploader(private val engine: Engine, private val context: Context) {
    private val lock = Any()
    private val client: OkHttpClient = engine.http.newBuilder()
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(10, TimeUnit.MINUTES)
        .build()

    @Volatile var current: UploadProgress? = null
        private set
    @Volatile var lastError: String? = null
        private set

    fun keys(): Pair<String, String> = engine.config.current.archive.accessKey.trim() to engine.config.secretKey()

    fun enabled(): Boolean {
        val a = engine.config.current.archive
        val (access, secret) = keys()
        return a.enabled && access.isNotEmpty() && secret.isNotEmpty()
    }

    fun networkAllowed(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return !engine.config.current.archive.wifiOnly || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    data class Result(val uploaded: Int, val failed: Int, val skipped: Int)

    /** Uploads everything pending. Safe to call from several threads (serialised). */
    fun processPending(): Result {
        if (!enabled() || !networkAllowed()) return Result(0, 0, 0)
        synchronized(lock) {
            var up = 0
            var failed = 0
            var skipped = 0
            for (r in engine.db.pendingUploads()) {
                val st = engine.config.station(r.stationId)
                if (st != null && !st.archive.enabled) {
                    skipped++
                    continue
                }
                if (!networkAllowed()) break
                if (uploadOne(r)) up++ else failed++
            }
            return Result(up, failed, skipped)
        }
    }

    private fun uploadOne(r: Recording): Boolean {
        val settings = engine.config.current.archive
        val (access, secret) = keys()
        val file = r.file
        if (!file.exists()) {
            engine.db.update(r.id) { put("status", RecStatus.FAILED.name); put("last_error", "file missing"); put("upload_attempts", 99) }
            return false
        }
        val start = runCatching { ZonedDateTime.parse(r.startLocal) }.getOrElse { ZonedDateTime.now() }
        var ident = r.iaIdentifier ?: ArchiveMeta.identifier(settings.identifierPrefix, r.stationName, start)
        engine.db.update(r.id) {
            put("status", RecStatus.UPLOADING.name); put("ia_identifier", ident); put("last_attempt_ms", System.currentTimeMillis())
        }
        val st = engine.config.station(r.stationId)
        val info = RecordingInfo(
            stationName = r.stationName,
            program = r.program.orEmpty(),
            start = start,
            end = r.endMs?.let { ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), start.zone) },
            titles = r.titles.map { it.title },
            sourceUrl = st?.url.orEmpty(),
        )
        val meta = ArchiveMeta.metadata(info, st?.archive, settings)
        current = UploadProgress(r.id, file.name, 0, file.length())
        return try {
            var url: String? = null
            for (attempt in 0..1) {
                val res = putFile(file, ident, meta, access, secret, settings.derive) { sent ->
                    current = UploadProgress(r.id, file.name, sent, file.length())
                }
                if (res.first == 200) {
                    url = "https://archive.org/details/$ident"
                    break
                }
                // Identifier taken by someone else's item: pick a new one once.
                if (attempt == 0 && res.first in listOf(403, 409) && res.second.contains("BucketAlreadyExists")) {
                    ident = "${ident.take(93)}-${UUID.randomUUID().toString().take(6)}"
                    engine.db.update(r.id) { put("ia_identifier", ident) }
                    continue
                }
                throw StreamException("archive.org HTTP ${res.first}: ${messageOf(res.second)}")
            }
            engine.db.update(r.id) {
                put("status", RecStatus.UPLOADED.name); put("ia_url", url); putNull("last_error")
                put("upload_attempts", r.uploadAttempts + 1)
            }
            lastError = null
            Log.i(TAG, "uploaded ${file.name} -> $url")
            true
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            lastError = msg
            Log.w(TAG, "upload of ${file.name} failed: $msg")
            engine.db.update(r.id) {
                put("status", RecStatus.FAILED.name); put("last_error", msg.take(500)); put("upload_attempts", r.uploadAttempts + 1)
            }
            false
        } finally {
            current = null
        }
    }

    private fun putFile(
        file: File, ident: String, meta: Map<String, Any>, access: String, secret: String, derive: Boolean,
        progress: (Long) -> Unit,
    ): Pair<Int, String> {
        val mime = when (file.extension.lowercase()) {
            "mp3" -> "audio/mpeg"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            else -> "application/octet-stream"
        }.toMediaType()
        val body = FileBody(file, mime, progress)
        val req = Request.Builder()
            .url("https://s3.us.archive.org/${enc(ident)}/${enc(file.name)}")
            .put(body)
            .header("authorization", "LOW $access:$secret")
            .header("x-amz-auto-make-bucket", "1")
            .header("x-archive-queue-derive", if (derive) "1" else "0")
            .header("x-archive-size-hint", file.length().toString())
            .header("x-archive-keep-old-version", "0")
            .apply { for ((k, v) in ArchiveMeta.headers(meta)) addHeader(k, v) }
            .build()
        client.newCall(req).execute().use { resp -> return resp.code to (resp.body?.string() ?: "") }
    }

    /** Checks the keys by listing the account's items (needs both keys to be right). */
    fun checkKeys(access: String, secret: String): Pair<Boolean, String> {
        if (access.isBlank() || secret.isBlank()) return false to "Enter both the access key and the secret key"
        return try {
            val req = Request.Builder().url("https://s3.us.archive.org/").header("authorization", "LOW ${access.trim()}:${secret.trim()}").build()
            engine.http.newCall(req).execute().use { resp ->
                if (resp.code == 200) true to "Keys accepted by archive.org"
                else false to "archive.org HTTP ${resp.code}: ${messageOf(resp.body?.string().orEmpty())}"
            }
        } catch (e: Exception) {
            false to "Couldn't reach archive.org: ${e.message}"
        }
    }

    private fun messageOf(xml: String): String =
        Regex("<Message>(.*?)</Message>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)?.take(200) ?: xml.take(200)

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private class FileBody(private val file: File, private val type: MediaType, private val progress: (Long) -> Unit) : RequestBody() {
        override fun contentType() = type
        override fun contentLength() = file.length()
        override fun writeTo(sink: BufferedSink) {
            file.source().use { src ->
                var sent = 0L
                while (true) {
                    val n = src.read(sink.buffer, 256 * 1024)
                    if (n < 0) break
                    sink.flush()
                    sent += n
                    progress(sent)
                }
            }
        }
    }

    private companion object {
        const val TAG = "RadioRecorder"
    }
}
