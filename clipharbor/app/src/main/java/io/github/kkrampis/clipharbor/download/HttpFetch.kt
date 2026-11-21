package io.github.kkrampis.clipharbor.download

import android.net.Uri
import io.github.kkrampis.clipharbor.yt.BROWSER_USER_AGENT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Downloads a stream in ranged chunks. YouTube slows down long single requests,
 * while short ranged requests come through at full speed.
 */
object HttpFetch {
    private const val CHUNK = 8L * 1024 * 1024

    /** Size from the `clen` URL parameter that YouTube stream URLs carry, or a HEAD request. */
    suspend fun contentLength(client: OkHttpClient, url: String): Long = withContext(Dispatchers.IO) {
        Uri.parse(url).getQueryParameter("clen")?.toLongOrNull()?.let { return@withContext it }
        try {
            val req = Request.Builder().url(url).head().header("User-Agent", BROWSER_USER_AGENT).build()
            client.newCall(req).execute().use { it.header("Content-Length")?.toLongOrNull() ?: -1L }
        } catch (e: IOException) {
            -1L
        }
    }

    suspend fun download(
        client: OkHttpClient,
        url: String,
        dest: File,
        onBytes: suspend (Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        var pos = if (dest.exists()) dest.length() else 0L
        if (pos > 0) onBytes(pos)
        val buffer = ByteArray(64 * 1024)
        FileOutputStream(dest, true).use { out ->
            var finished = false
            while (!finished) {
                ensureActive()
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .header("Range", "bytes=$pos-${pos + CHUNK - 1}")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.code == 416) {
                        finished = true
                    } else {
                        if (!resp.isSuccessful) throw IOException("Server answered HTTP ${resp.code}")
                        val body = resp.body ?: throw IOException("Empty response")
                        var read = 0L
                        body.byteStream().use { input ->
                            while (true) {
                                ensureActive()
                                val n = input.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                                pos += n
                                read += n
                                onBytes(n.toLong())
                            }
                        }
                        val total = resp.header("Content-Range")
                            ?.substringAfter('/', "")
                            ?.toLongOrNull()
                        finished = when {
                            resp.code == 200 -> true // server ignored the range and sent everything
                            read == 0L -> true
                            total != null -> pos >= total
                            else -> read < CHUNK
                        }
                    }
                }
            }
        }
    }
}
