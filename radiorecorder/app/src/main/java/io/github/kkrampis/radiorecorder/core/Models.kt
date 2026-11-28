package io.github.kkrampis.radiorecorder.core

/**
 * Settings model. Plain Kotlin (no Android types) so the logic can be unit tested.
 * Days of the week follow java.time: Monday = 1 … Sunday = 7.
 */

enum class OutFormat { ORIGINAL, AAC }

data class Quality(
    val format: OutFormat = OutFormat.AAC,
    val bitrateKbps: Int = 128,
    val mono: Boolean = false,
) {
    fun label(): String = when (format) {
        OutFormat.ORIGINAL -> "Original stream"
        OutFormat.AAC -> "AAC $bitrateKbps kbps" + if (mono) " mono" else ""
    }

    companion object {
        val BITRATES = listOf(32, 48, 64, 96, 128, 160, 192, 256)
    }
}

data class SplitRules(
    /** Start a new file when the stream's title (ICY StreamTitle) changes… */
    val onMetadata: Boolean = true,
    /** …but never make a file shorter than this. */
    val minMinutes: Double = 15.0,
    /** Always cut at least this often. */
    val maxMinutes: Double = 60.0,
    /** Cut on the clock (:00, :30…) instead of N minutes after the start. */
    val alignToClock: Boolean = true,
)

data class Slot(
    val days: Set<Int> = setOf(1, 2, 3, 4, 5),
    val start: String = "09:00",
    val end: String = "10:00",
    val program: String = "",
)

data class OneOff(
    val date: String,
    val start: String,
    val end: String,
    val program: String = "",
)

enum class ScheduleMode { ALWAYS, SCHEDULED }

data class StationSchedule(
    val mode: ScheduleMode = ScheduleMode.ALWAYS,
    val slots: List<Slot> = emptyList(),
    val oneOff: List<OneOff> = emptyList(),
)

data class StationArchive(
    val enabled: Boolean = true,
    val collection: String = "",
    val tags: List<String> = emptyList(),
    val description: String = "",
    val language: String = "",
)

data class Station(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    /** IANA zone such as "Europe/Athens"; blank = the phone's time zone. */
    val timezone: String = "",
    val quality: Quality = Quality(),
    val split: SplitRules = SplitRules(),
    val schedule: StationSchedule = StationSchedule(),
    val archive: StationArchive = StationArchive(),
)

data class ArchiveSettings(
    val enabled: Boolean = false,
    val accessKey: String = "",
    /** The secret key itself lives in the Android Keystore-backed SecureStore, not here. */
    val hasSecret: Boolean = false,
    val identifierPrefix: String = "radiorec",
    val defaultCollection: String = "opensource_audio",
    val derive: Boolean = true,
    val testMode: Boolean = false,
    val uploadOnFinish: Boolean = true,
    val wifiOnly: Boolean = true,
)

data class CleanupSettings(
    val intervalHours: Double = 24.0,
    val retentionHours: Double = 24.0,
    val minFreeGb: Double = 1.0,
    /** 0 = never delete recordings that have not been uploaded. */
    val deleteUnuploadedAfterHours: Double = 0.0,
)

data class Config(
    val stations: List<Station> = emptyList(),
    val archive: ArchiveSettings = ArchiveSettings(),
    val cleanup: CleanupSettings = CleanupSettings(),
    /** Set from the notification's "Stop" button; recording resumes when you tap Resume. */
    val paused: Boolean = false,
)

object Validation {
    private val HM = Regex("^([01]?\\d|2[0-3]):([0-5]\\d)$")
    private val DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    fun isTime(s: String) = HM.matches(s)

    /** Returns an error message, or null when the station is valid. */
    fun station(st: Station): String? {
        if (st.name.isBlank()) return "Give the station a name"
        if (!Regex("^https?://\\S+$", RegexOption.IGNORE_CASE).matches(st.url.trim())) {
            return "The stream URL must start with http:// or https://"
        }
        if (st.timezone.isNotBlank()) {
            try {
                java.time.ZoneId.of(st.timezone)
            } catch (e: Exception) {
                return "Unknown time zone: ${st.timezone}"
            }
        }
        if (st.split.maxMinutes < 1 || st.split.maxMinutes > 24 * 60) {
            return "Maximum file length must be between 1 and 1440 minutes"
        }
        if (st.split.minMinutes < 0) return "Minimum file length can't be negative"
        if (st.quality.bitrateKbps !in 8..512) return "Bitrate must be 8–512 kbps"
        for (s in st.schedule.slots) {
            if (!isTime(s.start) || !isTime(s.end)) return "Slot times must be HH:MM"
            if (s.days.isEmpty()) return "Pick at least one day for each weekly slot"
        }
        for (o in st.schedule.oneOff) {
            if (!DATE.matches(o.date)) return "One-off dates must be YYYY-MM-DD"
            if (!isTime(o.start) || !isTime(o.end)) return "One-off times must be HH:MM"
        }
        if (st.schedule.mode == ScheduleMode.SCHEDULED && st.schedule.slots.isEmpty() && st.schedule.oneOff.isEmpty()) {
            return "Add at least one time slot or one-off recording, or choose \"Always\""
        }
        return null
    }
}
