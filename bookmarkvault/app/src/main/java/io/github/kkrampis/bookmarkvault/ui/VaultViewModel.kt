package io.github.kkrampis.bookmarkvault.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.kkrampis.bookmarkvault.data.PushRecord
import io.github.kkrampis.bookmarkvault.data.Settings
import io.github.kkrampis.bookmarkvault.data.VaultStore
import io.github.kkrampis.bookmarkvault.model.Node
import io.github.kkrampis.bookmarkvault.model.Vault
import io.github.kkrampis.bookmarkvault.model.VaultJson
import io.github.kkrampis.bookmarkvault.model.addToFolder
import io.github.kkrampis.bookmarkvault.model.importInto
import io.github.kkrampis.bookmarkvault.model.moveNode
import io.github.kkrampis.bookmarkvault.model.removeNode
import io.github.kkrampis.bookmarkvault.model.replaceNode
import io.github.kkrampis.bookmarkvault.model.replaceWith
import io.github.kkrampis.bookmarkvault.sync.JsonBin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.Instant
import java.util.Date

enum class StatusKind { Info, Ok, Error }

data class Status(val kind: StatusKind, val text: String)

/** Data pulled from a bin or opened from a file, waiting for the person to pick what to import. */
data class PendingImport(val vault: Vault, val source: String)

data class SyncUiState(
    val masterKey: String = "",
    val accessKey: String = "",
    val binId: String? = null,
    val lastSyncedAt: String? = null,
    val history: List<PushRecord> = emptyList(),
    val busy: Boolean = false,
)

class VaultViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = Settings(app)
    private val store = VaultStore(app)
    private val jsonBin = JsonBin()

    private val _vault = MutableStateFlow(store.load(settings.deviceName))
    val vault: StateFlow<Vault> = _vault.asStateFlow()

    private val _sync = MutableStateFlow(readSyncState())
    val sync: StateFlow<SyncUiState> = _sync.asStateFlow()

    private val _deviceName = MutableStateFlow(settings.deviceName)
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _pending = MutableStateFlow<PendingImport?>(null)
    val pending: StateFlow<PendingImport?> = _pending.asStateFlow()

    /** One-off messages shown in a snackbar. */
    private val _status = MutableStateFlow<Status?>(null)
    val status: StateFlow<Status?> = _status.asStateFlow()

    fun clearStatus() { _status.value = null }

    private fun say(kind: StatusKind, text: String) { _status.value = Status(kind, text) }

    private fun readSyncState() = SyncUiState(
        masterKey = settings.masterKey,
        accessKey = settings.accessKey,
        binId = settings.syncBinId,
        lastSyncedAt = settings.lastSyncedAt,
        history = settings.history,
    )

    private fun edit(transform: (Vault) -> Vault) {
        _vault.update(transform)
        val snapshot = _vault.value
        viewModelScope.launch(Dispatchers.IO) { store.save(snapshot) }
    }

    // ── Library edits ────────────────────────────────────────────
    fun add(folderId: String, node: Node) = edit { it.addToFolder(folderId, node) }
    fun update(node: Node) = edit { it.replaceNode(node) }
    fun delete(id: String) = edit { it.removeNode(id) }
    fun move(id: String, folderId: String) = edit { it.moveNode(id, folderId) }

    // ── Sync ─────────────────────────────────────────────────────
    fun saveKeys(masterKey: String, accessKey: String) {
        if (masterKey.isBlank() && accessKey.isBlank()) return say(StatusKind.Error, "Enter at least one key")
        if (masterKey.isNotBlank()) settings.masterKey = masterKey
        if (accessKey.isNotBlank()) settings.accessKey = accessKey
        _sync.value = readSyncState()
        say(StatusKind.Ok, "Keys saved on this phone")
    }

    private fun rememberKeys(masterKey: String, accessKey: String) {
        if (masterKey.isNotBlank()) settings.masterKey = masterKey
        if (accessKey.isNotBlank()) settings.accessKey = accessKey
    }

    fun push(masterKey: String, accessKey: String) {
        rememberKeys(masterKey, accessKey)
        val key = masterKey.ifBlank { settings.masterKey }
        if (key.isBlank()) return say(StatusKind.Error, "Master Key required. Enter it above and try again.")
        val now = Instant.now().toString()
        val data = _vault.value.copy(exportedAt = now, device = settings.deviceName)
        _sync.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val result = jsonBin.push(data, settings.syncBinId, key)
                settings.syncBinId = result.binId
                settings.lastSyncedAt = now
                settings.history = listOf(PushRecord(result.binId, now, data.device, data.totalCount)) + settings.history
                say(StatusKind.Ok, "Pushed ${data.totalCount} bookmarks. Bin ID: ${result.binId}")
            } catch (e: Exception) {
                say(StatusKind.Error, e.message ?: "Push failed")
            } finally {
                _sync.value = readSyncState()
            }
        }
    }

    fun pull(binId: String, masterKey: String, accessKey: String) {
        if (binId.isBlank()) return say(StatusKind.Error, "Enter a Sync ID (Bin ID)")
        rememberKeys(masterKey, accessKey)
        _sync.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val data = jsonBin.pull(binId, settings.masterKey, settings.accessKey)
                _pending.value = PendingImport(data, "bin $binId")
                say(StatusKind.Ok, "Pulled from \"${data.device.ifBlank { "Unknown" }}\". Pick what to import.")
            } catch (e: Exception) {
                say(StatusKind.Error, e.message ?: "Pull failed")
            } finally {
                _sync.value = readSyncState()
            }
        }
    }

    // ── Import / export ──────────────────────────────────────────
    fun loadText(text: String, source: String) {
        if (text.isBlank()) return say(StatusKind.Error, "Paste JSON content first")
        try {
            _pending.value = PendingImport(VaultJson.parse(text), source)
            say(StatusKind.Ok, "Loaded $source. Pick what to import.")
        } catch (e: VaultJson.FormatException) {
            say(StatusKind.Error, e.message ?: "Not a valid BookmarkVault file")
        }
    }

    fun loadFile(uri: Uri) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val text = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            }
            if (text == null) say(StatusKind.Error, "Could not read that file") else loadText(text, uri.lastPathSegment ?: "file")
        }
    }

    fun cancelImport() { _pending.value = null }

    /** Adds the chosen folders under "BookmarkVault — <device> · <date>" in Other bookmarks. */
    fun importSelected(selected: Set<String>?) {
        val p = _pending.value ?: return
        if (selected != null && selected.isEmpty()) return say(StatusKind.Error, "Nothing selected")
        val title = "BookmarkVault — ${p.vault.device.ifBlank { "Unknown" }} · ${DateFormat.getDateInstance().format(Date())}"
        var added = 0
        edit { v -> v.importInto(p.vault, selected, title).also { added = it.second }.first }
        _pending.value = null
        say(StatusKind.Ok, "Imported $added bookmarks into Other bookmarks › $title")
    }

    /** Makes the phone's library a copy of the pulled data, for keeping it in step with one browser. */
    fun replaceLibrary() {
        val p = _pending.value ?: return
        edit { it.replaceWith(p.vault) }
        _pending.value = null
        say(StatusKind.Ok, "Library replaced with ${p.vault.totalCount} bookmarks from ${p.vault.device.ifBlank { "Unknown" }}")
    }

    fun exportJson(): String = VaultJson.toJsonString(stamped())
    fun exportHtml(): String = VaultJson.toNetscapeHtml(stamped())

    private fun stamped() = _vault.value.copy(exportedAt = Instant.now().toString(), device = settings.deviceName)

    fun writeTo(uri: Uri, content: String, label: String) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray(Charsets.UTF_8)) } != null
                }.getOrDefault(false)
            }
            if (ok) say(StatusKind.Ok, "Exported $label") else say(StatusKind.Error, "Export failed")
        }
    }

    // ── Settings ─────────────────────────────────────────────────
    fun setDeviceName(name: String) {
        if (name.isBlank()) return
        settings.deviceName = name
        _deviceName.value = settings.deviceName
        say(StatusKind.Ok, "Device name: \"${name.trim()}\"")
    }

    fun clearSync() {
        settings.clearSync()
        _sync.value = readSyncState()
        say(StatusKind.Ok, "Sync data cleared")
    }

    fun clearLibrary() {
        edit { Vault.empty(settings.deviceName) }
        say(StatusKind.Ok, "Library emptied")
    }
}
