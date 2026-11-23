package io.github.kkrampis.spokemap.ui

import io.github.kkrampis.spokemap.data.Kind

/** Map colors as ARGB ints, shared by the map layers and the legend. */
object Palette {
    const val KIND_PATH = 0xFF15803D.toInt()       // deep green
    const val KIND_PROTECTED = 0xFF22C55E.toInt()  // bright green
    const val KIND_LANE = 0xFF2563EB.toInt()       // blue
    const val KIND_SHARED = 0xFFF59E0B.toInt()     // amber
    const val KIND_OTHER = 0xFF94A3B8.toInt()      // slate

    /** Traffic stress 1 (calm) to 5 (most stressful). */
    val STRESS = intArrayOf(
        0xFF16A34A.toInt(),
        0xFF84CC16.toInt(),
        0xFFFACC15.toInt(),
        0xFFF97316.toInt(),
        0xFFDC2626.toInt(),
    )
    const val STRESS_UNKNOWN = 0xFF94A3B8.toInt()

    const val STATION_OK = 0xFF0EA5E9.toInt()
    const val STATION_FEW = 0xFFF59E0B.toInt()
    const val STATION_EMPTY = 0xFF64748B.toInt()
    const val STATION_CLOSED = 0xFF1F2937.toInt()
    const val STATION_STROKE = 0xFFFFFFFF.toInt()

    fun kind(kind: String?): Int = when (kind) {
        Kind.PATH -> KIND_PATH
        Kind.PROTECTED -> KIND_PROTECTED
        Kind.LANE -> KIND_LANE
        Kind.SHARED -> KIND_SHARED
        else -> KIND_OTHER
    }
}
