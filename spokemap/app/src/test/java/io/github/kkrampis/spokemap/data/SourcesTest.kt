package io.github.kkrampis.spokemap.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcesTest {
    @Test
    fun citiBikeDiscoveryAndMerge() {
        val discovery = """
            {"data":{"en":{"feeds":[
              {"name":"system_information","url":"https://x/en/system_information.json"},
              {"name":"station_information","url":"https://x/en/station_information.json"},
              {"name":"station_status","url":"https://x/en/station_status.json"}]}}}
        """.trimIndent()
        assertEquals(
            "https://x/en/station_information.json" to "https://x/en/station_status.json",
            CitiBike.feedUrls(discovery),
        )

        val info = """{"data":{"stations":[
            {"station_id":"a","name":"Grove St PATH","lat":40.7197,"lon":-74.0431,"capacity":30},
            {"station_id":"b","name":"Removed","lat":40.7,"lon":-74.0,"capacity":10}]}}"""
        val status = """{"data":{"stations":[
            {"station_id":"a","num_bikes_available":5,"num_ebikes_available":2,"num_docks_available":25,
             "is_installed":1,"is_renting":1,"is_returning":1,"last_reported":1700000000},
            {"station_id":"b","num_bikes_available":0,"num_docks_available":0,"is_installed":0,"is_renting":0}]}}"""
        val stations = CitiBike.merge(info, status)
        assertEquals(1, stations.size)
        val s = stations[0]
        assertEquals("Grove St PATH", s.name)
        assertEquals(5, s.bikes)
        assertEquals(2, s.ebikes)
        assertTrue(s.renting)

        val gj = JSONObject(CitiBike.toGeoJson(stations))
        assertEquals(1, gj.getJSONArray("features").length())
    }

    @Test
    fun njStressPageParsing() {
        val json = """{"type":"FeatureCollection","exceededTransferLimit":true,"features":[
            {"type":"Feature","id":7,"geometry":{"type":"LineString","coordinates":[[-74.1,40.7],[-74.11,40.71]]},
             "properties":{"FinalLOS":3.0,"RouteST":"Kennedy Blvd","BikeLane":null,"BikePath":"No","SpdLimit":35}}]}"""
        val page = NjStress.parse(json)
        assertTrue(page.more)
        assertEquals(7L, page.features[0].first)
        val p = JSONObject(page.features[0].second).getJSONObject("properties")
        assertEquals(3, p.getInt("los"))
        assertEquals("Kennedy Blvd", p.getString("name"))
        assertEquals("", p.getString("bikelane"))
    }

    @Test
    fun nycDotClassification() {
        assertEquals(Kind.PROTECTED, NycDot.classify("I", "Protected Path", null))
        assertEquals(Kind.LANE, NycDot.classify("II", "Standard", "Standard"))
        assertEquals(Kind.SHARED, NycDot.classify("III", "Sharrows", null))
        assertEquals(Kind.PATH, NycDot.classify("I", "Greenway", null))
        assertEquals(Kind.LANE, NycDot.classify("II", null, null))

        val json = """{"type":"FeatureCollection","features":[
            {"type":"Feature","geometry":{"type":"MultiLineString","coordinates":[[[-73.99,40.73],[-73.98,40.74]]]},
             "properties":{"street":"1 AV","FacilityCl":"I","ft_facilit":"Protected Path","fromstreet":"E 1 ST","tostreet":"E 14 ST"}}]}"""
        val page = NycDot.parse(json)
        assertFalse(page.more)
        val p = JSONObject(page.features[0]).getJSONObject("properties")
        assertEquals("protected", p.getString("kind"))
        assertEquals("1 AV", p.getString("name"))
        assertEquals("E 1 ST to E 14 ST", p.getString("span"))
    }
}
