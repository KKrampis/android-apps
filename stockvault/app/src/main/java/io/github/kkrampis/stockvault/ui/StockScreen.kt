package io.github.kkrampis.stockvault.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kkrampis.stockvault.ai.ClaudeApp
import io.github.kkrampis.stockvault.ai.Prompts
import io.github.kkrampis.stockvault.analysis.Backtest
import io.github.kkrampis.stockvault.analysis.BacktestInput
import io.github.kkrampis.stockvault.analysis.BacktestResult
import io.github.kkrampis.stockvault.analysis.Stats
import io.github.kkrampis.stockvault.analysis.StockAnalysis
import io.github.kkrampis.stockvault.data.DetailsBundle
import io.github.kkrampis.stockvault.data.YahooParse
import io.github.kkrampis.stockvault.model.Chart
import io.github.kkrampis.stockvault.model.ChartRange
import io.github.kkrampis.stockvault.model.Format
import io.github.kkrampis.stockvault.model.Holding
import io.github.kkrampis.stockvault.model.Library
import io.github.kkrampis.stockvault.model.NewsItem
import io.github.kkrampis.stockvault.model.Portfolio
import io.github.kkrampis.stockvault.model.Quote
import io.github.kkrampis.stockvault.model.Rebalance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Something that loads: still loading, loaded, or failed with a message. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

private val tabs = listOf("Overview", "Analysis", "What if", "Similar", "News")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(vm: AppViewModel, symbol: String, initialName: String, library: Library, settings: SettingsUiState) {
    val context = LocalContext.current
    var range by rememberSaveable { mutableStateOf(ChartRange.D1) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSma by rememberSaveable { mutableStateOf(false) }
    var compareWith by rememberSaveable { mutableStateOf<String?>(null) }
    var pickCompare by remember { mutableStateOf(false) }
    var saveDialog by remember { mutableStateOf(false) }
    var portfolioDialog by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    // Today's quote (1-day chart) drives the header; the selected range drives the chart.
    val today by produceState<Load<Chart>>(Load.Loading, symbol, reload) {
        value = runCatching { vm.repo.chart(symbol, ChartRange.D1, 30_000) }
            .fold({ Load.Ok(it) }, { Load.Failed(it.message ?: "Could not load $symbol") })
    }
    val chart by produceState<Load<Chart>>(Load.Loading, symbol, range, reload) {
        value = Load.Loading
        value = runCatching { vm.repo.chart(symbol, range) }
            .fold({ Load.Ok(it) }, { Load.Failed(it.message ?: "Could not load the chart") })
    }
    val history by produceState<Load<Chart>>(Load.Loading, symbol) {
        value = runCatching { vm.repo.history(symbol) }
            .fold({ Load.Ok(it) }, { Load.Failed(it.message ?: "Could not load the price history") })
    }
    val compare by produceState<Chart?>(null, compareWith, range) {
        value = compareWith?.let { s -> runCatching { vm.repo.chart(s, range) }.getOrNull() }
    }
    val head = (today as? Load.Ok)?.value
    val name = head?.name ?: initialName.ifBlank { symbol }
    val currency = head?.currency.orEmpty()
    val savedHere = library.saved.any { it.symbol.equals(symbol, true) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(symbol, fontWeight = FontWeight.SemiBold)
                    Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            navigationIcon = { IconButton(onClick = vm::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = { saveDialog = true }) {
                    Icon(if (savedHere) Icons.Default.BookmarkAdded else Icons.Default.BookmarkAdd, "Save to folder")
                }
                IconButton(onClick = { portfolioDialog = true }) { Icon(Icons.Default.PieChart, "Add to portfolio") }
                IconButton(onClick = {
                    val q = head?.let { Quote.of(it) }
                    shareText(context, "$symbol – $name", "$name ($symbol): ${Format.price(q?.price, currency)} " +
                        "${Format.pct(q?.changePct)} today\nhttps://finance.yahoo.com/quote/$symbol")
                }) { Icon(Icons.Default.Share, "Share") }
            },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // Price header
            Column(Modifier.padding(horizontal = 16.dp)) {
                when (val t = today) {
                    is Load.Failed -> Column {
                        Text(t.message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { reload++ }) { Text("Try again") }
                    }
                    else -> {
                        val c = (t as? Load.Ok)?.value
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(Format.price(c?.price, currency), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.size(10.dp))
                            ChangeText(c?.change, c?.changePct, MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            listOfNotNull(c?.exchangeName, currency.ifBlank { null }, c?.marketTime?.let { "as of " + formatEpoch(it) })
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Chart
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ChartRange.entries.forEach { r -> FilterChip(selected = range == r, onClick = { range = r }, label = { Text(r.label) }) }
            }
            Box(Modifier.padding(horizontal = 16.dp)) {
                when (val c = chart) {
                    Load.Loading -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    is Load.Failed -> Text(c.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.height(80.dp))
                    is Load.Ok -> PriceChart(c.value, range, compare, compareWith, showSma, (history as? Load.Ok)?.value)
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!range.intraday) {
                    FilterChip(selected = showSma, onClick = { showSma = !showSma }, label = { Text("50/200-day averages") })
                }
                FilterChip(
                    selected = compareWith != null,
                    onClick = { if (compareWith != null) compareWith = null else pickCompare = true },
                    label = { Text(compareWith?.let { "vs $it" } ?: "Compare…") },
                )
                if (compareWith == null && symbol != settings.benchmark) {
                    FilterChip(selected = false, onClick = { compareWith = settings.benchmark }, label = { Text("vs ${settings.benchmark}") })
                }
            }

            PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp, modifier = Modifier.padding(top = 8.dp)) {
                tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
            when (tab) {
                0 -> OverviewTab(vm, symbol, head)
                1 -> AnalysisTab(vm, symbol, name, history, settings, head)
                2 -> WhatIfTab(symbol, history)
                3 -> SimilarTab(vm, symbol, name)
                else -> NewsTab(vm, symbol)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (saveDialog) {
        FolderPickerDialog(
            library = library,
            title = "Save $symbol to…",
            onDismiss = { saveDialog = false },
            onNewFolder = { n, parent -> vm.addFolder(n, parent) },
        ) { folder -> vm.saveStock(symbol, name, folder, head?.price, currency) }
    }
    if (portfolioDialog) {
        PortfolioPickerDialog(
            library = library,
            onDismiss = { portfolioDialog = false },
            onNew = {
                vm.push(Route.PortfolioEdit(null, Portfolio(name = "$symbol portfolio",
                    holdings = listOf(Holding(symbol, name, 100.0)), plan = vm.newPortfolioPlan())))
            },
        ) { id -> vm.addToPortfolio(id, symbol, name) }
    }
    if (pickCompare) {
        SymbolSearchDialog(vm.repo, title = "Compare $symbol with", onDismiss = { pickCompare = false }) { compareWith = it.symbol }
    }
}

@Composable
private fun PriceChart(c: Chart, range: ChartRange, compare: Chart?, compareSymbol: String?, showSma: Boolean, history: Chart?) {
    val zone = runCatching { ZoneId.of(c.timezone) }.getOrDefault(ZoneId.systemDefault())
    val fmt = remember(range) {
        DateTimeFormatter.ofPattern(if (range.intraday) "MMM d, HH:mm" else "MMM d, yyyy", Locale.US)
    }
    val formatX: (Long) -> String = { Instant.ofEpochSecond(it).atZone(zone).format(fmt) }
    val bars = c.bars
    if (bars.isEmpty()) {
        Text("No prices for this range", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.height(80.dp))
        return
    }
    val xs = bars.map { it.time }
    if (compare != null && compareSymbol != null && compare.bars.isNotEmpty()) {
        // Both lines as % change from the start of the range.
        val start = xs.first()
        val other = compare.bars.filter { it.time >= start - 86_400 * 4 }
        val b0 = bars.first().close
        val o0 = other.firstOrNull()?.close ?: 1.0
        LineChart(
            series = listOf(
                Series(c.symbol, xs, bars.map { (it.close / b0 - 1) * 100 }, Palette.series[0]),
                Series(compareSymbol, other.map { it.time }, other.map { (it.close / o0 - 1) * 100 }, Palette.series[1]),
            ),
            formatX = formatX,
            formatY = { Format.pct(it, digits = 1) },
            baseline = 0.0,
            height = 240.dp,
        )
        return
    }
    val series = mutableListOf(
        Series("Price", xs, bars.map { it.close }, if (bars.last().close >= (c.previousClose ?: bars.first().close)) Palette.up else Palette.down, fill = true),
    )
    if (showSma && !range.intraday && history != null && history.bars.size > 50) {
        val closes = history.bars.map { it.close }
        val hx = history.bars.map { it.time }
        val from = xs.first()
        fun sma(n: Int, color: androidx.compose.ui.graphics.Color, label: String) {
            val s = Stats.sma(closes, n)
            val idx = hx.indices.filter { hx[it] >= from }
            if (idx.isNotEmpty()) series += Series(label, idx.map { hx[it] }, idx.map { s[it] }, color, width = 1.5f)
        }
        sma(50, Palette.series[1], "50-day")
        sma(200, Palette.series[2], "200-day")
    }
    LineChart(
        series = series,
        formatX = formatX,
        formatY = { Format.price(it, c.currency) },
        baseline = if (range == ChartRange.D1) c.previousClose else null,
        height = 240.dp,
        showLegend = series.size > 1,
    )
}

@Composable
private fun OverviewTab(vm: AppViewModel, symbol: String, head: Chart?) {
    val context = LocalContext.current
    val exchange = head?.exchange.orEmpty()
    val details by produceState<Load<DetailsBundle>>(Load.Loading, symbol, exchange) {
        if (head == null) return@produceState
        value = runCatching { vm.repo.details(symbol, exchange) }.fold({ Load.Ok(it) }, { Load.Failed(it.message ?: "Failed") })
    }
    var aboutOpen by remember { mutableStateOf(false) }
    when (val d = details) {
        Load.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is Load.Failed -> Text(d.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        is Load.Ok -> {
            val b = d.value
            val y = b.yahoo
            Section("Key stats", note = if (y != null) "From Yahoo Finance" else "Basic figures; the full summary was not available") {
                StatTable(if (y != null && y.stats.isNotEmpty()) y.stats else head?.let { YahooParse.basicStats(it) }.orEmpty())
            }
            val profile = listOfNotNull(
                y?.sector?.let { "Sector" to it },
                y?.industry?.let { "Industry" to it },
                (y?.employees ?: b.google?.employees)?.let { "Employees" to it },
                (y?.headquarters ?: b.google?.headquarters)?.let { "Headquarters" to it },
                y?.recommendation?.let { "Analyst view" to it },
                y?.analystTarget?.let { "Price target (mean)" to it },
            )
            val about = y?.about ?: b.google?.about
            if (profile.isNotEmpty() || about != null) {
                Section("Company") {
                    profile.forEach { (l, v) -> StatRow(l, v) }
                    (y?.website ?: b.google?.website)?.let { site ->
                        TextButton(onClick = { openUrl(context, if (site.startsWith("http")) site else "https://$site") }) {
                            Text(hostOf(if (site.startsWith("http")) site else "https://$site").ifBlank { site })
                        }
                    }
                    if (about != null) {
                        Text(
                            about,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (aboutOpen) Int.MAX_VALUE else 5,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp).clickable { aboutOpen = !aboutOpen },
                        )
                        if (!aboutOpen) SmallLabel("Tap to read more", Modifier.padding(top = 2.dp))
                    }
                }
            }
            Section("Google Finance", note = if (b.google != null) "Read from the Google Finance quote page" else
                "Google Finance's page couldn't be read just now; open it to see everything it shows") {
                b.google?.let { StatTable(it.stats) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { vm.push(Route.GoogleFinancePage(b.googleUrl, symbol)) }) { Text("Open in app") }
                    TextButton(onClick = { openUrl(context, b.googleUrl) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp)); Text(" Browser")
                    }
                }
            }
            if (b.errors.isNotEmpty() && (y == null || b.google == null)) {
                b.errors.forEach { SmallLabel(it, Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) }
            }
        }
    }
}

@Composable
private fun AnalysisTab(vm: AppViewModel, symbol: String, name: String, history: Load<Chart>, settings: SettingsUiState, head: Chart?) {
    val context = LocalContext.current
    val bench = settings.benchmark
    val analysis by produceState<Load<StockAnalysis>>(Load.Loading, history, bench) {
        val h = (history as? Load.Ok)?.value
        if (history is Load.Failed) { value = Load.Failed(history.message); return@produceState }
        if (h == null) return@produceState
        value = withContext(Dispatchers.Default) {
            val market = if (symbol.equals(bench, true)) null else runCatching { Stats.points(vm.repo.history(bench).bars) }.getOrNull()
            val yearAgo = Instant.now().epochSecond - 365L * 86_400
            val divs = h.dividends.filter { it.time >= yearAgo }.sumOf { it.amount }.takeIf { it > 0 }
            StockAnalysis.of(h.bars, market, bench, divs)?.let { Load.Ok(it) } ?: Load.Failed("Not enough history to analyse")
        }
    }
    when (val a = analysis) {
        Load.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is Load.Failed -> Text(a.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        is Load.Ok -> {
            val s = a.value
            val cur = head?.currency.orEmpty()
            Section("Returns", note = "With dividends reinvested. Longer periods also show the yearly rate. History since ${formatDay(s.firstDate)}.") {
                s.periods.forEach { p ->
                    StatRow(
                        p.label,
                        Format.pct(p.pct, digits = 1) + (p.annualizedPct?.let { "  (${Format.pct(it, digits = 1)}/yr)" } ?: ""),
                        Palette.change(p.pct),
                    )
                }
            }
            Section("Risk") {
                StatRow("Volatility, last year", Format.pct(s.volatility1y, signed = false))
                StatRow("Volatility, all history", Format.pct(s.volatilityAll, signed = false))
                StatRow("Worst fall, last year", Format.pct(s.drawdown1y.depthPct, signed = false, digits = 1), Palette.down)
                StatRow(
                    "Worst fall ever",
                    "${Format.pct(s.maxDrawdown.depthPct, signed = false, digits = 1)} (${formatDay(s.maxDrawdown.peak)} → ${formatDay(s.maxDrawdown.trough)})",
                    Palette.down,
                )
                StatRow("Recovered from it", s.maxDrawdown.recovered?.let(::formatDay) ?: "Not yet")
                StatRow("Sharpe ratio, last year", Format.num(s.sharpe1y))
                StatRow("Sortino ratio, last year", Format.num(s.sortino1y))
                if (s.beta != null) {
                    StatRow("Beta vs ${s.benchmark} (3 yr daily)", Format.num(s.beta))
                    StatRow("Correlation with ${s.benchmark}", Format.num(s.correlation))
                }
                s.bestDay?.let { StatRow("Best day", "${formatDay(it.date)}: ${Format.pct(it.value, digits = 1)}", Palette.up) }
                s.worstDay?.let { StatRow("Worst day", "${formatDay(it.date)}: ${Format.pct(it.value, digits = 1)}", Palette.down) }
            }
            Section("Trend and momentum") {
                Text(s.trend, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 6.dp))
                StatRow("Last close", Format.price(s.last, cur))
                StatRow("50-day average", Format.price(s.sma50, cur))
                StatRow("200-day average", Format.price(s.sma200, cur))
                s.rsi14?.let { r ->
                    StatRow("RSI (14 days)", Format.num(r, 1) + when {
                        r >= 70 -> " · overbought"
                        r <= 30 -> " · oversold"
                        else -> ""
                    })
                }
                if (s.high52 != null && s.low52 != null && s.high52 > s.low52) {
                    StatRow("52-week range", "${Format.price(s.low52, cur)} – ${Format.price(s.high52, cur)}")
                    StatRow("Position in range", Format.pct((s.last - s.low52) / (s.high52 - s.low52) * 100, signed = false, digits = 0))
                    StatRow("Below 52-week high", Format.pct((s.last / s.high52 - 1) * 100, digits = 1))
                }
                s.dividends12m?.let {
                    StatRow("Dividends, last 12 months", Format.price(it, cur))
                    StatRow("Trailing yield", Format.pct(it / s.last * 100, signed = false))
                }
            }
            if (s.years.isNotEmpty()) {
                Section("Year by year", note = "First and current years are partial.") {
                    ReturnBars(s.years.takeLast(20).map { it.first.toString() to it.second })
                }
            }
            val summary = analysisSummary(symbol, s, cur)
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val q = Prompts.analyze(symbol, name, summary)
                    if (vm.hasClaudeKey()) vm.askClaude(q, followUp = false, showTab = true) else ClaudeApp.ask(context, q)
                }) { Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp)); Text(" Ask Claude") }
                OutlinedButton(onClick = { vm.runAiMode("Analyze $name ($symbol) stock: recent performance, valuation versus peers, risks and outlook") }) {
                    Text("Google AI Mode")
                }
            }
        }
    }
}

private fun analysisSummary(symbol: String, s: StockAnalysis, cur: String): String = buildString {
    appendLine("$symbol last close ${Format.price(s.last, cur)} on ${s.lastDate}")
    s.periods.forEach { appendLine("${it.label}: ${Format.pct(it.pct, digits = 1)}" + (it.annualizedPct?.let { a -> " (${Format.pct(a, digits = 1)}/yr)" } ?: "")) }
    appendLine("Volatility 1y ${Format.pct(s.volatility1y, signed = false)}, max drawdown ${Format.pct(s.maxDrawdown.depthPct, signed = false)}")
    s.beta?.let { appendLine("Beta vs ${s.benchmark}: ${Format.num(it)}") }
    appendLine("RSI14 ${Format.num(s.rsi14, 1)}; ${s.trend}")
}

@Composable
private fun WhatIfTab(symbol: String, history: Load<Chart>) {
    var amount by rememberSaveable { mutableStateOf("10000") }
    var monthly by rememberSaveable { mutableStateOf("0") }
    var start by rememberSaveable { mutableStateOf<String?>(LocalDate.now().minusYears(5).toString()) }
    var pickDate by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<BacktestResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val h = (history as? Load.Ok)?.value

    fun run() {
        val bars = h?.bars ?: return
        scope.launch {
            error = null
            runCatching {
                withContext(Dispatchers.Default) {
                    Backtest.run(
                        BacktestInput(
                            listOf(Holding(symbol, weight = 100.0)),
                            mapOf(symbol to Stats.points(bars)),
                            amount.toDoubleOrNull() ?: 0.0,
                            monthly.toDoubleOrNull() ?: 0.0,
                            start?.let { LocalDate.parse(it) },
                            null,
                            Rebalance.None,
                        ),
                    )
                }
            }.onSuccess { result = it }.onFailure { error = it.message }
        }
    }
    LaunchedEffect(h, start) { if (h != null) run() }

    Section("If you had invested…", note = "Buys $symbol on the start date, optionally adds money every month, and reinvests dividends.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Amount") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            OutlinedTextField(monthly, { monthly = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Each month") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp)) { StartPresetChips(start) { start = it } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { pickDate = true }) { Text("Start: " + (start?.let { formatDay(LocalDate.parse(it)) } ?: "first trading day")) }
            Spacer(Modifier.weight(1f))
            Button(onClick = ::run, enabled = h != null) { Text("Calculate") }
        }
        if (history is Load.Failed) Text(history.message, color = MaterialTheme.colorScheme.error)
        if (history is Load.Loading) SmallLabel("Loading price history…")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    result?.let { BacktestResultView(it, null, "", "$symbol result") }
    if (pickDate) DatePickDialog(start, onDismiss = { pickDate = false }) { start = it }
}

@Composable
private fun SimilarTab(vm: AppViewModel, symbol: String, name: String) {
    val context = LocalContext.current
    val similar by produceState<Load<List<Quote>>>(Load.Loading, symbol) {
        value = runCatching {
            val syms = vm.repo.similar(symbol)
            val q = vm.repo.quotes(syms)
            syms.mapNotNull { q[it] }
        }.fold({ Load.Ok(it) }, { Load.Failed(it.message ?: "Failed") })
    }
    Section("Ask AI for similar stocks", note = "Google AI Mode opens in the AI tab without an account; Claude uses your API key " +
        "if you added one, otherwise the Claude app opens with the question.") {
        val q = Prompts.similar(symbol, name)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.runAiMode(q) }) { Text("Google AI Mode") }
            OutlinedButton(onClick = {
                if (vm.hasClaudeKey()) vm.askClaude(q, followUp = false, showTab = true) else ClaudeApp.ask(context, q)
            }) { Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp)); Text(" Claude") }
        }
        if (vm.hasClaudeKey()) {
            TextButton(onClick = { ClaudeApp.ask(context, q) }) { Text("Send to the Claude app instead") }
        }
    }
    Section("People also watch", note = "Yahoo Finance's list of stocks viewed together with $symbol") {
        when (val s = similar) {
            Load.Loading -> CircularProgressIndicator(Modifier.padding(8.dp))
            is Load.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error)
            is Load.Ok -> if (s.value.isEmpty()) SmallLabel("Nothing listed for $symbol") else Column {
                s.value.forEach { q -> QuoteRow(q.symbol, q.name, q) { vm.openStock(q.symbol, q.name) } }
            }
        }
    }
}

@Composable
private fun NewsTab(vm: AppViewModel, symbol: String) {
    val context = LocalContext.current
    val news by produceState<Load<List<NewsItem>>>(Load.Loading, symbol) {
        value = runCatching { vm.repo.news(symbol) }.fold({ Load.Ok(it) }, { Load.Failed(it.message ?: "Failed") })
    }
    when (val n = news) {
        Load.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is Load.Failed -> Text(n.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        is Load.Ok -> if (n.value.isEmpty()) {
            SmallLabel("No recent news", Modifier.padding(16.dp))
        } else {
            n.value.forEach { item ->
                Column(Modifier.fillMaxWidth().clickable { openUrl(context, item.url) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(item.title, style = MaterialTheme.typography.bodyLarge)
                    SmallLabel(listOf(item.publisher, formatEpoch(item.time)).filter { it.isNotBlank() }.joinToString(" · "))
                }
            }
        }
    }
}

/** A Material date picker for an ISO date; picking clears to null when [allowClear] and "Clear" is pressed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(initial: String?, allowClear: Boolean = false, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    val initialMs = initial?.let { runCatching { LocalDate.parse(it).toEpochDay() * 86_400_000 }.getOrNull() }
    val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = initialMs)
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(LocalDate.ofEpochDay(it / 86_400_000).toString()) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            Row {
                if (allowClear) TextButton(onClick = { onPick(null); onDismiss() }) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    ) { androidx.compose.material3.DatePicker(state = state) }
}
