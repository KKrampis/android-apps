package io.github.kkrampis.spokemap.data

import org.json.JSONObject
import java.io.IOException
import kotlin.math.roundToInt

/**
 * NJTPA "Bike Level of Traffic Stress" for northern New Jersey roads, from NJTPA's own ArcGIS
 * server (the dataset behind share-open-data-njtpa.hub.arcgis.com). FinalLOS runs from 1 (calm)
 * to 5 (most stressful). It has about 150,000 road segments, so it is fetched per map area.
 */
object NjStress {
    private const val QUERY =
        "https://gis.njtpa.org/server/rest/services/MapServices/BicycleLOSMapX/MapServer/0/query"
    const val PAGE_SIZE = 1000

    fun url(box: Bbox, offset: Int): String =
        QUERY + "?where=1%3D1" +
            "&geometry=" + box.envelope() +
            "&geometryType=esriGeometryEnvelope&inSR=4326&spatialRel=esriSpatialRelIntersects" +
            "&outFields=FinalLOS,BikeLane,BikePath,RouteST,SpdLimit" +
            "&returnGeometry=true&outSR=4326&f=geojson" +
            "&resultOffset=$offset&resultRecordCount=$PAGE_SIZE"

    data class Page(val features: List<Pair<Long, String>>, val more: Boolean)

    fun parse(json: String): Page {
        val root = JSONObject(json)
        root.optJSONObject("error")?.let { throw IOException(it.optString("message", "NJTPA server error")) }
        val features = root.optJSONArray("features") ?: throw IOException("Unexpected NJTPA response")
        val out = ArrayList<Pair<Long, String>>(features.length())
        for (i in 0 until features.length()) {
            val f = features.optJSONObject(i) ?: continue
            val geometry = f.optJSONObject("geometry") ?: continue
            val p = f.optJSONObject("properties") ?: JSONObject()
            val id = when {
                f.has("id") -> f.optLong("id")
                p.has("OBJECTID") -> p.optLong("OBJECTID")
                else -> geometry.toString().hashCode().toLong()
            }
            val los = p.optDouble("FinalLOS", Double.NaN).let { if (it.isNaN()) 0 else it.roundToInt() }
            val props = JSONObject()
                .put("src", "nj")
                .put("los", los)
                .put("name", p.optString("RouteST").takeUnless { it == "null" } ?: "")
                .put("bikelane", p.optString("BikeLane").takeUnless { it == "null" } ?: "")
                .put("bikepath", p.optString("BikePath").takeUnless { it == "null" } ?: "")
                .put("speed", p.optDouble("SpdLimit", 0.0).roundToInt())
            out += id to JSONObject()
                .put("type", "Feature")
                .put("properties", props)
                .put("geometry", geometry)
                .toString()
        }
        val more = root.optBoolean("exceededTransferLimit") ||
            root.optJSONObject("properties")?.optBoolean("exceededTransferLimit") == true
        return Page(out, more)
    }

    fun label(los: Int): String = when (los) {
        1 -> "1 of 5: comfortable for most riders"
        2 -> "2 of 5: comfortable for most adults"
        3 -> "3 of 5: for confident riders"
        4 -> "4 of 5: high stress"
        5 -> "5 of 5: very high stress"
        else -> "Not rated"
    }
}
