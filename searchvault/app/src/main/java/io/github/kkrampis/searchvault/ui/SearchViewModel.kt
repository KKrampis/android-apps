package io.github.kkrampis.searchvault.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.kkrampis.searchvault.ai.Gemini
import io.github.kkrampis.searchvault.data.SearchStore
import io.github.kkrampis.searchvault.data.Settings
import io.github.kkrampis.searchvault.model.Archive
import io.github.kkrampis.searchvault.model.ArchiveJson
import io.github.kkrampis.searchvault.model.Engine
import io.github.kkrampis.searchvault.model.Search
import io.github.kkrampis.searchvault.model.Source
import io.github.kkrampis.searchvault.model.Turn
import io.github.kkrampis.searchvault.sync.JsonBin
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
import java.time.Instant

enum class StatusKind { Info, Ok, Error }

data class Status(val kind: StatusKind, val text: String)

data class SettingsUiState(
    val engine: Engine,
    val model: String,
    val geminiKey: String,
    val deviceName: String,
)

data class SyncUiState(
    val masterKey: String = "",
    val accessKey: String = "",
    val binId: String? = null,
    val lastSyncedAt: String? = null,
    val autoSync: Boolean = true,
    val busy: Boolean = false,
)

class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = Settings(app)
    private val store = SearchStore(app)
    private val gemini = Gemini()
    private val jsonBin = JsonBin()
    private val syncLock = Mutex()
    private var autoSyncJob: Job? = null

    private val _archive = MutableStateFlow(store.load())
    val archive: StateFlow<Archive> = _archive.asStateFlow()

    /** The search open on the Ask tab; follow-up questions are added to it. */
    private val _currentId = MutableStateFlow<String?>(null)
    val currentId: StateFlow<String?> = _currentId.asStateFlow()

    /** The question being answered right now, shown while Gemini works. */
    private val _asking = MutableStateFlow<String?>(null)
    val asking: StateFlow<String?> = _asking.asStateFlow()

    private val _settings = MutableStateFlow(readSettings())
    val settingsState: StateFlow<SettingsUiState> = _settings.asStateFlow()

    private val _sync = MutableStateFlow(readSyncState())
    val sync: StateFlow<SyncUiState> = _sync.asStateFlow()

    private val _status = MutableStateFlow<Status?>(null)
    val status: StateFlow<Status?> = _status.asStateFlow()

    fun clearStatus() { _status.value = null }

    private fun say(kind: StatusKind, text: String) { _status.value = Status(kind, text) }

    private fun readSettings() = SettingsUiState(settings.engine, settings.model, settings.geminiKey, settings.deviceName)

    private fun readSyncState() = SyncUiState(
        masterKey = settings.masterKey,
        accessKey = settings.accessKey,
        binId = settings.syncBinId,
        lastSyncedAt = settings.lastSyncedAt,
        autoSync = settings.autoSync,
    )

    private fun edit(sync: Boolean = true, transform: (Archive) -> Archive) {
        _archive.update(transform)
        val snapshot = _archive.value
        viewModelScope.launch(Dispatchers.IO) { store.save(snapshot) }
        if (sync) scheduleAutoSync()
    }

    fun find(id: String?): Search? = id?.let { i -> _archive.value.searches.firstOrNull { it.id == i } }

    // ── Asking ───────────────────────────────────────────────────
    fun newSearch() { _currentId.value = null }

    fun open(id: String) { _currentId.value = id }

    /** Asks Gemini. Adds a follow-up to the open search, or starts a new one. */
    fun ask(question: String) {
        val q = question.trim()
        if (q.isEmpty() || _asking.value != null) return
        val key = settings.geminiKey
        if (key.isBlank()) return say(StatusKind.Error, "Add your Gemini API key in Settings, or switch to Google AI Mode")
        val current = find(_currentId.value)
        _asking.value = q
        viewModelScope.launch {
            try {
                val turn = gemini.ask(key, settings.model, current?.turns.orEmpty(), q)
                val now = Instant.now().toString()
                val saved = find(current?.id)
                val search = if (saved != null) {
                    saved.copy(turns = saved.turns + turn, updatedAt = now)
                } else {
                    Search(title = q.take(120), turns = listOf(turn), createdAt = now)
                }
                edit { it.upsert(search) }
                _currentId.value = search.id
            } catch (e: Exception) {
                say(StatusKind.Error, e.message ?: "Search failed")
            } finally {
                _asking.value = null
            }
        }
    }

    /**
     * Saves what Google AI Mode shows for [question]. If the open search already ends with that
     * question its answer is updated, otherwise the question is added as a new turn.
     */
    fun saveAiMode(question: String, answer: String, pageUrl: String, links: List<Source>, quiet: Boolean = false): String {
        val q = question.trim().ifEmpty { "Google AI Mode" }
        val now = Instant.now().toString()
        val sources = (listOf(Source("Google AI Mode", pageUrl)) + links).distinctBy { it.url }
        val turn = Turn(question = q, answer = answer.trim(), sources = sources, engine = Engine.AiMode, at = now)
        val current = find(_currentId.value)?.takeIf { s -> s.turns.all { it.engine == Engine.AiMode } }
        val search = when {
            current == null -> Search(title = q.take(120), turns = listOf(turn), createdAt = now)
            current.turns.lastOrNull()?.question == q -> current.copy(turns = current.turns.dropLast(1) + turn, updatedAt = now)
            else -> current.copy(turns = current.turns + turn, updatedAt = now)
        }
        edit { it.upsert(search) }
        _currentId.value = search.id
        if (!quiet) say(StatusKind.Ok, if (answer.isBlank()) "Search saved" else "Answer saved")
        return search.id
    }

    fun setEngine(engine: Engine) {
        if (settings.engine == engine) return
        settings.engine = engine
        _currentId.value = null
        _settings.value = readSettings()
    }

    // ── Library ──────────────────────────────────────────────────
    fun toggleStar(id: String) {
        val s = find(id) ?: return
        edit { it.upsert(s.copy(starred = !s.starred, updatedAt = Instant.now().toString())) }
    }

    fun rename(id: String, title: String) {
        val s = find(id) ?: return
        if (title.isBlank()) return
        edit { it.upsert(s.copy(title = title.trim(), updatedAt = Instant.now().toString())) }
    }

    fun delete(id: String) {
        if (_currentId.value == id) _currentId.value = null
        edit { it.remove(id) }
        say(StatusKind.Info, "Search deleted")
    }

    fun markdown(id: String): String? = find(id)?.let(ArchiveJson::toMarkdown)

    // ── Settings ─────────────────────────────────────────────────
    fun saveGemini(key: String, model: String) {
        settings.geminiKey = key
        settings.model = model
        _settings.value = readSettings()
        say(StatusKind.Ok, if (key.isBlank()) "Gemini key removed" else "Gemini settings saved")
    }

    fun setDeviceName(name: String) {
        if (name.isBlank()) return
        settings.deviceName = name
        _settings.value = readSettings()
        say(StatusKind.Ok, "Device name saved")
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

    /** Uses an existing bin, such as the one another phone created, then syncs with it. */
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
        say(StatusKind.Ok, "Sync data cleared. Your searches are still on this phone.")
    }

    /** Pull and merge first (when there is a bin), then push the combined searches. */
    fun syncNow() {
        viewModelScope.launch { runSync(quiet = false) }
    }

    private fun scheduleAutoSync() {
        if (!settings.autoSync || settings.masterKey.isBlank()) return
        autoSyncJob?.cancel()
        autoSyncJob = viewModelScope.launch {
            delay(1500)
            runSync(quiet = true)
        }
    }

    private suspend fun runSync(quiet: Boolean): Unit = syncLock.withLock {
        val master = settings.masterKey
        if (master.isBlank()) {
            if (!quiet) say(StatusKind.Error, "Master Key required. Enter it above and save.")
            return@withLock
        }
        _sync.update { it.copy(busy = true) }
        try {
            var binId = settings.syncBinId
            var pulled = ""
            if (!binId.isNullOrBlank()) {
                val remote = jsonBin.pull(binId, master, settings.accessKey)
                val result = _archive.value.merge(remote)
                if (result.added + result.updated + result.removed > 0) {
                    _archive.value = result.archive
                    withContext(Dispatchers.IO) { store.save(result.archive) }
                    pulled = " Got ${result.added} new, ${result.updated} updated, ${result.removed} removed."
                }
            }
            val now = Instant.now().toString()
            val data = _archive.value.copy(device = settings.deviceName, exportedAt = now)
            binId = jsonBin.push(data, binId, master)
            settings.syncBinId = binId
            settings.lastSyncedAt = now
            if (!quiet || pulled.isNotEmpty()) say(StatusKind.Ok, "Synced ${data.searches.size} searches.$pulled")
        } catch (e: Exception) {
            say(StatusKind.Error, "Sync failed: ${e.message ?: "unknown error"}")
        } finally {
            _sync.value = readSyncState()
        }
    }

    // ── Import / export ──────────────────────────────────────────
    fun exportJson(): String =
        ArchiveJson.toJsonString(_archive.value.copy(device = settings.deviceName, exportedAt = Instant.now().toString()))

    fun writeTo(uri: Uri, content: String) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray(Charsets.UTF_8)) } != null
                }.getOrDefault(false)
            }
            if (ok) say(StatusKind.Ok, "Exported ${_archive.value.searches.size} searches") else say(StatusKind.Error, "Export failed")
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val text = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            } ?: return@launch say(StatusKind.Error, "Could not read that file")
            try {
                val result = _archive.value.merge(ArchiveJson.parse(text))
                edit { result.archive }
                say(StatusKind.Ok, "Imported ${result.added} new and ${result.updated} updated searches")
            } catch (e: ArchiveJson.FormatException) {
                say(StatusKind.Error, e.message ?: "Not a SearchVault file")
            }
        }
    }
}
