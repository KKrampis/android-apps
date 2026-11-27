package io.github.kkrampis.stockvault.data

import io.github.kkrampis.stockvault.model.Chart
import io.github.kkrampis.stockvault.model.ChartRange
import io.github.kkrampis.stockvault.model.Details
import io.github.kkrampis.stockvault.model.NewsItem
import io.github.kkrampis.stockvault.model.Quote
import io.github.kkrampis.stockvault.model.SearchHit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/** Details from both sources; either can be missing when that source failed. */
data class DetailsBundle(val yahoo: Details?, val google: Details?, val googleUrl: String, val errors: List<String>)

/**
 * The one place screens get market data from. Quotes and charts are kept in memory for a minute;
 * daily histories are also cached on disk (see [HistoryCache]).
 */
class MarketRepository(
    private val cache: HistoryCache,
    private val yahoo: Yahoo = Yahoo(),
    private val google: GoogleFinance = GoogleFinance(),
) {
    private data class Timed<T>(val at: Long, val value: T)

    private val charts = ConcurrentHashMap<String, Timed<Chart>>()
    private val histories = ConcurrentHashMap<String, Timed<Chart>>()
    private val limit = Semaphore(4) // be gentle with Yahoo

    suspend fun quote(symbol: String, maxAgeMs: Long = 60_000): Quote = Quote.of(chart(symbol, ChartRange.D1, maxAgeMs))

    /** Quotes for many symbols; ones that fail are left out of the map. */
    suspend fun quotes(symbols: Collection<String>): Map<String, Quote> = coroutineScope {
        symbols.distinct().map { s -> async { runCatching { s to quote(s) }.getOrNull() } }
            .awaitAll().filterNotNull().toMap()
    }

    suspend fun chart(symbol: String, range: ChartRange, maxAgeMs: Long = 60_000): Chart {
        val key = "$symbol|${range.name}"
        charts[key]?.takeIf { System.currentTimeMillis() - it.at < maxAgeMs }?.let { return it.value }
        val c = limit.withPermit { yahoo.chart(symbol, range) }
        charts[key] = Timed(System.currentTimeMillis(), c)
        return c
    }

    /** All daily bars since listing, adjusted for splits and dividends. */
    suspend fun history(symbol: String, forceRefresh: Boolean = false): Chart {
        val key = symbol.uppercase()
        if (!forceRefresh) {
            histories[key]?.takeIf { System.currentTimeMillis() - it.at < 30 * 60_000 }?.let { return it.value }
            cache.get(key)?.let { text ->
                runCatching { YahooParse.chart(text) }.getOrNull()?.let {
                    histories[key] = Timed(System.currentTimeMillis(), it)
                    return it
                }
            }
        }
        return try {
            val text = limit.withPermit { yahoo.dailyHistoryText(symbol) }
            val c = YahooParse.chart(text)
            if (c.bars.isNotEmpty()) cache.put(key, text)
            histories[key] = Timed(System.currentTimeMillis(), c)
            c
        } catch (e: Exception) {
            // Offline or rate limited: an old cached copy is better than nothing.
            cache.get(key, allowStale = true)?.let { runCatching { YahooParse.chart(it) }.getOrNull() } ?: throw e
        }
    }

    suspend fun histories(symbols: Collection<String>): Map<String, Chart> = coroutineScope {
        symbols.distinct().map { s -> async { s to history(s) } }.awaitAll().toMap()
    }

    suspend fun details(symbol: String, exchange: String): DetailsBundle = coroutineScope {
        val y = async { runCatching { limit.withPermit { yahoo.details(symbol) } } }
        val g = async { runCatching { google.details(symbol, exchange) } }
        val yr = y.await()
        val gr = g.await()
        DetailsBundle(
            yahoo = yr.getOrNull(),
            google = gr.getOrNull()?.takeIf { it.stats.isNotEmpty() || it.about != null },
            googleUrl = GoogleFinanceParse.quoteUrl(symbol, exchange),
            errors = listOfNotNull(
                yr.exceptionOrNull()?.let { "Yahoo Finance: ${it.message}" },
                gr.exceptionOrNull()?.let { "Google Finance: ${it.message}" },
            ),
        )
    }

    suspend fun search(query: String): List<SearchHit> = yahoo.search(query, quotes = 15, news = 0).first

    suspend fun news(symbol: String): List<NewsItem> = yahoo.search(symbol, quotes = 1, news = 10).second

    suspend fun similar(symbol: String): List<String> = yahoo.recommendations(symbol)
}
