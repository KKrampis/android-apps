package io.github.kkrampis.stockvault.data

import java.io.File

/**
 * Daily price history per symbol, cached on the phone so backtests and analysis don't download
 * decades of prices every time. A cached history is refreshed once it is older than [maxAgeMs].
 */
class HistoryCache(private val dir: File, private val maxAgeMs: Long = 6 * 3600_000L) {
    init {
        dir.mkdirs()
    }

    fun get(symbol: String, allowStale: Boolean = false): String? {
        val f = fileFor(symbol)
        if (!f.exists()) return null
        if (!allowStale && System.currentTimeMillis() - f.lastModified() > maxAgeMs) return null
        return runCatching { f.readText() }.getOrNull()
    }

    fun put(symbol: String, text: String) {
        runCatching {
            val f = fileFor(symbol)
            val tmp = File(dir, f.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(f)) {
                f.writeText(text)
                tmp.delete()
            }
        }
    }

    fun clear() = dir.listFiles()?.forEach { it.delete() }

    fun sizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0

    private fun fileFor(symbol: String) =
        File(dir, symbol.uppercase().replace(Regex("[^A-Z0-9._=^-]"), "_") + ".json")
}
