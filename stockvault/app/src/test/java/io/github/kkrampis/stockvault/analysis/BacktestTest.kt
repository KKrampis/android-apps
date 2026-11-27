package io.github.kkrampis.stockvault.analysis

import io.github.kkrampis.stockvault.model.Holding
import io.github.kkrampis.stockvault.model.Rebalance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BacktestTest {
    private val start = LocalDate.of(2020, 1, 1)

    /** A series that grows at a steady daily rate on weekdays. */
    private fun steady(days: Int, daily: Double, from: LocalDate = start, startValue: Double = 100.0): List<Point> {
        val out = mutableListOf<Point>()
        var d = from
        var v = startValue
        while (out.size < days) {
            if (d.dayOfWeek.value <= 5) {
                out += Point(d, v)
                v *= 1 + daily
            }
            d = d.plusDays(1)
        }
        return out
    }

    @Test
    fun lumpSumInOneStockMatchesItsReturn() {
        val s = steady(500, 0.001)
        val r = Backtest.run(BacktestInput(listOf(Holding("AAA", weight = 100.0)), mapOf("AAA" to s), amount = 1000.0))
        val expected = 1000.0 * s.last().value / s.first().value
        assertEquals(expected, r.finalValue, 1e-6)
        assertEquals(1000.0, r.totalInvested, 1e-9)
        assertEquals((expected / 1000 - 1) * 100, r.twrPct, 1e-6)
        assertEquals(0.0, r.maxDrawdown.depthPct, 1e-9)
    }

    @Test
    fun monthlyContributionsAreCountedButDoNotInflateTimeWeightedReturn() {
        val s = steady(300, 0.0005)
        val r = Backtest.run(
            BacktestInput(listOf(Holding("AAA", weight = 100.0)), mapOf("AAA" to s), amount = 1000.0, monthly = 100.0),
        )
        val months = s.map { it.date.withDayOfMonth(1) }.distinct().size - 1
        assertEquals(1000.0 + 100.0 * months, r.totalInvested, 1e-9)
        assertEquals((s.last().value / s.first().value - 1) * 100, r.twrPct, 1e-6)
        assertNotNull(r.irrPct)
        assertTrue(r.finalValue > r.totalInvested)
    }

    @Test
    fun startMovesToFirstDayAllHoldingsHaveData() {
        val a = steady(400, 0.001)
        val b = steady(200, 0.002, from = start.plusDays(200))
        val r = Backtest.run(
            BacktestInput(
                listOf(Holding("A", weight = 50.0), Holding("B", weight = 50.0)),
                mapOf("A" to a, "B" to b), amount = 1000.0, start = start,
            ),
        )
        assertEquals(b.first().date, r.start)
        assertTrue(r.notes.any { it.contains("B") })
    }

    @Test
    fun rebalancingKeepsWeightsNearTarget() {
        val up = steady(520, 0.003)
        val flat = steady(520, 0.0)
        val holdings = listOf(Holding("UP", weight = 50.0), Holding("FLAT", weight = 50.0))
        val prices = mapOf("UP" to up, "FLAT" to flat)
        val hold = Backtest.run(BacktestInput(holdings, prices, amount = 1000.0))
        val monthly = Backtest.run(BacktestInput(holdings, prices, amount = 1000.0, rebalance = Rebalance.Monthly))
        val upHold = hold.holdings.first { it.symbol == "UP" }.finalWeight
        val upRebal = monthly.holdings.first { it.symbol == "UP" }.finalWeight
        assertTrue(upHold > 70)
        assertTrue(upRebal < 60)
        assertTrue(monthly.rebalances > 20)
    }

    @Test
    fun weightsAreScaledTo100() {
        val s = steady(100, 0.001)
        val r = Backtest.run(BacktestInput(listOf(Holding("A", weight = 30.0), Holding("B", weight = 30.0)), mapOf("A" to s, "B" to s), 1000.0))
        assertEquals(50.0, r.holdings[0].targetWeight, 1e-9)
        assertTrue(r.notes.isNotEmpty())
    }

    @Test
    fun drawdownIsFound() {
        val pts = listOf(100.0, 120.0, 90.0, 60.0, 130.0).mapIndexed { i, v -> Point(start.plusDays(i.toLong()), v) }
        val dd = Stats.maxDrawdown(pts)
        assertEquals(-50.0, dd.depthPct, 1e-9)
        assertEquals(start.plusDays(1), dd.peak)
        assertEquals(start.plusDays(3), dd.trough)
        assertEquals(start.plusDays(4), dd.recovered)
    }

    @Test
    fun irrOfDoublingInOneYearIs100Percent() {
        val irr = Backtest.irr(listOf(start to -100.0, start.plusDays(365) to 200.0))!!
        assertEquals(1.0, irr, 0.01)
    }

    @Test
    fun rsiOfOnlyGainsIs100() {
        assertEquals(100.0, Stats.rsi((1..30).map { it.toDouble() })!!, 1e-9)
    }
}
