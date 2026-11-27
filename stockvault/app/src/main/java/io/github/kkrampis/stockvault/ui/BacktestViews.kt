package io.github.kkrampis.stockvault.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.kkrampis.stockvault.analysis.BacktestResult
import io.github.kkrampis.stockvault.analysis.Point
import io.github.kkrampis.stockvault.model.Format
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFmt = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

fun formatDay(d: LocalDate?): String = d?.format(dayFmt) ?: "—"

fun formatEpochDay(x: Long): String = formatDay(LocalDate.ofEpochDay(x))

fun List<Point>.toSeries(label: String, color: Color, dashed: Boolean = false, fill: Boolean = false) =
    Series(label, map { it.date.toEpochDay() }, map { it.value }, color, dashed = dashed, fill = fill)

/** Look-back presets for the start date of a backtest, as ISO dates (null = as far back as possible). */
fun startPresets(today: LocalDate = LocalDate.now()): List<Pair<String, String?>> = listOf(
    "1Y" to today.minusYears(1).toString(),
    "3Y" to today.minusYears(3).toString(),
    "5Y" to today.minusYears(5).toString(),
    "10Y" to today.minusYears(10).toString(),
    "20Y" to today.minusYears(20).toString(),
    "Max" to null,
)

@Composable
fun StartPresetChips(selected: String?, onPick: (String?) -> Unit) {
    val presets = startPresets()
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        presets.forEach { (label, date) ->
            FilterChip(selected = selected == date, onClick = { onPick(date) }, label = { Text(label) })
        }
    }
}

/** The summary, chart and tables for one backtest, with the benchmark alongside when there is one. */
@Composable
fun BacktestResultView(result: BacktestResult, benchmark: BacktestResult?, benchmarkSymbol: String, title: String) {
    val profitColor = Palette.change(result.profit)
    Section(title, note = "${formatDay(result.start)} – ${formatDay(result.end)} · " +
        "${Format.num(io.github.kkrampis.stockvault.analysis.Stats.years(result.start, result.end), 1)} years") {
        MetricRow(
            Triple("Invested", Format.money(result.totalInvested), Color.Unspecified),
            Triple("Worth now", Format.money(result.finalValue), Color.Unspecified),
            Triple("Gain", Format.money(result.profit), profitColor),
        )
        MetricRow(
            Triple("Return on money", Format.pct(result.moneyReturnPct, digits = 1), Palette.change(result.moneyReturnPct)),
            Triple("Per year (CAGR)", Format.pct(result.cagrPct, digits = 1), Palette.change(result.cagrPct)),
            Triple("Max drawdown", Format.pct(result.maxDrawdown.depthPct, signed = false, digits = 1), Palette.down),
        )
        if (benchmark != null) {
            val diff = (result.cagrPct ?: 0.0) - (benchmark.cagrPct ?: 0.0)
            Text(
                "$benchmarkSymbol over the same days: ${Format.money(benchmark.finalValue)} " +
                    "(${Format.pct(benchmark.cagrPct, digits = 1)} a year). " +
                    if (diff >= 0) "This beat it by ${Format.num(diff, 1)} points a year." else "This trailed it by ${Format.num(-diff, 1)} points a year.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Column(Modifier.padding(top = 12.dp)) {
            LineChart(
                series = buildList {
                    add(result.values.toSeries("Value", Palette.series[0], fill = true))
                    benchmark?.let { add(it.values.toSeries(benchmarkSymbol, Palette.series[1])) }
                    if (result.totalInvested > result.invested.first().value) {
                        add(result.invested.toSeries("Invested", MaterialTheme.colorScheme.onSurfaceVariant, dashed = true))
                    }
                },
                formatX = ::formatEpochDay,
                formatY = { Format.money(it) },
                showLegend = true,
            )
        }
        result.notes.forEach {
            Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
    Section("Risk and return", note = "Time-weighted, so monthly additions don't count as gains. Dividends reinvested, no fees or taxes.") {
        StatRow("Total return (time-weighted)", Format.pct(result.twrPct, digits = 1), Palette.change(result.twrPct))
        StatRow("Annual return (CAGR)", Format.pct(result.cagrPct), Palette.change(result.cagrPct))
        if (result.irrPct != null && result.totalInvested > result.invested.first().value) {
            StatRow("Annual return on your money (IRR)", Format.pct(result.irrPct), Palette.change(result.irrPct))
        }
        StatRow("Volatility (yearly)", Format.pct(result.volatilityPct, signed = false))
        StatRow("Sharpe ratio", Format.num(result.sharpe))
        StatRow("Sortino ratio", Format.num(result.sortino))
        StatRow(
            "Worst fall",
            "${Format.pct(result.maxDrawdown.depthPct, signed = false, digits = 1)} (${formatDay(result.maxDrawdown.peak)} → ${formatDay(result.maxDrawdown.trough)})",
        )
        StatRow("Recovered", result.maxDrawdown.recovered?.let(::formatDay) ?: if (result.maxDrawdown.depthPct < 0) "Not yet" else "—")
        result.bestYear?.let { StatRow("Best year", "${it.first}: ${Format.pct(it.second, digits = 1)}", Palette.up) }
        result.worstYear?.let { StatRow("Worst year", "${it.first}: ${Format.pct(it.second, digits = 1)}", Palette.down) }
        if (result.rebalances > 0) StatRow("Rebalances", result.rebalances.toString())
        if (benchmark != null) {
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            StatRow("$benchmarkSymbol annual return", Format.pct(benchmark.cagrPct), Palette.change(benchmark.cagrPct))
            StatRow("$benchmarkSymbol volatility", Format.pct(benchmark.volatilityPct, signed = false))
            StatRow("$benchmarkSymbol worst fall", Format.pct(benchmark.maxDrawdown.depthPct, signed = false, digits = 1))
        }
    }
    if (result.holdings.size > 1) {
        Section("Holdings", note = "Where the money ended up. Return is each stock's own return over the period.") {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                listOf("Symbol" to 1f, "Target" to 0.8f, "Now" to 0.8f, "Value" to 1.1f, "Return" to 1f).forEach { (h, w) ->
                    Text(h, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(w), textAlign = if (h == "Symbol") TextAlign.Start else TextAlign.End)
                }
            }
            result.holdings.forEach { h ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(h.symbol, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(Format.pct(h.targetWeight, signed = false, digits = 0), modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                    Text(Format.pct(h.finalWeight, signed = false, digits = 0), modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                    Text(Format.money(h.finalValue), modifier = Modifier.weight(1.1f), textAlign = TextAlign.End)
                    Text(Format.pct(h.returnPct, digits = 0), color = Palette.change(h.returnPct), modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                }
            }
        }
    }
    if (result.years.isNotEmpty()) {
        Section("Year by year", note = "First and last years are partial.") {
            ReturnBars(result.years.takeLast(30).map { it.first.toString() to it.second })
        }
    }
}

/** A short text version of a backtest, for sharing or asking an AI about it. */
fun backtestSummary(name: String, holdings: String, r: BacktestResult, bench: BacktestResult?, benchSymbol: String): String = buildString {
    appendLine("Portfolio: $name")
    appendLine("Holdings: $holdings")
    appendLine("Period: ${r.start} to ${r.end}")
    appendLine("Invested ${Format.money(r.totalInvested)}, worth ${Format.money(r.finalValue)} (${Format.pct(r.moneyReturnPct, digits = 1)})")
    appendLine("CAGR ${Format.pct(r.cagrPct)}, volatility ${Format.pct(r.volatilityPct, signed = false)}, max drawdown ${Format.pct(r.maxDrawdown.depthPct, signed = false)}, Sharpe ${Format.num(r.sharpe)}")
    if (bench != null) appendLine("$benchSymbol over the same period: CAGR ${Format.pct(bench.cagrPct)}, max drawdown ${Format.pct(bench.maxDrawdown.depthPct, signed = false)}")
    if (r.years.isNotEmpty()) appendLine("By year: " + r.years.joinToString { "${it.first} ${Format.pct(it.second, digits = 1)}" })
}

@Composable
fun SmallLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}
