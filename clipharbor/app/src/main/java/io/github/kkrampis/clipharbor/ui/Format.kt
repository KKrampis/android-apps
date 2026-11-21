package io.github.kkrampis.clipharbor.ui

import java.util.Locale

fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun formatCount(n: Long): String = when {
    n < 0 -> ""
    n < 1_000 -> n.toString()
    n < 1_000_000 -> trim(n / 1_000.0) + "K"
    n < 1_000_000_000 -> trim(n / 1_000_000.0) + "M"
    else -> trim(n / 1_000_000_000.0) + "B"
}

private fun trim(v: Double): String =
    if (v >= 100) v.toInt().toString() else String.format(Locale.US, "%.1f", v).removeSuffix(".0")

fun viewsLine(views: Long, uploaded: String?): String =
    listOfNotNull(
        formatCount(views).takeIf { it.isNotEmpty() }?.let { "$it views" },
        uploaded?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
