package io.github.kkrampis.radiorecorder.engine

import io.github.kkrampis.radiorecorder.core.Icy
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.Closeable
import java.io.IOException
import java.io.InputStream

class StreamException(message: String) : IOException(message)

/** One piece of a stream: audio bytes, or a new StreamTitle. */
class Chunk(val audio: ByteArray?, val audioLength: Int, val title: String?)

/**
 * An open Icecast/Shoutcast connection. ICY metadata (StreamTitle) is removed from the audio
 * and reported separately; that's what program-change detection is based on.
 */
class StreamSource private constructor(
    private val response: Response,
    val finalUrl: String,
) : Closeable {
    private val input: InputStream = response.body!!.byteStream()
    val contentType: String? = response.header("Content-Type")
    val ext: String = Icy.extension(contentType)
    val metaInt: Int = response.header("icy-metaint")?.trim()?.toIntOrNull() ?: 0
    val name: String = response.header("icy-name")?.trim().orEmpty()
    val hasMetadata: Boolean get() = metaInt > 0

    private val buf = ByteArray(if (metaInt > 0) metaInt else 16 * 1024)
    private var lastTitle: String? = null
    private var atMeta = false

    /** Blocks until the next chunk. Throws on disconnect. */
    fun next(): Chunk {
        if (metaInt <= 0) {
            val n = input.read(buf)
            if (n < 0) throw StreamException("The stream ended")
            return Chunk(buf, n, null)
        }
        while (true) {
            if (!atMeta) {
                readFully(buf, metaInt)
                atMeta = true
                return Chunk(buf, metaInt, null)
            }
            atMeta = false
            val lenByte = input.read()
            if (lenByte < 0) throw StreamException("The stream ended")
            val n = lenByte * 16
            if (n == 0) continue
            val meta = ByteArray(n)
            readFully(meta, n)
            val title = Icy.parseTitle(meta)
            if (title != null && title != lastTitle) {
                lastTitle = title
                return Chunk(null, 0, title)
            }
        }
    }

    private fun readFully(b: ByteArray, n: Int) {
        var off = 0
        while (off < n) {
            val r = input.read(b, off, n - off)
            if (r < 0) throw StreamException("The stream ended")
            off += r
        }
    }

    override fun close() {
        try {
            response.close()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val UA = "RadioRecorderAndroid/1.0 (+https://github.com/KKrampis/android-apps)"

        /** Opens [url], following .pls/.m3u playlists to the actual stream. */
        fun open(client: OkHttpClient, url: String): StreamSource {
            var target = url.trim()
            repeat(3) {
                if (Icy.isHls(target)) throw hls()
                val req = Request.Builder().url(target)
                    .header("Icy-MetaData", "1")
                    .header("User-Agent", UA)
                    .header("Accept-Encoding", "identity")
                    .build()
                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) {
                    val code = resp.code
                    resp.close()
                    throw StreamException("The server answered HTTP $code")
                }
                val final = resp.request.url.toString()
                val ct = resp.header("Content-Type")
                if (Icy.isPlaylist(ct, final)) {
                    val body = resp.body!!.source().let { src ->
                        src.request(64 * 1024)
                        src.buffer.readUtf8(minOf(src.buffer.size, 64L * 1024))
                    }
                    resp.close()
                    if (Icy.isHls(final, body)) throw hls()
                    target = Icy.firstPlaylistEntry(body, final) ?: throw StreamException("The playlist has no stream link in it")
                    return@repeat
                }
                if (ct?.startsWith("text/html", ignoreCase = true) == true) {
                    resp.close()
                    throw StreamException("That link is a web page, not an audio stream")
                }
                return StreamSource(resp, final)
            }
            throw StreamException("Too many nested playlists")
        }

        private fun hls() = StreamException(
            "HLS (.m3u8) streams aren't supported on Android yet. Use the station's direct MP3/AAC (Icecast/Shoutcast) link.",
        )
    }
}
