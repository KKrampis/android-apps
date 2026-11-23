package io.github.kkrampis.spokemap.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Bike infrastructure from OpenStreetMap through the Overpass API. It covers NYC and New Jersey
 * the same way, so it is the base layer of the app.
 */
object Overpass {
    /** Tried in order. The second is a public instance that keeps no request logs. */
    val ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
    )

    fun query(box: Bbox): String {
        val b = box.overpass()
        return buildString {
            append("[out:json][timeout:90];(")
            append("way[\"highway\"=\"cycleway\"]($b);")
            append("way[\"highway\"~\"^(path|footway|pedestrian|track|bridleway)$\"][\"bicycle\"=\"designated\"]($b);")
            append("way[\"cycleway\"]($b);")
            append("way[\"cycleway:both\"]($b);")
            append("way[\"cycleway:left\"]($b);")
            append("way[\"cycleway:right\"]($b);")
            append(");out tags geom;")
        }
    }

    private val CYCLEWAY_KEYS = listOf("cycleway", "cycleway:both", "cycleway:left", "cycleway:right")

    /** Returns the [Kind] of a way from its tags, or null when it has no bike facility. */
    fun classify(tags: Map<String, String>): String? {
        val highway = tags["highway"]
        if (highway == "cycleway") return Kind.PATH
        if (highway in setOf("path", "footway", "pedestrian", "track", "bridleway") &&
            tags["bicycle"] == "designated"
        ) return Kind.PATH

        val values = CYCLEWAY_KEYS.mapNotNull { tags[it] }
        return when {
            values.any { it == "track" || it == "opposite_track" } -> Kind.PROTECTED
            values.any { it == "lane" || it == "opposite_lane" } -> Kind.LANE
            values.any {
                it == "shared_lane" || it == "share_busway" || it == "opposite_share_busway" || it == "shoulder"
            } -> Kind.SHARED
            else -> null // "no", "separate" (drawn as its own way), etc.
        }
    }

    /** Parses an Overpass `out geom` response into (way id, GeoJSON feature) pairs. */
    fun parse(json: String): List<Pair<Long, String>> {
        val root = JSONObject(json)
        val elements = root.optJSONArray("elements") ?: run {
            val remark = root.optString("remark")
            throw IllegalStateException(if (remark.isNotBlank()) remark else "Unexpected Overpass response")
        }
        val out = ArrayList<Pair<Long, String>>(elements.length())
        for (i in 0 until elements.length()) {
            val el = elements.optJSONObject(i) ?: continue
            if (el.optString("type") != "way") continue
            val tagsJson = el.optJSONObject("tags") ?: continue
            val tags = HashMap<String, String>()
            for (k in tagsJson.keys()) tags[k] = tagsJson.optString(k)
            val kind = classify(tags) ?: continue
            val geom = el.optJSONArray("geometry") ?: continue
            val coords = JSONArray()
            for (j in 0 until geom.length()) {
                val p = geom.optJSONObject(j) ?: continue
                coords.put(JSONArray().put(p.getDouble("lon")).put(p.getDouble("lat")))
            }
            if (coords.length() < 2) continue
            val id = el.getLong("id")
            val props = JSONObject()
                .put("src", "osm")
                .put("kind", kind)
                .put("name", tags["name"] ?: tags["ref"] ?: "")
                .put("osm_id", id)
            val feature = JSONObject()
                .put("type", "Feature")
                .put("properties", props)
                .put("geometry", JSONObject().put("type", "LineString").put("coordinates", coords))
            out += id to feature.toString()
        }
        return out
    }
}
