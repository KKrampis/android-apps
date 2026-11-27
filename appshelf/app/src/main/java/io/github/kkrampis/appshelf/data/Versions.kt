package io.github.kkrampis.appshelf.data

/** Version numbers as written in release names, tags and Android's versionName. */
object Versions {
    private val NUMBER = Regex("""(?<![A-Za-z0-9])v?(\d+(?:\.\d+)+|\d+)(?:[-+.]?([A-Za-z][A-Za-z0-9.]*))?""")

    /** The first version-looking number in [text], such as `1.0.4` in `StockVault 1.0.4`. */
    fun find(text: String): String? {
        // Prefer dotted versions over lone numbers ("App 2 v1.3" is 1.3).
        val matches = NUMBER.findAll(text).toList()
        val m = matches.firstOrNull { '.' in it.groupValues[1] } ?: matches.firstOrNull() ?: return null
        val suffix = m.groupValues[2]
        return if (suffix.isNotEmpty() && suffix.lowercase().let { s -> PRE.any { s.startsWith(it) } }) {
            "${m.groupValues[1]}-$suffix"
        } else {
            m.groupValues[1]
        }
    }

    private val PRE = listOf("alpha", "beta", "rc", "pre", "dev", "preview", "snapshot")

    /**
     * Compares two versions number by number (`1.0.10` is newer than `1.0.9`). A version with a
     * pre-release suffix (`1.2-beta`) is older than the same version without one.
     */
    fun compare(a: String, b: String): Int {
        val na = numbers(a)
        val nb = numbers(b)
        for (i in 0 until maxOf(na.size, nb.size)) {
            val x = na.getOrElse(i) { 0L }
            val y = nb.getOrElse(i) { 0L }
            if (x != y) return x.compareTo(y)
        }
        val pa = isPre(a)
        val pb = isPre(b)
        return when {
            pa == pb -> 0
            pa -> -1
            else -> 1
        }
    }

    fun isNewer(candidate: String, installed: String?): Boolean =
        installed.isNullOrBlank() || compare(candidate, installed) > 0

    private fun numbers(v: String): List<Long> {
        val core = v.trim().removePrefix("v").removePrefix("V").split('-', '+', ' ').first()
        return core.split('.').map { part -> part.takeWhile { it.isDigit() }.toLongOrNull() ?: 0L }
    }

    private fun isPre(v: String): Boolean {
        val lower = v.lowercase()
        return PRE.any { lower.contains(it) }
    }
}
