package io.github.kkrampis.stockvault.analysis

import io.github.kkrampis.stockvault.model.Holding
import io.github.kkrampis.stockvault.model.Rebalance
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.pow

data class BacktestInput(
    val holdings: List<Holding>,
    /** Daily adjusted closes per symbol (dividends reinvested). */
    val prices: Map<String, List<Point>>,
    val amount: Double,
    val monthly: Double = 0.0,
    val start: LocalDate? = null,
    val end: LocalDate? = null,
    val rebalance: Rebalance = Rebalance.None,
)

data class HoldingResult(
    val symbol: String,
    val targetWeight: Double,
    val invested: Double,
    val finalValue: Double,
    val finalWeight: Double,
    val returnPct: Double,
    val cagrPct: Double?,
)

data class BacktestResult(
    val start: LocalDate,
    val end: LocalDate,
    val values: List<Point>,
    val invested: List<Point>,
    /** Growth of 1 unit with contributions taken out (time-weighted), for fair comparisons. */
    val index: List<Point>,
    val totalInvested: Double,
    val finalValue: Double,
    val profit: Double,
    /** Final value against the money put in. */
    val moneyReturnPct: Double,
    /** Time-weighted return over the whole period. */
    val twrPct: Double,
    val cagrPct: Double?,
    /** Money-weighted yearly return (IRR), which accounts for when contributions went in. */
    val irrPct: Double?,
    val volatilityPct: Double?,
    val sharpe: Double?,
    val sortino: Double?,
    val maxDrawdown: Drawdown,
    val years: List<Pair<Int, Double>>,
    val holdings: List<HoldingResult>,
    val rebalances: Int,
    val notes: List<String>,
) {
    val bestYear get() = years.maxByOrNull { it.second }
    val worstYear get() = years.minByOrNull { it.second }
}

class BacktestException(message: String) : Exception(message)

/**
 * Simulates buying the portfolio on the first day, optionally adding money on the first trading day
 * of each month and rebalancing back to the target weights, using adjusted closes so dividends are
 * reinvested. Fractional shares, no fees or taxes.
 */
object Backtest {

    fun run(input: BacktestInput): BacktestResult {
        val holdings = input.holdings.filter { it.weight > 0 }
        if (holdings.isEmpty()) throw BacktestException("Add at least one holding with a weight above 0 %.")
        if (input.amount <= 0 && input.monthly <= 0) throw BacktestException("Enter an amount to invest.")
        val notes = mutableListOf<String>()
        val total = holdings.sumOf { it.weight }
        if (abs(total - 100) > 0.01) notes += "Weights add up to ${"%.1f".format(total)} %, so they were scaled to 100 %."
        val weights = holdings.associate { it.symbol to it.weight / total }

        val series = weights.keys.associateWith { sym ->
            input.prices[sym].orEmpty().filter { it.value > 0 }.sortedBy { it.date }
        }
        series.filterValues { it.isEmpty() }.keys.takeIf { it.isNotEmpty() }?.let {
            throw BacktestException("No price history for ${it.joinToString()}.")
        }

        val dataStart = series.values.maxOf { it.first().date }
        val dataEnd = series.values.minOf { it.last().date }
        var start = input.start?.let { maxOf(it, dataStart) } ?: dataStart
        val end = input.end?.let { minOf(it, dataEnd) } ?: dataEnd
        if (input.start != null && dataStart > input.start) {
            val late = series.filterValues { it.first().date == dataStart }.keys.joinToString()
            notes += "Started on $dataStart, the first day with prices for $late."
        }
        if (!start.isBefore(end)) throw BacktestException("The holdings have no common history in that period.")

        // Trading days: every date any holding traded, from the start, with the last known price carried forward.
        val days = series.values.flatMap { s -> s.asSequence().map { it.date }.filter { it in start..end }.toList() }
            .toSortedSet().toList()
        val cursor = series.mapValues { 0 }.toMutableMap()
        val last = mutableMapOf<String, Double>()
        fun priceOn(sym: String, day: LocalDate): Double? {
            val s = series.getValue(sym)
            var i = cursor.getValue(sym)
            while (i < s.size && s[i].date <= day) {
                last[sym] = s[i].value
                i++
            }
            cursor[sym] = i
            return last[sym]
        }
        start = days.first()

        val shares = weights.keys.associateWith { 0.0 }.toMutableMap()
        val investedBy = weights.keys.associateWith { 0.0 }.toMutableMap()
        val values = ArrayList<Point>(days.size)
        val invested = ArrayList<Point>(days.size)
        val index = ArrayList<Point>(days.size)
        val flows = mutableListOf<Pair<LocalDate, Double>>()
        var totalIn = 0.0
        var idx = 1.0
        var prevValue = 0.0
        var rebalances = 0
        var monthsSinceStart = 0

        fun buy(money: Double, prices: Map<String, Double>) {
            weights.forEach { (sym, w) ->
                shares[sym] = shares.getValue(sym) + money * w / prices.getValue(sym)
                investedBy[sym] = investedBy.getValue(sym) + money * w
            }
        }

        for ((i, day) in days.withIndex()) {
            val prices = weights.keys.associateWith { priceOn(it, day)!! }
            var added = 0.0
            if (i == 0) {
                added = input.amount
                buy(added, prices)
            } else if (day.monthValue != days[i - 1].monthValue || day.year != days[i - 1].year) {
                monthsSinceStart++
                if (input.monthly > 0) {
                    added = input.monthly
                    buy(added, prices)
                }
                val every = input.rebalance.months
                if (every > 0 && monthsSinceStart % every == 0) {
                    val v = weights.keys.sumOf { shares.getValue(it) * prices.getValue(it) }
                    weights.forEach { (sym, w) -> shares[sym] = v * w / prices.getValue(sym) }
                    rebalances++
                }
            }
            totalIn += added
            if (added > 0) flows += day to -added
            val value = weights.keys.sumOf { shares.getValue(it) * prices.getValue(it) }
            if (i > 0 && prevValue > 0) idx *= (value - added) / prevValue
            prevValue = value
            values += Point(day, value)
            invested += Point(day, totalIn)
            index += Point(day, idx)
        }

        val finalValue = values.last().value
        flows += end to finalValue
        val lastPrices = weights.keys.associateWith { last.getValue(it) }
        val years = Stats.years(start, values.last().date)
        val holdingResults = weights.map { (sym, w) ->
            val v = shares.getValue(sym) * lastPrices.getValue(sym)
            val s = series.getValue(sym)
            val p0 = Stats.valueAt(s, start)?.value ?: s.first().value
            HoldingResult(
                symbol = sym,
                targetWeight = w * 100,
                invested = investedBy.getValue(sym),
                finalValue = v,
                finalWeight = if (finalValue > 0) v / finalValue * 100 else 0.0,
                returnPct = (lastPrices.getValue(sym) / p0 - 1) * 100,
                cagrPct = Stats.cagrPct(p0, lastPrices.getValue(sym), years),
            )
        }.sortedByDescending { it.finalValue }

        val idxValues = index.map { it.value }
        return BacktestResult(
            start = start,
            end = values.last().date,
            values = values,
            invested = invested,
            index = index,
            totalInvested = totalIn,
            finalValue = finalValue,
            profit = finalValue - totalIn,
            moneyReturnPct = if (totalIn > 0) (finalValue / totalIn - 1) * 100 else 0.0,
            twrPct = (idx - 1) * 100,
            cagrPct = Stats.cagrPct(1.0, idx, years),
            irrPct = irr(flows)?.let { it * 100 },
            volatilityPct = Stats.volatilityPct(idxValues),
            sharpe = Stats.sharpe(idxValues),
            sortino = Stats.sortino(idxValues),
            maxDrawdown = Stats.maxDrawdown(index),
            years = Stats.yearlyReturns(index),
            holdings = holdingResults,
            rebalances = rebalances,
            notes = notes,
        )
    }

    /**
     * Yearly internal rate of return of dated cash flows (money in is negative, the final value
     * positive), found by bisection. Null when there is no sign change.
     */
    fun irr(flows: List<Pair<LocalDate, Double>>): Double? {
        if (flows.size < 2) return null
        val t0 = flows.first().first
        fun npv(rate: Double) = flows.sumOf { (d, cf) -> cf / (1 + rate).pow(Stats.years(t0, d)) }
        var lo = -0.99
        var hi = 10.0
        var fLo = npv(lo)
        val fHi = npv(hi)
        if (fLo.isNaN() || fHi.isNaN() || fLo * fHi > 0) return null
        repeat(200) {
            val mid = (lo + hi) / 2
            val f = npv(mid)
            if (abs(f) < 1e-7) return mid
            if (f * fLo < 0) hi = mid else {
                lo = mid
                fLo = f
            }
        }
        return (lo + hi) / 2
    }
}
