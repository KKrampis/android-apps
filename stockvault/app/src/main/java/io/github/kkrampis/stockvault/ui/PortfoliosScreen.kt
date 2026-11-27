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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kkrampis.stockvault.ai.ClaudeApp
import io.github.kkrampis.stockvault.ai.Prompts
import io.github.kkrampis.stockvault.model.Format
import io.github.kkrampis.stockvault.model.Holding
import io.github.kkrampis.stockvault.model.Library
import io.github.kkrampis.stockvault.model.LibraryOps
import io.github.kkrampis.stockvault.model.Portfolio
import io.github.kkrampis.stockvault.model.Rebalance
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfoliosScreen(vm: AppViewModel, library: Library) {
    var selecting by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(if (selecting) "Pick portfolios to compare" else "Portfolios") },
                actions = {
                    if (selecting) {
                        TextButton(enabled = selected.size >= 2, onClick = {
                            vm.push(Route.Compare(selected.toList())); selecting = false; selected.clear()
                        }) { Text("Compare (${selected.size})") }
                        IconButton(onClick = { selecting = false; selected.clear() }) { Icon(Icons.Default.Close, "Cancel") }
                    } else if (library.portfolios.size >= 2) {
                        IconButton(onClick = { selecting = true }) { Icon(Icons.Default.CompareArrows, "Compare portfolios") }
                    }
                },
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(library.portfolios.sortedBy { it.name.lowercase() }, key = { it.id }) { p ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (selecting) {
                                if (p.id in selected) selected.remove(p.id) else selected.add(p.id)
                            } else {
                                vm.push(Route.PortfolioEdit(p.id))
                            }
                        }.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        if (selecting) Checkbox(checked = p.id in selected, onCheckedChange = null)
                        else Icon(Icons.Default.PieChart, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Text(p.name, fontWeight = FontWeight.Medium)
                            SmallLabel(
                                p.holdings.joinToString { "${it.symbol} ${Format.num(it.weight, 0)}%" }.ifBlank { "No holdings yet" },
                            )
                        }
                    }
                }
                if (library.portfolios.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No portfolios yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "A portfolio is a set of stocks with a share of the money for each. Backtest it to see what " +
                                    "an amount invested on a date, plus optional monthly additions, would be worth today, and " +
                                    "compare portfolios with each other and with the S&P 500.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
        if (!selecting) {
            ExtendedFloatingActionButton(
                onClick = { vm.push(Route.PortfolioEdit(null, Portfolio(name = "New portfolio", plan = vm.newPortfolioPlan()))) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New portfolio") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

/** Edit a portfolio's holdings and backtest settings, and run the backtest. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioEditScreen(vm: AppViewModel, route: Route.PortfolioEdit, library: Library) {
    val context = LocalContext.current
    val original = route.id?.let { id -> library.portfolios.firstOrNull { it.id == id } } ?: route.draft
        ?: Portfolio(name = "New portfolio", plan = vm.newPortfolioPlan())
    var p by remember(original.id) { mutableStateOf(original) }
    var amountText by remember(original.id) { mutableStateOf(Format.num(original.plan.amount, 0).replace(",", "")) }
    var monthlyText by remember(original.id) { mutableStateOf(Format.num(original.plan.monthly, 0).replace(",", "")) }
    var adding by remember { mutableStateOf(false) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var rebalanceMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var run by remember { mutableStateOf<BacktestRun?>(null) }
    var running by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val isSaved = library.portfolios.any { it.id == p.id }
    val dirty = p != library.portfolios.firstOrNull { it.id == p.id }

    fun current() = p.copy(plan = p.plan.copy(amount = amountText.toDoubleOrNull() ?: 0.0, monthly = monthlyText.toDoubleOrNull() ?: 0.0))

    fun runBacktest() {
        val portfolio = current()
        scope.launch {
            running = true
            error = null
            runCatching { vm.backtest(portfolio) }.onSuccess { run = it }.onFailure { error = it.message ?: "Backtest failed" }
            running = false
        }
    }

    // Saved portfolios with holdings show their last result straight away.
    LaunchedEffect(original.id) { if (isSaved && original.holdings.isNotEmpty()) runBacktest() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = vm::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                if (dirty || !isSaved) TextButton(onClick = { vm.savePortfolio(current()); p = current() }) { Text("Save") }
                if (isSaved) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete") }
            },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Section("Holdings", note = "Weights are the share of money in each. They are scaled to 100 % when they add up to something else.") {
                OutlinedTextField(p.name, { p = p.copy(name = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                p.holdings.forEachIndexed { i, h ->
                    HoldingRow(h, onWeight = { w ->
                        p = p.copy(holdings = p.holdings.toMutableList().also { it[i] = h.copy(weight = w) })
                    }, onOpen = { vm.openStock(h.symbol, h.name) }) {
                        p = p.copy(holdings = p.holdings.filterIndexed { j, _ -> j != i })
                    }
                }
                val total = p.totalWeight
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    SmallLabel("Total ${Format.num(total, 1)} %", Modifier.weight(1f))
                    TextButton(onClick = { p = p.copy(holdings = LibraryOps.equalWeights(p.holdings)) }, enabled = p.holdings.size > 1) { Text("Equal weights") }
                    TextButton(onClick = { adding = true }) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Text(" Add") }
                }
            }
            Section("Backtest", note = "Buy on the start date, add the monthly amount on the first trading day of each month, " +
                "rebalance if chosen. Dividends are reinvested; no fees or taxes.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(amountText, { amountText = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Start with") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(monthlyText, { monthlyText = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Add monthly") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp)) {
                    StartPresetChips(p.plan.start) { p = p.copy(plan = p.plan.copy(start = it)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { pickStart = true }) {
                        Text("From " + (p.plan.start?.let { formatDay(LocalDate.parse(it)) } ?: "earliest"))
                    }
                    TextButton(onClick = { pickEnd = true }) {
                        Text("to " + (p.plan.end?.let { formatDay(LocalDate.parse(it)) } ?: "today"))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        OutlinedButton(onClick = { rebalanceMenu = true }) { Text("Rebalance: ${p.plan.rebalance.label}") }
                        DropdownMenu(expanded = rebalanceMenu, onDismissRequest = { rebalanceMenu = false }) {
                            Rebalance.entries.forEach { r ->
                                DropdownMenuItem(text = { Text(r.label) }, onClick = { p = p.copy(plan = p.plan.copy(rebalance = r)); rebalanceMenu = false })
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        p.plan.benchmark, { p = p.copy(plan = p.plan.copy(benchmark = it.uppercase().trim())) },
                        label = { Text("Benchmark") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Button(onClick = ::runBacktest, enabled = p.holdings.isNotEmpty() && !running) { Text("Run backtest") }
                    if (running) CircularProgressIndicator(Modifier.padding(start = 12.dp).size(22.dp), strokeWidth = 2.dp)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
            }
            run?.let { r ->
                BacktestResultView(r.result, r.benchmark, r.benchmarkSymbol, "Result")
                if (r.benchmark == null) {
                    SmallLabel("No ${r.benchmarkSymbol} prices for the whole period, so there's no benchmark line.", Modifier.padding(horizontal = 16.dp))
                }
                val summary = backtestSummary(r.portfolio.name, r.portfolio.holdings.joinToString { "${it.symbol} ${Format.num(it.weight, 1)}%" },
                    r.result, r.benchmark, r.benchmarkSymbol)
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val q = Prompts.portfolio(r.portfolio.name, summary)
                        if (vm.hasClaudeKey()) vm.askClaude(q, followUp = false, showTab = true) else ClaudeApp.ask(context, q)
                    }) { Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp)); Text(" Ask Claude") }
                    OutlinedButton(onClick = { shareText(context, "Backtest: ${r.portfolio.name}", summary) }) {
                        Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Text(" Share")
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (adding) {
        SymbolSearchDialog(vm.repo, onDismiss = { adding = false }) { hit ->
            if (p.holdings.none { it.symbol.equals(hit.symbol, true) }) {
                val remaining = (100 - p.totalWeight).coerceAtLeast(0.0)
                p = p.copy(holdings = p.holdings + Holding(hit.symbol, hit.name, if (p.holdings.isEmpty()) 100.0 else remaining))
            }
        }
    }
    if (pickStart) DatePickDialog(p.plan.start, allowClear = true, onDismiss = { pickStart = false }) { p = p.copy(plan = p.plan.copy(start = it)) }
    if (pickEnd) DatePickDialog(p.plan.end, allowClear = true, onDismiss = { pickEnd = false }) { p = p.copy(plan = p.plan.copy(end = it)) }
    if (confirmDelete) {
        ConfirmDialog("Delete ${p.name}?", "The portfolio is removed. Its stocks stay wherever else you saved them.", onDismiss = { confirmDelete = false }) {
            vm.deletePortfolio(p.id)
            vm.pop()
        }
    }
}

@Composable
private fun HoldingRow(h: Holding, onWeight: (Double) -> Unit, onOpen: () -> Unit, onRemove: () -> Unit) {
    var text by remember(h.symbol) { mutableStateOf(Format.num(h.weight, 2).replace(",", "").removeSuffix(".00")) }
    LaunchedEffect(h.weight) {
        if (text.toDoubleOrNull() != h.weight) text = Format.num(h.weight, 2).replace(",", "").removeSuffix(".00")
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
            Text(h.symbol, fontWeight = FontWeight.SemiBold)
            if (h.name.isNotBlank()) SmallLabel(h.name)
        }
        OutlinedTextField(
            text,
            { v ->
                text = v.filter { c -> c.isDigit() || c == '.' }
                text.toDoubleOrNull()?.let(onWeight)
            },
            suffix = { Text("%") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(110.dp),
        )
        IconButton(onClick = onRemove) { Icon(Icons.Default.Close, "Remove") }
    }
}

/** Backtests several portfolios over the same period with the same money, and overlays them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(vm: AppViewModel, route: Route.Compare, library: Library) {
    val portfolios = route.ids.mapNotNull { id -> library.portfolios.firstOrNull { it.id == id } }
    var amountText by remember { mutableStateOf("10000") }
    var monthlyText by remember { mutableStateOf("0") }
    var start by remember { mutableStateOf<String?>(LocalDate.now().minusYears(10).toString()) }
    var results by remember { mutableStateOf<List<Pair<Portfolio, Result<BacktestRun>>>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun runAll() {
        scope.launch {
            running = true
            val amount = amountText.toDoubleOrNull() ?: 0.0
            val monthly = monthlyText.toDoubleOrNull() ?: 0.0
            // First pass finds the latest start any portfolio can manage, so all cover the same days.
            fun plan(p: Portfolio, s: String?) = p.copy(plan = p.plan.copy(amount = amount, monthly = monthly, start = s, end = null))
            val first = coroutineScope { portfolios.map { p -> async { p to runCatching { vm.backtest(plan(p, start)) } } }.awaitAll() }
            val commonStart = first.mapNotNull { it.second.getOrNull()?.result?.start }.maxOrNull()
            results = if (commonStart != null && first.any { it.second.getOrNull()?.result?.start != commonStart }) {
                coroutineScope { portfolios.map { p -> async { p to runCatching { vm.backtest(plan(p, commonStart.toString())) } } }.awaitAll() }
            } else {
                first
            }
            running = false
        }
    }
    LaunchedEffect(route.ids, start) { runAll() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Compare portfolios") },
            navigationIcon = { IconButton(onClick = vm::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Section("Same money, same days") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(amountText, { amountText = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Start with") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(monthlyText, { monthlyText = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Add monthly") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp)) { StartPresetChips(start) { start = it } }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Button(onClick = ::runAll, enabled = !running) { Text("Compare") }
                    if (running) CircularProgressIndicator(Modifier.padding(start = 12.dp).size(22.dp), strokeWidth = 2.dp)
                }
            }
            val ok = results.mapNotNull { (p, r) -> r.getOrNull()?.let { p to it } }
            if (ok.isNotEmpty()) {
                val r0 = ok.first().second.result
                Section("Growth", note = "${formatDay(r0.start)} – ${formatDay(r0.end)}") {
                    LineChart(
                        series = ok.mapIndexed { i, (p, run) -> run.result.values.toSeries(p.name, Palette.series[i % Palette.series.size]) } +
                            listOfNotNull(ok.first().second.benchmark?.values?.toSeries(ok.first().second.benchmarkSymbol, MaterialTheme.colorScheme.onSurfaceVariant, dashed = true)),
                        formatX = ::formatEpochDay,
                        formatY = { Format.money(it) },
                        showLegend = true,
                    )
                }
                Section("Results") {
                    Row(Modifier.fillMaxWidth()) {
                        listOf("Portfolio" to 1.4f, "Worth" to 1f, "CAGR" to 0.8f, "Vol." to 0.7f, "Worst" to 0.8f).forEach { (h, w) ->
                            SmallLabel(h, Modifier.weight(w))
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    val rows = ok.map { (p, run) -> p.name to run.result } +
                        listOfNotNull(ok.first().second.benchmark?.let { ok.first().second.benchmarkSymbol to it })
                    rows.sortedByDescending { it.second.finalValue }.forEach { (name, r) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.4f))
                            Text(Format.money(r.finalValue), modifier = Modifier.weight(1f))
                            Text(Format.pct(r.cagrPct, digits = 1), color = Palette.change(r.cagrPct), modifier = Modifier.weight(0.8f))
                            Text(Format.pct(r.volatilityPct, signed = false, digits = 0), modifier = Modifier.weight(0.7f))
                            Text(Format.pct(r.maxDrawdown.depthPct, signed = false, digits = 0), color = Palette.down, modifier = Modifier.weight(0.8f))
                        }
                    }
                    SmallLabel("Invested ${Format.money(r0.totalInvested)} in each.", Modifier.padding(top = 6.dp))
                }
            }
            results.filter { it.second.isFailure }.forEach { (p, r) ->
                Text("${p.name}: ${r.exceptionOrNull()?.message}", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
