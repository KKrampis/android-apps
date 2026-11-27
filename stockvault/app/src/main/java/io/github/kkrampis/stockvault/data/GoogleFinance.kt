package io.github.kkrampis.stockvault.data

import io.github.kkrampis.stockvault.model.Details
import io.github.kkrampis.stockvault.model.Stat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Google Finance has no API, so this reads its quote page (`google.com/finance/quote/AAPL:NASDAQ`).
 * The stats table ("Previous close", "Market cap", "P/E ratio" …) and the "About" box are plain
 * HTML there; the chart is drawn by scripts, so prices and history come from Yahoo instead.
 * The app also opens the real page in a browser view, which always shows everything Google has.
 */
class GoogleFinance(private val client: OkHttpClient = Http.client) {

    suspend fun details(symbol: String, yahooExchange: String): Details = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(GoogleFinanceParse.quoteUrl(symbol, yahooExchange))
            .header("User-Agent", Http.BROWSER_UA)
            .header("Accept-Language", "en-US,en;q=0.9")
            // "Reject all" consent, so visitors in Europe get the page instead of the cookie screen.
            .header("Cookie", "SOCS=CAI; CONSENT=PENDING+987")
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw DataException("Google Finance answered ${res.code}")
            GoogleFinanceParse.details(res.body?.string().orEmpty())
        }
    }
}

object GoogleFinanceParse {
    /** Yahoo exchange codes and symbol suffixes, mapped to the exchange names Google Finance uses. */
    private val byYahooExchange = mapOf(
        "NMS" to "NASDAQ", "NGM" to "NASDAQ", "NCM" to "NASDAQ", "NAS" to "NASDAQ", "NASDAQ" to "NASDAQ",
        "NYQ" to "NYSE", "NYS" to "NYSE", "NYSE" to "NYSE",
        "ASE" to "NYSEAMERICAN", "PCX" to "NYSEARCA", "BTS" to "BATS",
        "PNK" to "OTCMKTS", "OQB" to "OTCMKTS", "OQX" to "OTCMKTS",
    )
    private val bySuffix = mapOf(
        "L" to "LON", "TO" to "TSE", "V" to "CVE", "DE" to "ETR", "F" to "FRA", "PA" to "EPA", "AS" to "AMS",
        "BR" to "EBR", "MI" to "BIT", "MC" to "BME", "SW" to "SWX", "ST" to "STO", "CO" to "CPH", "OL" to "OSL",
        "HE" to "HEL", "VI" to "VIE", "LS" to "ELI", "IR" to "ISE", "T" to "TYO", "HK" to "HKG", "SS" to "SHA",
        "SZ" to "SHE", "KS" to "KRX", "KQ" to "KOSDAQ", "TW" to "TPE", "AX" to "ASX", "NZ" to "NZE",
        "NS" to "NSE", "BO" to "BOM", "SI" to "SGX", "SA" to "BVMF", "MX" to "BMV", "JO" to "JSE", "TA" to "TLV",
        "WA" to "WSE", "IS" to "IST",
    )

    /** "AAPL:NASDAQ", "SHEL:LON", "^GSPC" → ".INX:INDEXSP", "BTC-USD" → "BTC-USD". */
    fun googleSymbol(symbol: String, yahooExchange: String): String {
        val s = symbol.uppercase()
        when (s) {
            "^GSPC" -> return ".INX:INDEXSP"
            "^DJI" -> return ".DJI:INDEXDJX"
            "^IXIC" -> return ".IXIC:INDEXNASDAQ"
            "^NDX" -> return "NDX:INDEXNASDAQ"
            "^RUT" -> return "RUT:INDEXRUSSELL"
            "^VIX" -> return "VIX:INDEXCBOE"
            "^FTSE" -> return "UKX:INDEXFTSE"
            "^GDAXI" -> return "DAX:INDEXDB"
            "^N225" -> return "NI225:INDEXNIKKEI"
        }
        // Currencies and crypto: "EURUSD=X" → "EUR-USD", "BTC-USD" stays.
        if (s.endsWith("=X")) {
            val pair = s.removeSuffix("=X")
            return if (pair.length == 6) "${pair.take(3)}-${pair.drop(3)}" else pair
        }
        if (yahooExchange == "CCC") return s
        val dot = s.lastIndexOf('.')
        if (dot > 0) {
            bySuffix[s.substring(dot + 1)]?.let { return "${s.substring(0, dot)}:$it" }
        }
        val base = s.replace('-', '.') // BRK-B on Yahoo is BRK.B on Google
        return byYahooExchange[yahooExchange.uppercase()]?.let { "$base:$it" } ?: base
    }

    fun quoteUrl(symbol: String, yahooExchange: String): String =
        "https://www.google.com/finance/quote/${googleSymbol(symbol, yahooExchange)}?hl=en"

    private val labels = listOf(
        "Previous close", "Day range", "Year range", "Market cap", "Avg Volume", "P/E ratio", "Dividend yield",
        "Primary exchange", "CEO", "Founded", "Headquarters", "Website", "Employees", "Expense ratio",
        "Net assets", "Price", "Category", "Morningstar rating", "Volume",
    )

    private val statRow = Regex(
        """<div class="mfs7Fc"[^>]*>(.*?)</div>.{0,1500}?<div class="P6K39c"[^>]*>(.*?)</div>""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val aboutBox = Regex("""<div class="bLLb2d"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)

    fun details(html: String): Details {
        val stats = statRow.findAll(html).mapNotNull { m ->
            val label = clean(m.groupValues[1])
            val value = clean(m.groupValues[2])
            if (label.isBlank() || value.isBlank() || label.length > 40) null else Stat(label, value)
        }.distinctBy { it.label }.toList().ifEmpty { fallbackStats(html) }
        val about = aboutBox.find(html)?.groupValues?.get(1)?.let(::clean)?.ifBlank { null }
        val map = stats.associate { it.label to it.value }
        return Details(
            stats = stats.filter { it.label !in setOf("Headquarters", "Website", "Employees") },
            about = about,
            website = map["Website"],
            employees = map["Employees"],
            headquarters = map["Headquarters"],
            source = "Google Finance",
        )
    }

    /**
     * If Google renames its CSS classes, find each known label as text and take the next short text
     * after its tooltip. Less exact, but keeps the stats coming until the parser is updated.
     */
    private fun fallbackStats(html: String): List<Stat> {
        val texts = Regex(">([^<>]{1,200})<").findAll(html).map { clean(it.groupValues[1]) }.filter { it.isNotBlank() }.toList()
        return labels.mapNotNull { label ->
            val i = texts.indexOf(label)
            if (i < 0) return@mapNotNull null
            val value = texts.drop(i + 1).take(4).firstOrNull { t ->
                t.length <= 40 && t !in labels && (t.any(Char::isDigit) || label in setOf("Primary exchange", "CEO", "Headquarters", "Website"))
            } ?: return@mapNotNull null
            Stat(label, value)
        }
    }

    private fun clean(s: String): String = s.replace(Regex("<[^>]+>"), " ")
        .replace("&amp;", "&").replace("&nbsp;", " ").replace("&#39;", "'").replace("&quot;", "\"")
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace(Regex("\\s+"), " ").trim()
}
