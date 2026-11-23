package io.github.kkrampis.spokemap.data

import java.util.Locale
import kotlin.math.floor

/** A lat/lon box. */
data class Bbox(val south: Double, val west: Double, val north: Double, val east: Double) {
    /** Overpass order: south,west,north,east. */
    fun overpass(): String = String.format(Locale.US, "%.5f,%.5f,%.5f,%.5f", south, west, north, east)

    /** ArcGIS envelope order: xmin,ymin,xmax,ymax. */
    fun envelope(): String = String.format(Locale.US, "%.5f,%.5f,%.5f,%.5f", west, south, east, north)
}

/** One square of the loading grid. Lanes are fetched per cell so panning back costs nothing. */
data class Cell(val x: Int, val y: Int) {
    val bbox: Bbox
        get() = Bbox(y * Grid.STEP, x * Grid.STEP, (y + 1) * Grid.STEP, (x + 1) * Grid.STEP)
}

object Grid {
    /** About 5.5 km north to south, 4.2 km east to west around New York. */
    const val STEP = 0.05

    fun cells(box: Bbox): List<Cell> {
        val x0 = floor(box.west / STEP).toInt()
        val x1 = floor(box.east / STEP).toInt()
        val y0 = floor(box.south / STEP).toInt()
        val y1 = floor(box.north / STEP).toInt()
        val out = ArrayList<Cell>()
        for (y in y0..y1) for (x in x0..x1) out += Cell(x, y)
        return out
    }

    /** The smallest box covering all the cells. */
    fun cover(cells: Collection<Cell>): Bbox {
        require(cells.isNotEmpty())
        return Bbox(
            south = cells.minOf { it.y } * STEP,
            west = cells.minOf { it.x } * STEP,
            north = (cells.maxOf { it.y } + 1) * STEP,
            east = (cells.maxOf { it.x } + 1) * STEP,
        )
    }
}

/** Kinds of bike facility, shared by the OpenStreetMap and NYC DOT layers so they use one legend. */
object Kind {
    const val PATH = "path"
    const val PROTECTED = "protected"
    const val LANE = "lane"
    const val SHARED = "shared"
    const val OTHER = "other"

    fun label(kind: String?): String = when (kind) {
        PATH -> "Off-street path or greenway"
        PROTECTED -> "Protected bike lane"
        LANE -> "Painted bike lane"
        SHARED -> "Shared lane or signed route"
        else -> "Other bike facility"
    }
}

object GeoJson {
    const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

    /** Joins already-serialized features into one FeatureCollection. */
    fun collection(features: Collection<String>): String {
        if (features.isEmpty()) return EMPTY
        val sb = StringBuilder(features.sumOf { it.length } + 64)
        sb.append("""{"type":"FeatureCollection","features":[""")
        var first = true
        for (f in features) {
            if (!first) sb.append(',')
            sb.append(f)
            first = false
        }
        sb.append("]}")
        return sb.toString()
    }
}
