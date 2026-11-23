package io.github.kkrampis.spokemap.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverpassTest {
    @Test
    fun classifiesCommonTags() {
        assertEquals(Kind.PATH, Overpass.classify(mapOf("highway" to "cycleway")))
        assertEquals(Kind.PATH, Overpass.classify(mapOf("highway" to "path", "bicycle" to "designated")))
        assertEquals(Kind.PROTECTED, Overpass.classify(mapOf("highway" to "primary", "cycleway:right" to "track")))
        assertEquals(Kind.LANE, Overpass.classify(mapOf("highway" to "secondary", "cycleway" to "lane")))
        assertEquals(Kind.SHARED, Overpass.classify(mapOf("highway" to "residential", "cycleway:both" to "shared_lane")))
        assertNull(Overpass.classify(mapOf("highway" to "residential", "cycleway" to "no")))
        assertNull(Overpass.classify(mapOf("highway" to "primary", "cycleway:left" to "separate")))
        assertNull(Overpass.classify(mapOf("highway" to "footway")))
    }

    @Test
    fun protectedWinsOverPaintedOnOppositeSides() {
        assertEquals(
            Kind.PROTECTED,
            Overpass.classify(mapOf("cycleway:left" to "lane", "cycleway:right" to "track")),
        )
    }

    @Test
    fun parsesWaysIntoLineStrings() {
        val json = """
            {"elements":[
              {"type":"way","id":42,"tags":{"highway":"cycleway","name":"Hudson River Greenway"},
               "geometry":[{"lat":40.7,"lon":-74.01},{"lat":40.71,"lon":-74.012}]},
              {"type":"way","id":43,"tags":{"highway":"residential","cycleway":"no"},
               "geometry":[{"lat":40.7,"lon":-74.0},{"lat":40.71,"lon":-74.0}]},
              {"type":"node","id":1,"lat":40.7,"lon":-74.0}
            ]}
        """.trimIndent()
        val features = Overpass.parse(json)
        assertEquals(1, features.size)
        assertEquals(42L, features[0].first)
        val f = JSONObject(features[0].second)
        assertEquals("path", f.getJSONObject("properties").getString("kind"))
        assertEquals("Hudson River Greenway", f.getJSONObject("properties").getString("name"))
        val coords = f.getJSONObject("geometry").getJSONArray("coordinates")
        assertEquals(-74.01, coords.getJSONArray(0).getDouble(0), 1e-9)
        assertEquals(40.7, coords.getJSONArray(0).getDouble(1), 1e-9)
    }

    @Test
    fun queryUsesSouthWestNorthEast() {
        val q = Overpass.query(Bbox(40.5, -74.3, 40.9, -73.7))
        assertTrue(q.contains("(40.50000,-74.30000,40.90000,-73.70000)"))
        assertTrue(q.startsWith("[out:json]"))
    }
}
