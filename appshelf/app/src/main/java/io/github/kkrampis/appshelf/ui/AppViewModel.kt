package io.github.kkrampis.appshelf.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.kkrampis.appshelf.data.AppRow
import io.github.kkrampis.appshelf.data.AppVersion
import io.github.kkrampis.appshelf.data.CatalogApp
import io.github.kkrampis.appshelf.data.RepoUrl
import io.github.kkrampis.appshelf.data.Source
import io.github.kkrampis.appshelf.data.SourceState
import io.github.kkrampis.appshelf.data.Store
import io.github.kkrampis.appshelf.install.Installer
import io.github.kkrampis.appshelf.work.UpdateWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What is happening to one app right now. */
sealed interface Task {
    /** [progress] is 0..1, or negative when the size is unknown. */
    data class Downloading(val progress: Float) : Task
    data object Installing : Task
}

data class UiSettings(
    val hasToken: Boolean,
    val includePrereleases: Boolean,
    val backgroundChecks: Boolean,
    val lastCheckedAt: Long,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)

    private val _sources = MutableStateFlow(store.settings.sources.map { SourceState(it) })
    val sources: StateFlow<List<SourceState>> = _sources.asStateFlow()

    private val _rows = MutableStateFlow<List<AppRow>>(emptyList())
    val rows: StateFlow<List<AppRow>> = _rows.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _tasks = MutableStateFlow<Map<String, Task>>(emptyMap())
    val tasks: StateFlow<Map<String, Task>> = _tasks.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<UiSettings> = _settings.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()
    private var refreshJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            Installer.results.collect { r ->
                _tasks.update { it - r.appId }
                _message.value = r.message
                refreshInstalled()
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun say(text: String) {
        _message.value = text
    }

    /** Reads every repository again (cheap when nothing changed) and matches installed apps. */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _loading.value = true
            try {
                val states = store.load()
                _sources.value = states
                _rows.value = store.rows(states)
                _settings.value = readSettings()
            } finally {
                _loading.value = false
            }
        }
    }

    /** Only looks at installed versions again, after coming back to the app. */
    fun refreshInstalled() {
        viewModelScope.launch { _rows.value = store.rows(_sources.value) }
    }

    fun row(appId: String): AppRow? = _rows.value.firstOrNull { it.app.id == appId }

    fun install(app: CatalogApp, version: AppVersion = app.latest) {
        if (jobs[app.id]?.isActive == true) return
        jobs[app.id] = viewModelScope.launch {
            _tasks.update { it + (app.id to Task.Downloading(0f)) }
            try {
                val file = store.download(app, version) { p ->
                    _tasks.update { it + (app.id to Task.Downloading(p)) }
                }
                _tasks.update { it + (app.id to Task.Installing) }
                Installer.install(getApplication(), file, app.id, app.name, store.settings.packageFor(app.id))
                // The result arrives through Installer.results.
            } catch (e: CancellationException) {
                _tasks.update { it - app.id }
                throw e
            } catch (e: Exception) {
                _tasks.update { it - app.id }
                _message.value = "${app.name}: ${e.message ?: "couldn't install"}"
            }
        }
    }

    fun cancel(appId: String) {
        jobs[appId]?.cancel()
        _tasks.update { it - appId }
    }

    fun updateAll() {
        val updates = _rows.value.filter { it.hasUpdate }
        updates.forEach { install(it.app) }
    }

    /** Adds a repository from a link or `owner/repo`. Returns an error to show, or null when added. */
    fun addSource(input: String): String? {
        val source = RepoUrl.parse(input) ?: return "That isn't a GitHub repository link. Try https://github.com/owner/repo or owner/repo."
        val current = store.settings.sources
        if (current.any { it.key.equals(source.key, ignoreCase = true) }) return "${source.key} is already in your list."
        store.settings.sources = current + source
        _sources.value = _sources.value + SourceState(source)
        _message.value = "Added ${source.key}."
        refresh()
        return null
    }

    fun removeSource(source: Source) {
        store.settings.sources = store.settings.sources.filterNot { it.key.equals(source.key, ignoreCase = true) }
        _sources.update { list -> list.filterNot { it.source == source } }
        _rows.update { list -> list.filterNot { it.app.source == source } }
    }

    fun moveSource(source: Source, delta: Int) {
        val list = store.settings.sources.toMutableList()
        val i = list.indexOfFirst { it == source }
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list.add(j, list.removeAt(i))
        store.settings.sources = list
        _sources.update { states -> list.mapNotNull { s -> states.firstOrNull { it.source == s } } }
    }

    fun setToken(token: String) {
        store.settings.token = token
        _settings.value = readSettings()
        _message.value = if (token.isBlank()) "Token removed." else "Token saved."
        refresh()
    }

    fun setIncludePrereleases(on: Boolean) {
        store.settings.includePrereleases = on
        _settings.value = readSettings()
        refresh()
    }

    fun setBackgroundChecks(on: Boolean) {
        store.settings.backgroundChecks = on
        UpdateWorker.schedule(getApplication(), on)
        _settings.value = readSettings()
    }

    fun clearDownloads() {
        store.clearDownloads()
        _message.value = "Downloaded APKs deleted."
    }

    private fun readSettings() = UiSettings(
        hasToken = store.settings.token.isNotBlank(),
        includePrereleases = store.settings.includePrereleases,
        backgroundChecks = store.settings.backgroundChecks,
        lastCheckedAt = store.settings.lastCheckedAt,
    )
}
