package io.github.kkrampis.spokemap.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Live Citi Bike dock status (NYC, Jersey City and Hoboken) from the public GBFS feed.
 * The discovery file points at the current feed host, with the old fixed URLs as a fallback.
 */
object CitiBike {
    const val DISCOVERY = "https://gbfs.citibikenyc.com/gbfs/gbfs.json"
    const val FALLBACK_INFO = "https://gbfs.citibikenyc.com/gbfs/en/station_information.json"
    const val FALLBACK_STATUS = "https://gbfs.citibikenyc.com/gbfs/en/station_status.json"

    data class Station(
        val id: String,
        val name: String,
        val lat: Double,
        val lon: Double,
        val capacity: Int,
        val bikes: Int,
        val ebikes: Int,
        val docks: Int,
        val renting: Boolean,
        val returning: Boolean,
        val lastReported: Long,
    )

    /** Returns (station_information, station_status) URLs from a gbfs.json discovery file. */
    fun feedUrls(discovery: String): Pair<String, String>? {
        val data = JSONObject(discovery).optJSONObject("data") ?: return null
        val lang = data.optJSONObject("en") ?: data.keys().asSequence().firstOrNull()?.let { data.optJSONObject(it) }
        val feeds = lang?.optJSONArray("feeds") ?: data.optJSONArray("feeds") ?: return null
        var info: String? = null
        var status: String? = null
        for (i in 0 until feeds.length()) {
            val f = feeds.optJSONObject(i) ?: continue
            when (f.optString("name")) {
                "station_information" -> info = f.optString("url")
                "station_status" -> status = f.optString("url")
            }
        }
        return if (!info.isNullOrBlank() && !status.isNullOrBlank()) info to status else null
    }

    fun merge(infoJson: String, statusJson: String): List<Station> {
        val info = stations(infoJson)
        val status = HashMap<String, JSONObject>()
        val statusList = stations(statusJson)
        for (i in 0 until statusList.length()) {
            val s = statusList.optJSONObject(i) ?: continue
            status[s.optString("station_id")] = s
        }
        val out = ArrayList<Station>(info.length())
        for (i in 0 until info.length()) {
            val s = info.optJSONObject(i) ?: continue
            val id = s.optString("station_id")
            val st = status[id] ?: continue
            if (st.has("is_installed") && !flag(st, "is_installed")) continue
            val lat = s.optDouble("lat", Double.NaN)
            val lon = s.optDouble("lon", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) continue
            out += Station(
                id = id,
                name = s.optString("name"),
                lat = lat,
                lon = lon,
                capacity = s.optInt("capacity"),
                bikes = st.optInt("num_bikes_available"),
                ebikes = st.optInt("num_ebikes_available"),
                docks = st.optInt("num_docks_available"),
                renting = !st.has("is_renting") || flag(st, "is_renting"),
                returning = !st.has("is_returning") || flag(st, "is_returning"),
                lastReported = st.optLong("last_reported"),
            )
        }
        return out
    }

    /** GBFS 1.x uses 0/1 for booleans, 2.x and later use true/false. */
    private fun flag(o: JSONObject, key: String): Boolean = when (val v = o.opt(key)) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> v == "true" || v == "1"
        else -> false
    }

    private fun stations(json: String): JSONArray =
        JSONObject(json).optJSONObject("data")?.optJSONArray("stations")
            ?: throw IOException("Unexpected Citi Bike response")

    fun toGeoJson(stations: List<Station>): String = GeoJson.collection(
        stations.map { s ->
            JSONObject()
                .put("type", "Feature")
                .put(
                    "properties",
                    JSONObject()
                        .put("src", "bikes")
                        .put("id", s.id)
                        .put("name", s.name)
                        .put("bikes", s.bikes)
                        .put("ebikes", s.ebikes)
                        .put("docks", s.docks)
                        .put("capacity", s.capacity)
                        .put("renting", s.renting)
                        .put("returning", s.returning)
                        .put("updated", s.lastReported),
                )
                .put(
                    "geometry",
                    JSONObject().put("type", "Point").put("coordinates", JSONArray().put(s.lon).put(s.lat)),
                )
                .toString()
        },
    )
}
