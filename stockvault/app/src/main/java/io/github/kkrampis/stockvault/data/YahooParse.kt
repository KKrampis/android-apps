package io.github.kkrampis.stockvault.data

import io.github.kkrampis.stockvault.model.Bar
import io.github.kkrampis.stockvault.model.Chart
import io.github.kkrampis.stockvault.model.Details
import io.github.kkrampis.stockvault.model.Dividend
import io.github.kkrampis.stockvault.model.Format
import io.github.kkrampis.stockvault.model.NewsItem
import io.github.kkrampis.stockvault.model.SearchHit
import io.github.kkrampis.stockvault.model.Split
import io.github.kkrampis.stockvault.model.Stat
import org.json.JSONArray
import org.json.JSONObject

class DataException(message: String) : Exception(message)

/** Turns Yahoo Finance's JSON into the app's models. Kept free of networking so it can be unit tested. */
object YahooParse {

    fun chart(text: String): Chart {
        val root = JSONObject(text).optJSONObject("chart") ?: throw DataException("Unexpected chart response")
        root.optJSONObject("error")?.let { e ->
            throw DataException(e.optString("description").ifBlank { e.optString("code", "Chart error") })
        }
        val r = root.optJSONArray("result")?.optJSONObject(0) ?: throw DataException("No data for that symbol")
        val meta = r.optJSONObject("meta") ?: JSONObject()
        val offset = meta.optLong("gmtoffset", 0)
        val ts = r.optJSONArray("timestamp") ?: JSONArray()
        val q = r.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0) ?: JSONObject()
        val adj = r.optJSONObject("indicators")?.optJSONArray("adjclose")?.optJSONObject(0)?.optJSONArray("adjclose")
        val open = q.optJSONArray("open")
        val high = q.optJSONArray("high")
        val low = q.optJSONArray("low")
        val close = q.optJSONArray("close")
        val volume = q.optJSONArray("volume")
        val bars = ArrayList<Bar>(ts.length())
        for (i in 0 until ts.length()) {
            val c = close.num(i) ?: continue // Yahoo leaves gaps as null
            val t = ts.optLong(i)
            bars += Bar(
                time = t,
                day = Math.floorDiv(t + offset, 86_400L),
                open = open.num(i),
                high = high.num(i),
                low = low.num(i),
                close = c,
                adjClose = adj.num(i) ?: c,
                volume = volume.num(i)?.toLong(),
            )
        }
        val events = r.optJSONObject("events")
        val dividends = events?.optJSONObject("dividends")?.let { d ->
            d.keys().asSequence().mapNotNull { k ->
                val o = d.optJSONObject(k) ?: return@mapNotNull null
                Dividend(o.optLong("date", k.toLongOrNull() ?: 0), o.optDouble("amount", 0.0))
            }.sortedBy { it.time }.toList()
        }.orEmpty()
        val splits = events?.optJSONObject("splits")?.let { d ->
            d.keys().asSequence().mapNotNull { k ->
                val o = d.optJSONObject(k) ?: return@mapNotNull null
                Split(o.optLong("date"), o.optDouble("numerator", 1.0), o.optDouble("denominator", 1.0))
            }.sortedBy { it.time }.toList()
        }.orEmpty()

        val symbol = meta.optString("symbol")
        return Chart(
            symbol = symbol,
            name = meta.optString("longName").ifBlank { meta.optString("shortName") }.ifBlank { symbol },
            currency = meta.optString("currency"),
            exchange = meta.optString("exchangeName"),
            exchangeName = meta.optString("fullExchangeName").ifBlank { meta.optString("exchangeName") },
            instrumentType = meta.optString("instrumentType"),
            timezone = meta.optString("exchangeTimezoneName"),
            price = meta.num("regularMarketPrice") ?: bars.lastOrNull()?.close,
            // For a one-day chart this is yesterday's close; for longer ranges, the close before the range.
            previousClose = meta.num("previousClose") ?: meta.num("chartPreviousClose"),
            dayHigh = meta.num("regularMarketDayHigh"),
            dayLow = meta.num("regularMarketDayLow"),
            volume = meta.num("regularMarketVolume")?.toLong(),
            fiftyTwoWeekHigh = meta.num("fiftyTwoWeekHigh"),
            fiftyTwoWeekLow = meta.num("fiftyTwoWeekLow"),
            marketTime = meta.num("regularMarketTime")?.toLong(),
            firstTradeDate = meta.num("firstTradeDate")?.toLong(),
            bars = bars,
            dividends = dividends,
            splits = splits,
        )
    }

    fun search(text: String): Pair<List<SearchHit>, List<NewsItem>> {
        val o = JSONObject(text)
        val hits = o.optJSONArray("quotes").objects().mapNotNull { q ->
            val sym = q.optString("symbol")
            if (sym.isBlank() || q.optString("isYahooFinance", "true") == "false") return@mapNotNull null
            SearchHit(
                symbol = sym,
                name = q.optString("longname").ifBlank { q.optString("shortname") }.ifBlank { sym },
                exchange = q.optString("exchange"),
                exchangeName = q.optString("exchDisp").ifBlank { q.optString("exchange") },
                type = q.optString("typeDisp").ifBlank { q.optString("quoteType") },
                sector = q.optString("sectorDisp").ifBlank { q.optString("sector") }.ifBlank { null },
                industry = q.optString("industryDisp").ifBlank { q.optString("industry") }.ifBlank { null },
            )
        }
        val news = o.optJSONArray("news").objects().mapNotNull { n ->
            val link = n.optString("link")
            if (link.isBlank()) null else NewsItem(
                title = n.optString("title"),
                publisher = n.optString("publisher"),
                url = link,
                time = n.optLong("providerPublishTime").takeIf { it > 0 },
            )
        }
        return hits to news
    }

    /** Symbols Yahoo shows as "People also watch" for a stock. */
    fun recommendations(text: String): List<String> =
        JSONObject(text).optJSONObject("finance")?.optJSONArray("result")?.optJSONObject(0)
            ?.optJSONArray("recommendedSymbols").objects().map { it.optString("symbol") }.filter { it.isNotBlank() }

    private val statFields = listOf(
        // module, field, label
        Triple("summaryDetail", "previousClose", "Previous close"),
        Triple("summaryDetail", "open", "Open"),
        Triple("summaryDetail", "dayLow", "Day low"),
        Triple("summaryDetail", "dayHigh", "Day high"),
        Triple("summaryDetail", "fiftyTwoWeekLow", "52-week low"),
        Triple("summaryDetail", "fiftyTwoWeekHigh", "52-week high"),
        Triple("summaryDetail", "volume", "Volume"),
        Triple("summaryDetail", "averageVolume", "Avg volume (3 mo)"),
        Triple("summaryDetail", "marketCap", "Market cap"),
        Triple("defaultKeyStatistics", "enterpriseValue", "Enterprise value"),
        Triple("summaryDetail", "trailingPE", "P/E ratio (TTM)"),
        Triple("summaryDetail", "forwardPE", "Forward P/E"),
        Triple("defaultKeyStatistics", "pegRatio", "PEG ratio"),
        Triple("defaultKeyStatistics", "priceToBook", "Price/book"),
        Triple("summaryDetail", "priceToSalesTrailing12Months", "Price/sales (TTM)"),
        Triple("defaultKeyStatistics", "trailingEps", "EPS (TTM)"),
        Triple("defaultKeyStatistics", "forwardEps", "Forward EPS"),
        Triple("summaryDetail", "dividendRate", "Dividend (yearly)"),
        Triple("summaryDetail", "dividendYield", "Dividend yield"),
        Triple("summaryDetail", "yield", "Yield"),
        Triple("summaryDetail", "exDividendDate", "Ex-dividend date"),
        Triple("summaryDetail", "payoutRatio", "Payout ratio"),
        Triple("summaryDetail", "beta", "Beta (5Y monthly)"),
        Triple("summaryDetail", "fiftyDayAverage", "50-day average"),
        Triple("summaryDetail", "twoHundredDayAverage", "200-day average"),
        Triple("defaultKeyStatistics", "52WeekChange", "52-week change"),
        Triple("summaryDetail", "totalAssets", "Net assets"),
        Triple("defaultKeyStatistics", "sharesOutstanding", "Shares outstanding"),
        Triple("defaultKeyStatistics", "floatShares", "Float"),
        Triple("defaultKeyStatistics", "heldPercentInsiders", "Held by insiders"),
        Triple("defaultKeyStatistics", "heldPercentInstitutions", "Held by institutions"),
        Triple("defaultKeyStatistics", "shortPercentOfFloat", "Short % of float"),
        Triple("financialData", "totalRevenue", "Revenue (TTM)"),
        Triple("financialData", "revenueGrowth", "Revenue growth (YoY)"),
        Triple("financialData", "earningsGrowth", "Earnings growth (YoY)"),
        Triple("financialData", "grossMargins", "Gross margin"),
        Triple("financialData", "operatingMargins", "Operating margin"),
        Triple("financialData", "profitMargins", "Profit margin"),
        Triple("financialData", "returnOnEquity", "Return on equity"),
        Triple("financialData", "returnOnAssets", "Return on assets"),
        Triple("financialData", "totalCash", "Cash"),
        Triple("financialData", "totalDebt", "Debt"),
        Triple("financialData", "debtToEquity", "Debt/equity"),
        Triple("financialData", "freeCashflow", "Free cash flow"),
        Triple("calendarEvents", "earningsDate", "Next earnings"),
    )

    fun quoteSummary(text: String): Details {
        val root = JSONObject(text).optJSONObject("quoteSummary") ?: throw DataException("Unexpected summary response")
        root.optJSONObject("error")?.let { throw DataException(it.optString("description", "Summary error")) }
        val r = root.optJSONArray("result")?.optJSONObject(0) ?: throw DataException("No summary")
        val stats = statFields.mapNotNull { (module, field, label) ->
            val m = r.optJSONObject(module) ?: return@mapNotNull null
            val v = when (val raw = m.opt(field)) {
                is JSONObject -> raw.optString("fmt").ifBlank { raw.optString("longFmt") }
                // earningsDate is a list of dates
                is JSONArray -> raw.objects().map { it.optString("fmt") }.filter { it.isNotBlank() }.joinToString(" – ")
                is String -> raw
                else -> ""
            }
            if (v.isBlank()) null else Stat(label, v)
        }
        val profile = r.optJSONObject("assetProfile") ?: r.optJSONObject("summaryProfile")
        val fin = r.optJSONObject("financialData")
        val target = fin?.optJSONObject("targetMeanPrice")?.optString("fmt")?.takeIf { it.isNotBlank() }
        val analysts = fin?.optJSONObject("numberOfAnalystOpinions")?.optString("fmt")?.takeIf { it.isNotBlank() }
        val rec = fin?.optString("recommendationKey")?.takeIf { it.isNotBlank() && it != "none" }
            ?.replace('_', ' ')?.replaceFirstChar { it.uppercase() }
        val hq = profile?.let {
            listOf(it.optString("city"), it.optString("state"), it.optString("country")).filter { s -> s.isNotBlank() }
                .joinToString(", ").ifBlank { null }
        }
        return Details(
            stats = stats,
            about = profile?.optString("longBusinessSummary")?.ifBlank { null }
                ?: r.optJSONObject("fundProfile")?.optString("categoryName")?.ifBlank { null },
            sector = profile?.optString("sector")?.ifBlank { null },
            industry = profile?.optString("industry")?.ifBlank { null },
            website = profile?.optString("website")?.ifBlank { null },
            employees = profile?.optLong("fullTimeEmployees")?.takeIf { it > 0 }?.let { Format.big(it.toDouble()) },
            headquarters = hq,
            analystTarget = target?.let { t -> if (analysts != null) "$t ($analysts analysts)" else t },
            recommendation = rec,
            source = "Yahoo Finance",
        )
    }

    /** The few stats the chart endpoint already gives, for when the summary is not available. */
    fun basicStats(chart: Chart): List<Stat> = buildList {
        chart.previousClose?.let { add(Stat("Previous close", Format.price(it, chart.currency))) }
        if (chart.dayLow != null && chart.dayHigh != null) {
            add(Stat("Day range", "${Format.price(chart.dayLow, chart.currency)} – ${Format.price(chart.dayHigh, chart.currency)}"))
        }
        if (chart.fiftyTwoWeekLow != null && chart.fiftyTwoWeekHigh != null) {
            add(Stat("52-week range", "${Format.price(chart.fiftyTwoWeekLow, chart.currency)} – ${Format.price(chart.fiftyTwoWeekHigh, chart.currency)}"))
        }
        chart.volume?.let { add(Stat("Volume", Format.big(it.toDouble()))) }
        if (chart.exchangeName.isNotBlank()) add(Stat("Exchange", chart.exchangeName))
        if (chart.instrumentType.isNotBlank()) add(Stat("Type", chart.instrumentType.lowercase().replaceFirstChar { it.uppercase() }))
    }

    private fun JSONArray?.num(i: Int): Double? {
        if (this == null || i >= length() || isNull(i)) return null
        val d = optDouble(i)
        return if (d.isNaN()) null else d
    }

    private fun JSONObject.num(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val d = optDouble(key)
        return if (d.isNaN()) null else d
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
