package io.github.kkrampis.clipharbor.transcribe

import java.util.Locale

/**
 * Builds a readable transcript: text grouped into paragraphs of about half a minute,
 * each starting with its timestamp, e.g. "[02:30] and then we ...".
 */
class TranscriptBuilder(private val paragraphSeconds: Double = 30.0) {
    private val out = StringBuilder()
    private var paragraphStart = -1.0
    private val recent = ArrayDeque<String>()

    fun add(startSeconds: Double, text: String) {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        if (clean.isEmpty() || clean in recent) return
        recent.addLast(clean)
        if (recent.size > 4) recent.removeFirst()

        if (paragraphStart < 0 || startSeconds - paragraphStart >= paragraphSeconds) {
            if (out.isNotEmpty()) out.append("\n\n")
            out.append('[').append(timestamp(startSeconds)).append("] ")
            paragraphStart = startSeconds
        } else {
            out.append(' ')
        }
        out.append(clean)
    }

    val isEmpty: Boolean get() = out.isEmpty()

    override fun toString(): String = out.toString()

    companion object {
        fun timestamp(seconds: Double): String {
            val s = seconds.toLong().coerceAtLeast(0)
            val h = s / 3600
            val m = (s % 3600) / 60
            val sec = s % 60
            return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
            else String.format(Locale.US, "%02d:%02d", m, sec)
        }
    }
}

/** Turns a WebVTT caption file (as YouTube serves it) into a [TranscriptBuilder] transcript. */
object Vtt {
    private val timing = Regex("""^\s*((?:\d+:)?\d{1,2}:\d{2}[.,]\d{1,3})\s+-->""")
    private val tags = Regex("<[^>]*>")

    fun toTranscript(vtt: String): String {
        val builder = TranscriptBuilder()
        val lines = vtt.replace("\r\n", "\n").split('\n')
        var i = 0
        while (i < lines.size) {
            val m = timing.find(lines[i])
            if (m == null) {
                i++
                continue
            }
            val start = parseTime(m.groupValues[1])
            i++
            while (i < lines.size && lines[i].isNotBlank()) {
                val text = decode(lines[i].replace(tags, ""))
                builder.add(start, text)
                i++
            }
        }
        return builder.toString()
    }

    private fun parseTime(t: String): Double {
        val parts = t.replace(',', '.').split(':')
        var seconds = 0.0
        for (p in parts) seconds = seconds * 60 + (p.toDoubleOrNull() ?: 0.0)
        return seconds
    }

    private fun decode(s: String) = s
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
}
