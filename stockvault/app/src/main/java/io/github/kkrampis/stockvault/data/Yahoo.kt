package io.github.kkrampis.stockvault.data

import io.github.kkrampis.stockvault.model.Chart
import io.github.kkrampis.stockvault.model.ChartRange
import io.github.kkrampis.stockvault.model.Details
import io.github.kkrampis.stockvault.model.NewsItem
import io.github.kkrampis.stockvault.model.SearchHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Yahoo Finance's public web endpoints, the ones its own site uses. There is no API key and no
 * official contract, so every call fails soft: the app shows what it got and says what it could not get.
 *
 * - `v8/finance/chart`: prices, history (split and dividend adjusted), dividends, splits. No sign-in.
 * - `v1/finance/search`: symbol lookup and news. No sign-in.
 * - `v6/finance/recommendationsbysymbol`: "people also watch", used as quick similar stocks.
 * - `v10/finance/quoteSummary`: valuation, financials and company profile. Needs a cookie and a
 *   "crumb" token, which Yahoo hands to any visitor; the client fetches them once per session.
 */
class Yahoo(client: OkHttpClient = Http.client) {
    private val cookies = MemoryCookieJar()
    private val http = client.newBuilder().cookieJar(cookies).build()
    private val crumbLock = Mutex()
    @Volatile private var crumb: String? = null

    suspend fun chart(symbol: String, range: ChartRange): Chart =
        chart(symbol, range.range, range.interval)

    /** Daily bars for the whole history, with dividends and splits. */
    suspend fun dailyHistory(symbol: String): Chart = YahooParse.chart(dailyHistoryText(symbol))

    /** The raw daily history JSON, which the app caches on disk. */
    suspend fun dailyHistoryText(symbol: String): String = withContext(Dispatchers.IO) {
        get(chartUrl(symbol, "max", "1d"), allowErrorBody = true)
    }

    suspend fun chart(symbol: String, range: String, interval: String): Chart = withContext(Dispatchers.IO) {
        YahooParse.chart(get(chartUrl(symbol, range, interval), allowErrorBody = true))
    }

    private fun chartUrl(symbol: String, range: String, interval: String): HttpUrl {
        return "$Q2/v8/finance/chart/${enc(symbol)}".toHttpUrl().newBuilder()
            .addQueryParameter("range", range)
            .addQueryParameter("interval", interval)
            .addQueryParameter("includePrePost", "false")
            .addQueryParameter("events", "div,split")
            .addQueryParameter("includeAdjustedClose", "true")
            .build()
    }

    suspend fun search(query: String, quotes: Int = 12, news: Int = 8): Pair<List<SearchHit>, List<NewsItem>> =
        withContext(Dispatchers.IO) {
            val url = "$Q2/v1/finance/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("quotesCount", quotes.toString())
                .addQueryParameter("newsCount", news.toString())
                .addQueryParameter("enableFuzzyQuery", "true")
                .addQueryParameter("listsCount", "0")
                .build()
            YahooParse.search(get(url))
        }

    suspend fun recommendations(symbol: String): List<String> = withContext(Dispatchers.IO) {
        YahooParse.recommendations(get("$Q2/v6/finance/recommendationsbysymbol/${enc(symbol)}".toHttpUrl()))
    }

    suspend fun details(symbol: String): Details = withContext(Dispatchers.IO) {
        val modules = "price,summaryDetail,defaultKeyStatistics,financialData,assetProfile,calendarEvents,fundProfile"
        fun url(c: String) = "$Q2/v10/finance/quoteSummary/${enc(symbol)}".toHttpUrl().newBuilder()
            .addQueryParameter("modules", modules)
            .addQueryParameter("formatted", "true")
            .addQueryParameter("crumb", c)
            .build()
        try {
            YahooParse.quoteSummary(get(url(crumb())))
        } catch (e: HttpException) {
            if (e.code != 401 && e.code != 403) throw e
            // The crumb expired: get a new one and try once more.
            crumb = null
            YahooParse.quoteSummary(get(url(crumb())))
        }
    }

    private suspend fun crumb(): String = crumbLock.withLock {
        crumb?.let { return it }
        // Any Yahoo page sets the session cookie; fc.yahoo.com answers quickly (with a 404, which is fine).
        runCatching { http.newCall(request("https://fc.yahoo.com/".toHttpUrl())).execute().close() }
        val c = get("$Q2/v1/test/getcrumb".toHttpUrl()).trim()
        if (c.isBlank() || c.contains("<") || c.length > 64) throw DataException("Yahoo did not give a session token")
        crumb = c
        c
    }

    private fun get(url: HttpUrl, allowErrorBody: Boolean = false): String {
        http.newCall(request(url)).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (res.isSuccessful) return body
            // The chart endpoint explains bad symbols in a JSON body with a 404.
            if (allowErrorBody && body.startsWith("{")) return body
            throw HttpException(res.code, when (res.code) {
                429 -> "Yahoo Finance is limiting requests. Wait a minute and try again."
                404 -> "Not found"
                else -> "Yahoo Finance answered ${res.code}"
            })
        }
    }

    private fun request(url: HttpUrl) = Request.Builder().url(url)
        .header("User-Agent", Http.BROWSER_UA)
        .header("Accept", "application/json,text/plain,*/*")
        .header("Accept-Language", "en-US,en;q=0.9")
        .build()

    private fun enc(symbol: String) = java.net.URLEncoder.encode(symbol.trim(), "UTF-8")

    class HttpException(val code: Int, message: String) : IOException(message)

    private class MemoryCookieJar : CookieJar {
        private val store = mutableMapOf<String, Cookie>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { store["${it.domain}|${it.name}"] = it }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            store.values.removeAll { it.expiresAt < now }
            return store.values.filter { it.matches(url) }
        }
    }

    companion object {
        const val Q2 = "https://query2.finance.yahoo.com"
    }
}

object Http {
    /** A current desktop browser. Yahoo and Google serve reduced or blocked pages to unknown clients. */
    const val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}
