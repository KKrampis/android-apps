package io.github.kkrampis.stockvault.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.kkrampis.stockvault.ai.ClaudeAnswer
import io.github.kkrampis.stockvault.ai.ClaudeApi
import io.github.kkrampis.stockvault.analysis.Backtest
import io.github.kkrampis.stockvault.analysis.BacktestInput
import io.github.kkrampis.stockvault.analysis.BacktestResult
import io.github.kkrampis.stockvault.analysis.Stats
import io.github.kkrampis.stockvault.data.HistoryCache
import io.github.kkrampis.stockvault.data.LibraryStore
import io.github.kkrampis.stockvault.data.MarketRepository
import io.github.kkrampis.stockvault.data.RecentStore
import io.github.kkrampis.stockvault.data.Settings
import io.github.kkrampis.stockvault.model.BacktestPlan
import io.github.kkrampis.stockvault.model.Holding
import io.github.kkrampis.stockvault.model.Library
import io.github.kkrampis.stockvault.model.LibraryJson
import io.github.kkrampis.stockvault.model.LibraryOps
import io.github.kkrampis.stockvault.model.Portfolio
import io.github.kkrampis.stockvault.sync.JsonBin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate

enum class StatusKind { Info, Ok, Error }

data class Status(val kind: StatusKind, val text: String)

/** Screens shown on top of the tabs. Back returns to the one below. */
sealed interface Route {
    data class Stock(val symbol: String, val name: String = "") : Route
    data class PortfolioEdit(val id: String?, val draft: Portfolio? = null) : Route
    data class Compare(val ids: List<String>) : Route
    data class GoogleFinancePage(val url: String, val title: String) : Route
}

data class SettingsUiState(
    val deviceName: String,
    val privateMode: Boolean,
    val claudeKey: String,
    val claudeModel: String,
    val markets: List<String>,
    val benchmark: String,
)

data class SyncUiState(
    val masterKey: String = "",
    val accessKey: String = "",
    val binId: String? = null,
    val lastSyncedAt: String? = null,
    val autoSync: Boolean = true,
    val busy: Boolean = false,
)

/** A question and Claude's answer, kept for follow-ups. */
data class ClaudeTurn(val question: String, val answer: ClaudeAnswer)

/** A backtest of a portfolio and of the benchmark over the same days. */
data class BacktestRun(val portfolio: Portfolio, val result: BacktestResult, val benchmark: BacktestResult?, val benchmarkSymbol: String)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = Settings(app)
    private val store = LibraryStore(app)
    private val recentStore = RecentStore(app)
    val cache = HistoryCache(File(app.cacheDir, "history"))
    val repo = MarketRepository(cache)
    private val jsonBin = JsonBin()
    private val syncLock = Mutex()
    private var autoSyncJob: Job? = null

    private val _library = MutableStateFlow(store.load())
    val library: StateFlow<Library> = _library.asStateFlow()

    private val _settings = MutableStateFlow(readSettings())
    val settingsState: StateFlow<SettingsUiState> = _settings.asStateFlow()

    private val _sync = MutableStateFlow(readSyncState())
    val sync: StateFlow<SyncUiState> = _sync.asStateFlow()

    private val _status = MutableStateFlow<Status?>(null)
    val status: StateFlow<Status?> = _status.asStateFlow()

    private val _stack = MutableStateFlow<List<Route>>(emptyList())
    val stack: StateFlow<List<Route>> = _stack.asStateFlow()

    private val _recent = MutableStateFlow(recentStore.load())
    val recent: StateFlow<List<Pair<String, String>>> = _recent.asStateFlow()

    /** Text shared to the app (for example Claude's answer), waiting on the AI tab. */
    private val _incoming = MutableStateFlow<String?>(null)
    val incoming: StateFlow<String?> = _incoming.asStateFlow()

    /** A question to run in Google AI Mode, set from a stock's "Find similar". */
    private val _aiModeQuery = MutableStateFlow<String?>(null)
    val aiModeQuery: StateFlow<String?> = _aiModeQuery.asStateFlow()

    private val _claude = MutableStateFlow<List<ClaudeTurn>>(emptyList())
    val claude: StateFlow<List<ClaudeTurn>> = _claude.asStateFlow()

    private val _claudeBusy = MutableStateFlow<String?>(null)
    val claudeBusy: StateFlow<String?> = _claudeBusy.asStateFlow()

    /** Set when another screen wants the AI tab shown. */
    private val _showAi = MutableStateFlow(0)
    val showAi: StateFlow<Int> = _showAi.asStateFlow()

    fun clearStatus() { _status.value = null }

    fun say(kind: StatusKind, text: String) { _status.value = Status(kind, text) }

    private fun readSettings() = SettingsUiState(
        deviceName = settings.deviceName,
        privateMode = settings.privateMode,
        claudeKey = settings.claudeKey,
        claudeModel = settings.claudeModel,
        markets = settings.marketSymbols,
        benchmark = settings.benchmark,
    )

    private fun readSyncState() = SyncUiState(
        masterKey = settings.masterKey,
        accessKey = settings.accessKey,
        binId = settings.syncBinId,
        lastSyncedAt = settings.lastSyncedAt,
        autoSync = settings.autoSync,
    )

    // ── Navigation ───────────────────────────────────────────────
    fun push(route: Route) {
        _stack.update { it + route }
        if (route is Route.Stock) _recent.value = recentStore.add(route.symbol, route.name)
    }

    fun pop() = _stack.update { it.dropLast(1) }

    fun openStock(symbol: String, name: String = "") = push(Route.Stock(symbol.trim().uppercase(), name))

    fun clearRecent() {
        recentStore.clear()
        _recent.value = emptyList()
    }

    // ── Library ──────────────────────────────────────────────────
    private fun edit(transform: (Library) -> Library) {
        _library.update(transform)
        val snapshot = _library.value
        viewModelScope.launch(Dispatchers.IO) { store.save(snapshot) }
        scheduleAutoSync()
    }

    fun addFolder(name: String, parentId: String?) = edit { LibraryOps.addFolder(it, name, parentId) }
    fun renameFolder(id: String, name: String) = edit { LibraryOps.renameFolder(it, id, name) }
    fun moveFolder(id: String, parentId: String?) = edit { LibraryOps.moveFolder(it, id, parentId) }
    fun deleteFolder(id: String) = edit { LibraryOps.deleteFolder(it, id) }

    fun saveStock(symbol: String, name: String, folderId: String?, price: Double?, currency: String, note: String = "") {
        val before = _library.value.saved.size
        edit { LibraryOps.saveStock(it, symbol, name, folderId, price, currency, note) }
        if (_library.value.saved.size > before) {
            say(StatusKind.Ok, "Saved ${symbol.uppercase()} in ${_library.value.pathOf(folderId)}")
        } else {
            say(StatusKind.Info, "${symbol.uppercase()} is already in that folder")
        }
    }

    fun removeSaved(id: String) = edit { LibraryOps.removeStock(it, id) }
    fun setNote(id: String, note: String) = edit { LibraryOps.updateStock(it, id) { s -> s.copy(note = note) } }
    fun moveSaved(id: String, folderId: String?) = edit { LibraryOps.updateStock(it, id) { s -> s.copy(folderId = folderId) } }

    fun savePortfolio(p: Portfolio) = edit { LibraryOps.upsertPortfolio(it, p) }
    fun deletePortfolio(id: String) = edit { LibraryOps.deletePortfolio(it, id) }

    fun addToPortfolio(portfolioId: String, symbol: String, name: String) {
        val p = _library.value.portfolios.firstOrNull { it.id == portfolioId } ?: return
        if (p.holdings.any { it.symbol.equals(symbol, true) }) return say(StatusKind.Info, "$symbol is already in ${p.name}")
        val holdings = LibraryOps.equalWeights(p.holdings + Holding(symbol.uppercase(), name, 0.0))
        savePortfolio(p.copy(holdings = holdings))
        say(StatusKind.Ok, "Added $symbol to ${p.name} (weights evened out to ${"%.1f".format(holdings.first().weight)} % each)")
    }

    /** A new, unsaved portfolio with the folder's stocks in equal weights, opened in the editor. */
    fun portfolioFromFolder(folderId: String?) {
        val lib = _library.value
        val stocks = lib.saved.filter { it.folderId == folderId }.distinctBy { it.symbol }
        if (stocks.isEmpty()) return say(StatusKind.Info, "That folder has no stocks")
        val p = Portfolio(
            name = lib.pathOf(folderId),
            holdings = LibraryOps.equalWeights(stocks.map { Holding(it.symbol, it.name, 0.0) }),
            plan = BacktestPlan(benchmark = settings.benchmark),
        )
        push(Route.PortfolioEdit(null, p))
    }

    fun newPortfolioPlan() = BacktestPlan(benchmark = settings.benchmark)

    // ── Backtests ────────────────────────────────────────────────
    suspend fun backtest(p: Portfolio): BacktestRun {
        val bench = p.plan.benchmark.trim().uppercase().ifBlank { settings.benchmark }
        val symbols = p.holdings.map { it.symbol } + bench
        val charts = withContext(Dispatchers.IO) {
            symbols.distinct().associateWith { s -> runCatching { repo.history(s) } }
        }
        val failed = p.holdings.map { it.symbol }.filter { charts[it]?.isFailure != false }
        if (failed.isNotEmpty()) {
            val why = charts[failed.first()]?.exceptionOrNull()?.message
            throw IllegalStateException("Could not load prices for ${failed.joinToString()}" + (why?.let { ": $it" } ?: ""))
        }
        val prices = charts.mapNotNull { (s, r) -> r.getOrNull()?.let { s to Stats.points(it.bars) } }.toMap()
        val start = p.plan.start?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val end = p.plan.end?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return withContext(Dispatchers.Default) {
            val result = Backtest.run(
                BacktestInput(p.holdings, prices, p.plan.amount, p.plan.monthly, start, end, p.plan.rebalance),
            )
            val benchmark = prices[bench]?.let {
                runCatching {
                    Backtest.run(
                        BacktestInput(listOf(Holding(bench, weight = 100.0)), mapOf(bench to it), p.plan.amount, p.plan.monthly,
                            result.start, result.end, io.github.kkrampis.stockvault.model.Rebalance.None),
                    ).takeIf { b -> b.start == result.start }
                }.getOrNull()
            }
            BacktestRun(p, result, benchmark, bench)
        }
    }

    // ── AI ───────────────────────────────────────────────────────
    fun runAiMode(query: String) {
        _aiModeQuery.value = query
        _stack.value = emptyList()
        _showAi.update { it + 1 }
    }

    fun aiModeHandled() { _aiModeQuery.value = null }

    fun receiveShared(text: String) {
        _incoming.value = text
        _stack.value = emptyList()
        _showAi.update { it + 1 }
    }

    fun incomingHandled() { _incoming.value = null }

    fun hasClaudeKey() = settings.claudeKey.isNotBlank()

    fun askClaude(question: String, followUp: Boolean, showTab: Boolean = false) {
        val q = question.trim()
        if (q.isEmpty() || _claudeBusy.value != null) return
        val key = settings.claudeKey
        if (key.isBlank()) return say(StatusKind.Error, "Add an Anthropic API key in Settings, or send the question to the Claude app")
        if (showTab) {
            _stack.value = emptyList()
            _showAi.update { it + 1 }
        }
        val history = if (followUp) _claude.value else emptyList()
        if (!followUp) _claude.value = emptyList()
        _claudeBusy.value = q
        viewModelScope.launch {
            try {
                val answer = ClaudeApi(key, settings.claudeModel).ask(q, history.map { it.question to it.answer.text })
                _claude.value = history + ClaudeTurn(q, answer)
            } catch (e: Exception) {
                say(StatusKind.Error, e.message ?: "Claude request failed")
            } finally {
                _claudeBusy.value = null
            }
        }
    }

    fun clearClaude() { _claude.value = emptyList() }

    // ── Settings ─────────────────────────────────────────────────
    fun saveClaude(key: String, model: String) {
        settings.claudeKey = key
        settings.claudeModel = model
        _settings.value = readSettings()
        say(StatusKind.Ok, if (key.isBlank()) "Anthropic key removed" else "Claude settings saved")
    }

    fun setPrivateMode(on: Boolean) {
        settings.privateMode = on
        _settings.value = readSettings()
    }

    fun setDeviceName(name: String) {
        if (name.isBlank()) return
        settings.deviceName = name
        _settings.value = readSettings()
    }

    fun setMarkets(symbols: List<String>) {
        settings.marketSymbols = symbols.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.distinct()
        _settings.value = readSettings()
    }

    fun setBenchmark(symbol: String) {
        if (symbol.isBlank()) return
        settings.benchmark = symbol
        _settings.value = readSettings()
    }

    fun clearCache() {
        cache.clear()
        say(StatusKind.Ok, "Price cache cleared")
    }

    // ── Sync ─────────────────────────────────────────────────────
    fun saveKeys(masterKey: String, accessKey: String) {
        settings.masterKey = masterKey
        settings.accessKey = accessKey
        _sync.value = readSyncState()
        say(StatusKind.Ok, "Keys saved on this phone")
    }

    fun setAutoSync(on: Boolean) {
        settings.autoSync = on
        _sync.value = readSyncState()
    }

    fun linkBin(binId: String) {
        val id = binId.trim()
        if (id.isEmpty()) return say(StatusKind.Error, "Enter a Sync ID (Bin ID)")
        settings.syncBinId = id
        _sync.value = readSyncState()
        syncNow()
    }

    fun clearSync() {
        settings.clearSync()
        _sync.value = readSyncState()
        say(StatusKind.Ok, "Sync settings cleared. Your folders and portfolios are still on this phone.")
    }

    fun syncNow() {
        viewModelScope.launch { runSync(quiet = false) }
    }

    private fun scheduleAutoSync() {
        if (!settings.autoSync || settings.masterKey.isBlank()) return
        autoSyncJob?.cancel()
        autoSyncJob = viewModelScope.launch {
            delay(2000)
            runSync(quiet = true)
        }
    }

    /** Pull and merge first (when there is a bin), then push the merged library. */
    private suspend fun runSync(quiet: Boolean): Unit = syncLock.withLock {
        val master = settings.masterKey
        if (master.isBlank()) {
            if (!quiet) say(StatusKind.Error, "Master Key required. Enter it above and save.")
            return@withLock
        }
        _sync.update { it.copy(busy = true) }
        try {
            var binId = settings.syncBinId
            if (!binId.isNullOrBlank()) {
                val remote = jsonBin.pull(binId, master, settings.accessKey)
                val merged = LibraryOps.merge(_library.value, remote)
                _library.value = merged
                withContext(Dispatchers.IO) { store.save(merged) }
            }
            val now = Instant.now().toString()
            val data = _library.value.copy(device = settings.deviceName, exportedAt = now)
            binId = jsonBin.push(data, binId, master)
            settings.syncBinId = binId
            settings.lastSyncedAt = now
            if (!quiet) {
                say(StatusKind.Ok, "Synced ${data.saved.size} saved stocks, ${data.folders.size} folders and ${data.portfolios.size} portfolios")
            }
        } catch (e: Exception) {
            say(StatusKind.Error, "Sync failed: ${e.message ?: "unknown error"}")
        } finally {
            _sync.value = readSyncState()
        }
    }

    // ── Import / export ──────────────────────────────────────────
    fun exportJson(): String =
        LibraryJson.toJsonString(_library.value.copy(device = settings.deviceName, exportedAt = Instant.now().toString()))

    fun writeTo(uri: Uri, content: String) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray(Charsets.UTF_8)) } != null
                }.getOrDefault(false)
            }
            if (ok) say(StatusKind.Ok, "Backup saved") else say(StatusKind.Error, "Export failed")
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val text = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            } ?: return@launch say(StatusKind.Error, "Could not read that file")
            try {
                val incoming = LibraryJson.parse(text)
                edit { LibraryOps.merge(it, incoming) }
                say(StatusKind.Ok, "Imported ${incoming.saved.size} stocks, ${incoming.folders.size} folders, ${incoming.portfolios.size} portfolios")
            } catch (e: LibraryJson.FormatException) {
                say(StatusKind.Error, e.message ?: "Not a StockVault file")
            }
        }
    }
}
