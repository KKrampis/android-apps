package io.github.kkrampis.stockvault.model

import java.time.LocalDate

/**
 * One trading day (or one bar of an intraday chart). [time] is epoch seconds; [day] is the trading
 * day in the exchange's own time zone (epoch day), so Sydney or Tokyo bars land on the right date.
 */
data class Bar(
    val time: Long,
    val day: Long,
    val open: Double?,
    val high: Double?,
    val low: Double?,
    val close: Double,
    /** Close adjusted for splits and dividends, so returns include reinvested dividends. */
    val adjClose: Double,
    val volume: Long?,
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(day)
}

data class Dividend(val time: Long, val amount: Double)
data class Split(val time: Long, val numerator: Double, val denominator: Double)

/** What the chart endpoint says about the instrument, plus the bars. */
data class Chart(
    val symbol: String,
    val name: String,
    val currency: String,
    val exchange: String,
    val exchangeName: String,
    val instrumentType: String,
    val timezone: String,
    val price: Double?,
    val previousClose: Double?,
    val dayHigh: Double?,
    val dayLow: Double?,
    val volume: Long?,
    val fiftyTwoWeekHigh: Double?,
    val fiftyTwoWeekLow: Double?,
    val marketTime: Long?,
    val firstTradeDate: Long?,
    val bars: List<Bar>,
    val dividends: List<Dividend> = emptyList(),
    val splits: List<Split> = emptyList(),
) {
    val change: Double? get() = if (price != null && previousClose != null) price - previousClose else null
    val changePct: Double? get() = if (price != null && previousClose != null && previousClose != 0.0) {
        (price - previousClose) / previousClose * 100
    } else {
        null
    }
}

/** A short quote for lists: price and today's change. */
data class Quote(
    val symbol: String,
    val name: String,
    val currency: String,
    val price: Double?,
    val previousClose: Double?,
    val spark: List<Double> = emptyList(),
    val marketTime: Long? = null,
) {
    val change: Double? get() = if (price != null && previousClose != null) price - previousClose else null
    val changePct: Double? get() = if (price != null && previousClose != null && previousClose != 0.0) {
        (price - previousClose) / previousClose * 100
    } else {
        null
    }

    companion object {
        fun of(chart: Chart) = Quote(
            symbol = chart.symbol,
            name = chart.name,
            currency = chart.currency,
            price = chart.price,
            previousClose = chart.previousClose,
            spark = chart.bars.map { it.close },
            marketTime = chart.marketTime,
        )
    }
}

data class SearchHit(
    val symbol: String,
    val name: String,
    val exchange: String,
    val exchangeName: String,
    val type: String,
    val sector: String? = null,
    val industry: String? = null,
)

data class NewsItem(val title: String, val publisher: String, val url: String, val time: Long?)

/** A labelled value from Yahoo's quote summary or the Google Finance page, already formatted. */
data class Stat(val label: String, val value: String)

/** Everything shown under "Details" for a stock. Each part is optional; sources fill what they can. */
data class Details(
    val stats: List<Stat> = emptyList(),
    val about: String? = null,
    val sector: String? = null,
    val industry: String? = null,
    val website: String? = null,
    val employees: String? = null,
    val headquarters: String? = null,
    val analystTarget: String? = null,
    val recommendation: String? = null,
    val source: String = "",
)

/** The chart ranges offered on the stock screen, with the bar size Yahoo serves for each. */
enum class ChartRange(val label: String, val range: String, val interval: String, val intraday: Boolean) {
    D1("1D", "1d", "5m", true),
    D5("5D", "5d", "15m", true),
    M1("1M", "1mo", "1d", false),
    M6("6M", "6mo", "1d", false),
    YTD("YTD", "ytd", "1d", false),
    Y1("1Y", "1y", "1d", false),
    Y5("5Y", "5y", "1wk", false),
    MAX("Max", "max", "1mo", false),
}
