package io.github.kkrampis.radiorecorder.data

import io.github.kkrampis.radiorecorder.core.ArchiveSettings
import io.github.kkrampis.radiorecorder.core.CleanupSettings
import io.github.kkrampis.radiorecorder.core.Config
import io.github.kkrampis.radiorecorder.core.OneOff
import io.github.kkrampis.radiorecorder.core.OutFormat
import io.github.kkrampis.radiorecorder.core.Quality
import io.github.kkrampis.radiorecorder.core.ScheduleMode
import io.github.kkrampis.radiorecorder.core.Slot
import io.github.kkrampis.radiorecorder.core.SplitRules
import io.github.kkrampis.radiorecorder.core.Station
import io.github.kkrampis.radiorecorder.core.StationArchive
import io.github.kkrampis.radiorecorder.core.StationSchedule
import org.json.JSONArray
import org.json.JSONObject

/** Config <-> JSON. Field names match the desktop app's config.json where they overlap. */
object ConfigJson {
    fun toJson(c: Config): JSONObject = JSONObject()
        .put("paused", c.paused)
        .put("stations", JSONArray(c.stations.map { station(it) }))
        .put(
            "archive",
            JSONObject()
                .put("enabled", c.archive.enabled)
                .put("access_key", c.archive.accessKey)
                .put("identifier_prefix", c.archive.identifierPrefix)
                .put("default_collection", c.archive.defaultCollection)
                .put("derive", c.archive.derive)
                .put("test_mode", c.archive.testMode)
                .put("upload_on_finish", c.archive.uploadOnFinish)
                .put("wifi_only", c.archive.wifiOnly),
        )
        .put(
            "cleanup",
            JSONObject()
                .put("interval_hours", c.cleanup.intervalHours)
                .put("retention_hours", c.cleanup.retentionHours)
                .put("min_free_gb", c.cleanup.minFreeGb)
                .put("delete_unuploaded_after_hours", c.cleanup.deleteUnuploadedAfterHours),
        )

    fun fromJson(o: JSONObject, hasSecret: Boolean): Config {
        val a = o.optJSONObject("archive") ?: JSONObject()
        val cl = o.optJSONObject("cleanup") ?: JSONObject()
        val stations = o.optJSONArray("stations") ?: JSONArray()
        val d = ArchiveSettings()
        val dc = CleanupSettings()
        return Config(
            paused = o.optBoolean("paused", false),
            stations = (0 until stations.length()).mapNotNull { i -> stations.optJSONObject(i)?.let { stationFrom(it) } },
            archive = ArchiveSettings(
                enabled = a.optBoolean("enabled", d.enabled),
                accessKey = a.optString("access_key", d.accessKey),
                hasSecret = hasSecret,
                identifierPrefix = a.optString("identifier_prefix", d.identifierPrefix),
                defaultCollection = a.optString("default_collection", d.defaultCollection),
                derive = a.optBoolean("derive", d.derive),
                testMode = a.optBoolean("test_mode", d.testMode),
                uploadOnFinish = a.optBoolean("upload_on_finish", d.uploadOnFinish),
                wifiOnly = a.optBoolean("wifi_only", d.wifiOnly),
            ),
            cleanup = CleanupSettings(
                intervalHours = cl.optDouble("interval_hours", dc.intervalHours),
                retentionHours = cl.optDouble("retention_hours", dc.retentionHours),
                minFreeGb = cl.optDouble("min_free_gb", dc.minFreeGb),
                deleteUnuploadedAfterHours = cl.optDouble("delete_unuploaded_after_hours", dc.deleteUnuploadedAfterHours),
            ),
        )
    }

    private fun station(s: Station): JSONObject = JSONObject()
        .put("id", s.id)
        .put("name", s.name)
        .put("url", s.url)
        .put("enabled", s.enabled)
        .put("timezone", s.timezone)
        .put(
            "quality",
            JSONObject()
                .put("format", if (s.quality.format == OutFormat.ORIGINAL) "original" else "aac")
                .put("bitrate", s.quality.bitrateKbps)
                .put("mono", s.quality.mono),
        )
        .put(
            "split",
            JSONObject()
                .put("on_metadata", s.split.onMetadata)
                .put("min_minutes", s.split.minMinutes)
                .put("max_minutes", s.split.maxMinutes)
                .put("align_to_clock", s.split.alignToClock),
        )
        .put(
            "schedule",
            JSONObject()
                .put("mode", if (s.schedule.mode == ScheduleMode.ALWAYS) "always" else "scheduled")
                .put(
                    "slots",
                    JSONArray(
                        s.schedule.slots.map {
                            JSONObject().put("days", JSONArray(it.days.sorted())).put("start", it.start)
                                .put("end", it.end).put("program", it.program)
                        },
                    ),
                )
                .put(
                    "one_off",
                    JSONArray(
                        s.schedule.oneOff.map {
                            JSONObject().put("date", it.date).put("start", it.start).put("end", it.end).put("program", it.program)
                        },
                    ),
                ),
        )
        .put(
            "archive",
            JSONObject()
                .put("enabled", s.archive.enabled)
                .put("collection", s.archive.collection)
                .put("tags", JSONArray(s.archive.tags))
                .put("description", s.archive.description)
                .put("language", s.archive.language),
        )

    private fun stationFrom(o: JSONObject): Station {
        val q = o.optJSONObject("quality") ?: JSONObject()
        val sp = o.optJSONObject("split") ?: JSONObject()
        val sc = o.optJSONObject("schedule") ?: JSONObject()
        val ar = o.optJSONObject("archive") ?: JSONObject()
        val ds = SplitRules()
        return Station(
            id = o.optString("id"),
            name = o.optString("name"),
            url = o.optString("url"),
            enabled = o.optBoolean("enabled", true),
            timezone = o.optString("timezone", ""),
            quality = Quality(
                format = if (q.optString("format", "aac") == "original") OutFormat.ORIGINAL else OutFormat.AAC,
                bitrateKbps = q.optInt("bitrate", 128),
                mono = q.optBoolean("mono", false),
            ),
            split = SplitRules(
                onMetadata = sp.optBoolean("on_metadata", ds.onMetadata),
                minMinutes = sp.optDouble("min_minutes", ds.minMinutes),
                maxMinutes = sp.optDouble("max_minutes", ds.maxMinutes),
                alignToClock = sp.optBoolean("align_to_clock", ds.alignToClock),
            ),
            schedule = StationSchedule(
                mode = if (sc.optString("mode", "always") == "scheduled") ScheduleMode.SCHEDULED else ScheduleMode.ALWAYS,
                slots = objects(sc.optJSONArray("slots")).map {
                    val days = it.optJSONArray("days") ?: JSONArray()
                    Slot(
                        days = (0 until days.length()).map { i -> days.optInt(i) }.filter { d -> d in 1..7 }.toSet(),
                        start = it.optString("start", "09:00"),
                        end = it.optString("end", "10:00"),
                        program = it.optString("program", ""),
                    )
                },
                oneOff = objects(sc.optJSONArray("one_off")).map {
                    OneOff(it.optString("date"), it.optString("start"), it.optString("end"), it.optString("program", ""))
                },
            ),
            archive = StationArchive(
                enabled = ar.optBoolean("enabled", true),
                collection = ar.optString("collection", ""),
                tags = (ar.optJSONArray("tags") ?: JSONArray()).let { t -> (0 until t.length()).map { t.optString(it) } },
                description = ar.optString("description", ""),
                language = ar.optString("language", ""),
            ),
        )
    }

    private fun objects(a: JSONArray?): List<JSONObject> =
        if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it) }
}
