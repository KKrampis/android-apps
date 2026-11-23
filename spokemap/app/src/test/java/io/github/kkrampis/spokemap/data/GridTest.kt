package io.github.kkrampis.spokemap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridTest {
    @Test
    fun coverContainsTheViewport() {
        val view = Bbox(40.70, -74.03, 40.76, -73.97)
        val cells = Grid.cells(view)
        val cover = Grid.cover(cells)
        assertTrue(cover.south <= view.south && cover.north >= view.north)
        assertTrue(cover.west <= view.west && cover.east >= view.east)
        assertEquals(cells.toSet().size, cells.size)
    }

    @Test
    fun collectionOfNothingIsEmpty() {
        assertEquals(GeoJson.EMPTY, GeoJson.collection(emptyList()))
        assertEquals("""{"type":"FeatureCollection","features":[{"a":1},{"b":2}]}""", GeoJson.collection(listOf("""{"a":1}""", """{"b":2}""")))
    }
}
