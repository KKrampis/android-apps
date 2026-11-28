package io.github.kkrampis.radiorecorder.core

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the uploader knows about one finished recording. */
data class RecordingInfo(
    val stationName: String,
    val program: String,
    val start: ZonedDateTime,
    val end: ZonedDateTime?,
    val titles: List<String>,
    val sourceUrl: String,
)

/**
 * Internet Archive item metadata and IA-S3 headers.
 * See https://archive.org/developers/ias3.html
 */
object ArchiveMeta {
    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US)
    private val DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
    private val WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)

    fun identifier(prefix: String, stationName: String, start: ZonedDateTime): String {
        val id = "${Names.slug(prefix, 20)}-${Names.slug(stationName, 40)}-${start.format(STAMP)}"
        return id.replace(Regex("[^A-Za-z0-9._-]"), "-").take(100)
    }

    /** Field name -> value (String) or values (List<String>). Empty values are dropped. */
    fun metadata(rec: RecordingInfo, station: StationArchive?, settings: ArchiveSettings): LinkedHashMap<String, Any> {
        val program = rec.program
        val titles = rec.titles.filter { it.isNotBlank() }.distinct()
        val collection = when {
            settings.testMode -> "test_collection"
            !station?.collection.isNullOrBlank() -> station!!.collection
            settings.defaultCollection.isNotBlank() -> settings.defaultCollection
            else -> "opensource_audio"
        }
        val whenStr = rec.start.format(WHEN)
        val title = if (program.isNotBlank()) "${rec.stationName} — $program — $whenStr" else "${rec.stationName} — $whenStr"
        val durMin = rec.end?.let { Math.round((it.toEpochSecond() - rec.start.toEpochSecond()) / 60.0) }
        val desc = buildString {
            append(station?.description?.takeIf { it.isNotBlank() } ?: "Off-air recording of ${rec.stationName}.")
            append(" Recorded $whenStr")
            rec.end?.let { append(" to ${it.format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))}") }
            if (durMin != null) append(" ($durMin min)")
            append(".")
            if (program.isNotBlank()) append(" Program: $program.")
            if (titles.isNotEmpty()) append(" Stream titles during this recording: ${titles.take(50).joinToString("; ")}.")
        }
        val subjects = (
            listOf(
                "radio", rec.stationName, rec.start.format(DAY), rec.start.format(DateTimeFormatter.ofPattern("yyyy-MM", Locale.US)),
                rec.start.format(DateTimeFormatter.ofPattern("HH:00", Locale.US)),
                rec.start.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.US),
            ) + listOfNotNull(program.ifBlank { null }) + (station?.tags ?: emptyList())
            ).filter { it.isNotBlank() }.distinct()

        val m = LinkedHashMap<String, Any>()
        m["mediatype"] = "audio"
        m["collection"] = collection
        m["title"] = title
        m["creator"] = rec.stationName
        m["date"] = rec.start.format(DAY)
        m["subject"] = subjects
        m["description"] = desc
        station?.language?.takeIf { it.isNotBlank() }?.let { m["language"] = it }
        m["source"] = rec.sourceUrl
        if (program.isNotBlank()) m["program"] = program
        m["station"] = rec.stationName
        m["recording_start"] = rec.start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        rec.end?.let { m["recording_end"] = it.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) }
        if (titles.isNotEmpty()) m["stream_titles"] = titles.take(50).joinToString("; ")
        m["scanner"] = "radio-recorder-android"
        return m
    }

    /** IA-S3 metadata headers. "_" in a field name is written as "--"; lists become meta01-, meta02-… */
    fun headers(meta: Map<String, Any>): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        for ((key, value) in meta) {
            val name = key.replace("_", "--")
            when (value) {
                is List<*> -> value.filterIsInstance<String>().filter { it.isNotBlank() }.forEachIndexed { i, v ->
                    out += "x-archive-meta%02d-%s".format(Locale.US, i + 1, name) to headerValue(v)
                }
                else -> value.toString().takeIf { it.isNotBlank() }?.let { out += "x-archive-meta-$name" to headerValue(it) }
            }
        }
        return out
    }

    /** Header-safe value: printable ASCII as-is, anything else as uri(percent-encoded UTF-8). */
    fun headerValue(v: String): String {
        val s = v.replace(Regex("[\\r\\n\\t]+"), " ").trim()
        if (s.all { it.code in 32..126 }) return s
        val sb = StringBuilder("uri(")
        for (byte in s.toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt() and 0xFF
            val ch = c.toChar()
            if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                sb.append(ch)
            } else {
                sb.append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
            }
        }
        return sb.append(')').toString()
    }
}

object Names {
    fun slug(text: String, max: Int = 40): String {
        val s = text.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').lowercase(Locale.US)
        return s.take(max).trim('-').ifEmpty { "x" }
    }

    private val FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US)

    /** e.g. harbor-city-radio_20261127-090000_morning-show */
    fun fileBase(stationName: String, start: ZonedDateTime, program: String?): String {
        val b = "${slug(stationName, 30)}_${start.format(FILE_STAMP)}"
        return if (program.isNullOrBlank()) b else "${b}_${slug(program, 40)}"
    }

    /**
     * When the current file must end at the latest. With [align] the cut falls on clock multiples
     * of [maxMinutes] counted from midnight (60 -> every hour on the hour).
     */
    fun nextBoundary(start: ZonedDateTime, maxMinutes: Double, align: Boolean): ZonedDateTime {
        val maxSec = (maxMinutes * 60).toLong()
        if (!align) return start.plusSeconds(maxSec)
        val midnight = start.toLocalDate().atStartOfDay(start.zone)
        val sinceMidnight = start.toEpochSecond() - midnight.toEpochSecond()
        val k = sinceMidnight / maxSec + 1
        var b = midnight.plusSeconds(k * maxSec)
        if (b.toEpochSecond() - start.toEpochSecond() < 60) b = b.plusSeconds(maxSec) // no tiny first file
        return b
    }
}
