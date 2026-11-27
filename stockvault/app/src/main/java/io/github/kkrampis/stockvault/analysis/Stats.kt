package io.github.kkrampis.stockvault.analysis

import io.github.kkrampis.stockvault.model.Bar
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/** A dated value, e.g. a portfolio's worth on one day. */
data class Point(val date: LocalDate, val value: Double)

data class Drawdown(val depthPct: Double, val peak: LocalDate?, val trough: LocalDate?, val recovered: LocalDate?)

data class PeriodReturn(val label: String, val pct: Double?, val annualizedPct: Double? = null)

/** Calculations shared by the stock analysis and the portfolio backtests. All work on daily values. */
object Stats {
    const val TRADING_DAYS = 252.0

    fun dailyReturns(values: List<Double>): List<Double> =
        values.zipWithNext { a, b -> if (a > 0) b / a - 1 else 0.0 }

    /** Annualised standard deviation of daily returns, in percent. */
    fun volatilityPct(values: List<Double>): Double? {
        val r = dailyReturns(values)
        if (r.size < 2) return null
        val mean = r.average()
        val variance = r.sumOf { (it - mean).pow(2) } / (r.size - 1)
        return sqrt(variance) * sqrt(TRADING_DAYS) * 100
    }

    /** Compound annual growth rate in percent between two values [years] apart. */
    fun cagrPct(start: Double, end: Double, years: Double): Double? {
        if (start <= 0 || end <= 0 || years <= 0) return null
        return ((end / start).pow(1 / years) - 1) * 100
    }

    fun years(from: LocalDate, to: LocalDate): Double = (to.toEpochDay() - from.toEpochDay()) / 365.25

    /** The deepest fall from a high, with when it started, bottomed and (if it did) recovered. */
    fun maxDrawdown(points: List<Point>): Drawdown {
        if (points.isEmpty()) return Drawdown(0.0, null, null, null)
        var peak = points.first()
        var worst = 0.0
        var worstPeak: Point? = null
        var worstTrough: Point? = null
        for (p in points) {
            if (p.value > peak.value) peak = p
            val dd = if (peak.value > 0) p.value / peak.value - 1 else 0.0
            if (dd < worst) {
                worst = dd
                worstPeak = peak
                worstTrough = p
            }
        }
        val recovered = worstTrough?.let { t ->
            val target = worstPeak!!.value
            points.firstOrNull { it.date > t.date && it.value >= target }?.date
        }
        return Drawdown(worst * 100, worstPeak?.date, worstTrough?.date, recovered)
    }

    /** Sharpe ratio from daily returns, with [riskFreePct] a yearly rate. */
    fun sharpe(values: List<Double>, riskFreePct: Double = 0.0): Double? {
        val r = dailyReturns(values)
        if (r.size < 20) return null
        val rf = riskFreePct / 100 / TRADING_DAYS
        val excess = r.map { it - rf }
        val mean = excess.average()
        val sd = sqrt(excess.sumOf { (it - mean).pow(2) } / (excess.size - 1))
        if (sd == 0.0) return null
        return mean / sd * sqrt(TRADING_DAYS)
    }

    /** Sortino ratio: like Sharpe, but only falls count as risk. */
    fun sortino(values: List<Double>): Double? {
        val r = dailyReturns(values)
        if (r.size < 20) return null
        val down = r.filter { it < 0 }
        if (down.isEmpty()) return null
        val dd = sqrt(down.sumOf { it * it } / r.size)
        if (dd == 0.0) return null
        return r.average() / dd * sqrt(TRADING_DAYS)
    }

    fun sma(values: List<Double>, n: Int): List<Double?> {
        val out = ArrayList<Double?>(values.size)
        var sum = 0.0
        for (i in values.indices) {
            sum += values[i]
            if (i >= n) sum -= values[i - n]
            out += if (i >= n - 1) sum / n else null
        }
        return out
    }

    /** Wilder's relative strength index. */
    fun rsi(values: List<Double>, n: Int = 14): Double? {
        if (values.size <= n) return null
        var gain = 0.0
        var loss = 0.0
        for (i in 1..n) {
            val d = values[i] - values[i - 1]
            if (d > 0) gain += d else loss -= d
        }
        gain /= n
        loss /= n
        for (i in n + 1 until values.size) {
            val d = values[i] - values[i - 1]
            gain = (gain * (n - 1) + (if (d > 0) d else 0.0)) / n
            loss = (loss * (n - 1) + (if (d < 0) -d else 0.0)) / n
        }
        if (loss == 0.0) return 100.0
        val rs = gain / loss
        return 100 - 100 / (1 + rs)
    }

    /**
     * Beta and correlation of [asset] against [market], from daily returns on the days both traded.
     * Returns null when they share fewer than 60 days.
     */
    fun betaAndCorrelation(asset: List<Point>, market: List<Point>): Pair<Double, Double>? {
        val m = market.associate { it.date to it.value }
        val pairs = asset.filter { it.date in m }.map { it.value to m.getValue(it.date) }
        if (pairs.size < 61) return null
        val ra = pairs.zipWithNext { a, b -> b.first / a.first - 1 }
        val rm = pairs.zipWithNext { a, b -> b.second / a.second - 1 }
        val ma = ra.average()
        val mm = rm.average()
        var cov = 0.0
        var va = 0.0
        var vm = 0.0
        for (i in ra.indices) {
            cov += (ra[i] - ma) * (rm[i] - mm)
            va += (ra[i] - ma).pow(2)
            vm += (rm[i] - mm).pow(2)
        }
        if (vm == 0.0 || va == 0.0) return null
        return cov / vm to cov / sqrt(va * vm)
    }

    /** Value on the last day on or before [date], or the first value when [date] is earlier than all data. */
    fun valueAt(points: List<Point>, date: LocalDate): Point? {
        if (points.isEmpty()) return null
        var lo = 0
        var hi = points.size - 1
        if (date < points[0].date) return null
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (points[mid].date <= date) lo = mid else hi = mid - 1
        }
        return points[lo]
    }

    /** Total return over the usual look-back periods, ending at the last point. Long periods are also annualised. */
    fun periodReturns(points: List<Point>): List<PeriodReturn> {
        if (points.size < 2) return emptyList()
        val last = points.last()
        fun back(label: String, from: LocalDate, annualize: Boolean = false): PeriodReturn {
            val start = valueAt(points, from)
            if (start == null || start.date == last.date) return PeriodReturn(label, null)
            val pct = (last.value / start.value - 1) * 100
            val ann = if (annualize) cagrPct(start.value, last.value, years(start.date, last.date)) else null
            return PeriodReturn(label, pct, ann)
        }
        val d = last.date
        return listOf(
            back("1 week", d.minusWeeks(1)),
            back("1 month", d.minusMonths(1)),
            back("3 months", d.minusMonths(3)),
            back("6 months", d.minusMonths(6)),
            back("Year to date", LocalDate.of(d.year - 1, 12, 31)),
            back("1 year", d.minusYears(1)),
            back("3 years", d.minusYears(3), true),
            back("5 years", d.minusYears(5), true),
            back("10 years", d.minusYears(10), true),
            PeriodReturn(
                "Since ${points.first().date.year}",
                (last.value / points.first().value - 1) * 100,
                cagrPct(points.first().value, last.value, years(points.first().date, d)),
            ),
        ).filter { it.pct != null }
    }

    /** Return per calendar year (the first and last year are partial). */
    fun yearlyReturns(points: List<Point>): List<Pair<Int, Double>> {
        if (points.size < 2) return emptyList()
        val out = mutableListOf<Pair<Int, Double>>()
        var prevEnd = points.first().value
        points.groupBy { it.date.year }.toSortedMap().forEach { (year, pts) ->
            val end = pts.last().value
            if (prevEnd > 0) out += year to (end / prevEnd - 1) * 100
            prevEnd = end
        }
        return out
    }

    fun monthlyReturns(points: List<Point>): List<Pair<LocalDate, Double>> {
        if (points.size < 2) return emptyList()
        val out = mutableListOf<Pair<LocalDate, Double>>()
        var prevEnd = points.first().value
        points.groupBy { it.date.withDayOfMonth(1) }.toSortedMap().forEach { (month, pts) ->
            val end = pts.last().value
            if (prevEnd > 0) out += month to (end / prevEnd - 1) * 100
            prevEnd = end
        }
        return out
    }

    fun points(bars: List<Bar>, adjusted: Boolean = true): List<Point> =
        bars.map { Point(it.date, if (adjusted) it.adjClose else it.close) }
}

/** The numbers on a stock's Analysis tab, computed from its daily history. */
data class StockAnalysis(
    val periods: List<PeriodReturn>,
    val years: List<Pair<Int, Double>>,
    val volatility1y: Double?,
    val volatilityAll: Double?,
    val maxDrawdown: Drawdown,
    val drawdown1y: Drawdown,
    val sharpe1y: Double?,
    val sortino1y: Double?,
    val rsi14: Double?,
    val sma50: Double?,
    val sma200: Double?,
    val last: Double,
    val high52: Double?,
    val low52: Double?,
    val bestDay: Point?,
    val worstDay: Point?,
    val beta: Double?,
    val correlation: Double?,
    val benchmark: String?,
    val dividends12m: Double?,
    val firstDate: LocalDate,
    val lastDate: LocalDate,
) {
    val trend: String
        get() = when {
            sma50 == null || sma200 == null -> "Not enough history for a trend"
            last > sma50 && sma50 > sma200 -> "Up-trend: price above the 50-day average, which is above the 200-day"
            last < sma50 && sma50 < sma200 -> "Down-trend: price below the 50-day average, which is below the 200-day"
            sma50 > sma200 -> "Mixed: 50-day average above the 200-day, price below the 50-day"
            else -> "Mixed: 50-day average below the 200-day, price above the 50-day"
        }

    companion object {
        /**
         * [bars] are daily bars (any length); [market] is the benchmark's daily adjusted series for beta.
         * Returns null with fewer than two bars.
         */
        fun of(
            bars: List<Bar>,
            market: List<Point>? = null,
            benchmark: String? = null,
            dividendsLastYear: Double? = null,
        ): StockAnalysis? {
            if (bars.size < 2) return null
            val adj = Stats.points(bars)
            val closes = bars.map { it.close }
            val lastDate = adj.last().date
            val yearAgo = lastDate.minusYears(1)
            val lastYear = adj.filter { it.date >= yearAgo }
            val lastYearBars = bars.filter { it.date >= yearAgo }
            val daily = adj.zipWithNext { a, b -> Point(b.date, (b.value / a.value - 1) * 100) }
                .filter { abs(it.value) < 400 } // drops data glitches
            val beta = market?.let { Stats.betaAndCorrelation(adj.filter { it.date >= lastDate.minusYears(3) }, it) }
            return StockAnalysis(
                periods = Stats.periodReturns(adj),
                years = Stats.yearlyReturns(adj),
                volatility1y = Stats.volatilityPct(lastYear.map { it.value }),
                volatilityAll = Stats.volatilityPct(adj.map { it.value }),
                maxDrawdown = Stats.maxDrawdown(adj),
                drawdown1y = Stats.maxDrawdown(lastYear),
                sharpe1y = Stats.sharpe(lastYear.map { it.value }),
                sortino1y = Stats.sortino(lastYear.map { it.value }),
                rsi14 = Stats.rsi(closes),
                sma50 = Stats.sma(closes, 50).lastOrNull(),
                sma200 = Stats.sma(closes, 200).lastOrNull(),
                last = closes.last(),
                high52 = lastYearBars.mapNotNull { it.high ?: it.close }.maxOrNull(),
                low52 = lastYearBars.mapNotNull { it.low ?: it.close }.minOrNull(),
                bestDay = daily.maxByOrNull { it.value },
                worstDay = daily.minByOrNull { it.value },
                beta = beta?.first,
                correlation = beta?.second,
                benchmark = if (beta != null) benchmark else null,
                dividends12m = dividendsLastYear,
                firstDate = adj.first().date,
                lastDate = lastDate,
            )
        }
    }
}
