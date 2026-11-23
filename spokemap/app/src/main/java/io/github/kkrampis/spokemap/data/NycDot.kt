package io.github.kkrampis.spokemap.data

import org.json.JSONObject
import java.io.IOException

/**
 * NYC DOT's official bike network ("New York City Bike Routes", dataset mzxg-pwib on NYC Open
 * Data). It is small enough (about 20,000 segments) to download whole and cache for a week.
 */
object NycDot {
    /** Whole-dataset downloads, tried in order. */
    val DOWNLOADS = listOf(
        "https://data.cityofnewyork.us/resource/mzxg-pwib.geojson?\$limit=100000",
        "https://data.cityofnewyork.us/api/v3/views/mzxg-pwib/query.geojson?accessType=DOWNLOAD",
    )

    /** ArcGIS mirror of the same data, paged, used when NYC Open Data cannot be reached. */
    fun mirrorPage(offset: Int): String =
        "https://services9.arcgis.com/hsPsr2XddCBblVhb/arcgis/rest/services/NYC%20Bicycle%20Lanes/FeatureServer/0/query" +
            "?where=1%3D1&outFields=street,facilitycl,ft_facilit,tf_facilit,bikedir,fromstreet,tostreet" +
            "&returnGeometry=true&outSR=4326&f=geojson&resultOffset=$offset&resultRecordCount=2000"

    const val MIRROR_PAGE_SIZE = 2000

    /**
     * Maps NYC DOT facility types to the shared [Kind]s. The text fields ("Protected Path",
     * "Standard", "Sharrows", "Greenway"...) are more precise than the class (I, II, III).
     */
    fun classify(facilityClass: String?, ft: String?, tf: String?): String {
        val text = listOfNotNull(ft, tf).joinToString(" ").lowercase()
        return when {
            listOf("greenway", "dirt trail", "boardwalk", "ped plaza").any { it in text } -> Kind.PATH
            "protected" in text -> Kind.PROTECTED
            listOf("conventional", "standard", "curbside", "buffered", "bike lane").any { it in text } -> Kind.LANE
            listOf("sharrow", "signed route", "shared", "bike-friendly", "sidewalk").any { it in text } -> Kind.SHARED
            else -> when (facilityClass?.trim()?.uppercase()) {
                "I" -> Kind.PROTECTED
                "II" -> Kind.LANE
                "III" -> Kind.SHARED
                else -> Kind.OTHER
            }
        }
    }

    data class Page(val features: List<String>, val more: Boolean)

    /** Normalizes a GeoJSON download (any of the sources) into small features. */
    fun parse(json: String): Page {
        val root = JSONObject(json)
        root.optJSONObject("error")?.let { throw IOException(it.optString("message", "Server error")) }
        val features = root.optJSONArray("features") ?: throw IOException("Unexpected NYC response")
        val out = ArrayList<String>(features.length())
        for (i in 0 until features.length()) {
            val f = features.optJSONObject(i) ?: continue
            val geometry = f.optJSONObject("geometry") ?: continue
            val raw = f.optJSONObject("properties") ?: JSONObject()
            // Column names differ in case between the sources, so look them up case-insensitively.
            val p = HashMap<String, String>()
            for (k in raw.keys()) {
                val v = raw.opt(k)
                if (v != null && v != JSONObject.NULL) p[k.lowercase()] = v.toString()
            }
            val ft = p["ft_facilit"]
            val tf = p["tf_facilit"]
            val detail = listOfNotNull(ft, tf).filter { it.isNotBlank() }.distinct().joinToString(" / ")
            val from = p["fromstreet"].orEmpty()
            val to = p["tostreet"].orEmpty()
            val props = JSONObject()
                .put("src", "nyc")
                .put("kind", classify(p["facilitycl"], ft, tf))
                .put("name", p["street"].orEmpty())
                .put("detail", detail)
                .put("span", if (from.isNotBlank() && to.isNotBlank()) "$from to $to" else "")
            out += JSONObject()
                .put("type", "Feature")
                .put("properties", props)
                .put("geometry", geometry)
                .toString()
        }
        val more = root.optBoolean("exceededTransferLimit") ||
            root.optJSONObject("properties")?.optBoolean("exceededTransferLimit") == true
        return Page(out, more)
    }
}
