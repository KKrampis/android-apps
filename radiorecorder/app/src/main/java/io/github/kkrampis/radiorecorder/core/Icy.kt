package io.github.kkrampis.radiorecorder.core

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Helpers for Icecast/Shoutcast streams and playlist links. */
object Icy {
    private val TITLE = Regex("StreamTitle='(.*?)';(?=Stream|\u0000|$)", RegexOption.DOT_MATCHES_ALL)

    /** Extracts StreamTitle from an ICY metadata block (UTF-8, falling back to Latin-1). */
    fun parseTitle(meta: ByteArray): String? {
        var end = meta.size
        while (end > 0 && meta[end - 1] == 0.toByte()) end--
        val text = decode(meta.copyOf(end)) + "\u0000"
        return TITLE.find(text)?.groupValues?.get(1)?.trim()
    }

    private fun decode(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: Exception) {
        String(bytes, Charsets.ISO_8859_1)
    }

    /** File extension for a stream's Content-Type. */
    fun extension(contentType: String?): String {
        val ct = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        return when (ct) {
            "audio/mpeg", "audio/mp3", "audio/mpeg3", "audio/x-mpeg" -> "mp3"
            "audio/aac", "audio/aacp", "audio/x-aac", "audio/x-aacp", "audio/mp4" -> "aac"
            "application/ogg", "audio/ogg", "audio/opus", "audio/x-ogg" -> "ogg"
            "audio/flac", "audio/x-flac" -> "flac"
            else -> "mp3"
        }
    }

    private val PLAYLIST_TYPES = setOf(
        "audio/x-scpls", "audio/scpls", "audio/x-mpegurl", "audio/mpegurl", "application/pls+xml",
        "application/x-mpegurl", "application/vnd.apple.mpegurl",
    )

    fun isPlaylist(contentType: String?, url: String): Boolean {
        val ct = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        val path = url.substringBefore('?').lowercase()
        return ct in PLAYLIST_TYPES || path.endsWith(".pls") || path.endsWith(".m3u")
    }

    fun isHls(url: String, body: String? = null): Boolean =
        Regex("\\.m3u8(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(url) || (body?.contains("#EXT-X-") == true)

    /** First stream URL in a .pls or .m3u playlist. */
    fun firstPlaylistEntry(body: String, base: String): String? {
        for (raw in body.lineSequence()) {
            val line = raw.trim()
            Regex("^File\\d+\\s*=\\s*(\\S+)", RegexOption.IGNORE_CASE).find(line)?.let {
                return resolve(base, it.groupValues[1])
            }
            if (line.isNotEmpty() && !line.startsWith("#") && Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(line)) {
                return line
            }
        }
        return null
    }

    private fun resolve(base: String, ref: String): String =
        try {
            java.net.URI(base).resolve(ref).toString()
        } catch (e: Exception) {
            ref
        }
}
