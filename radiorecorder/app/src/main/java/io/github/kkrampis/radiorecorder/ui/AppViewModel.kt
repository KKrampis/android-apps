package io.github.kkrampis.radiorecorder.ui

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.kkrampis.radiorecorder.core.ArchiveSettings
import io.github.kkrampis.radiorecorder.core.CleanupSettings
import io.github.kkrampis.radiorecorder.core.Config
import io.github.kkrampis.radiorecorder.core.Station
import io.github.kkrampis.radiorecorder.data.RecStatus
import io.github.kkrampis.radiorecorder.data.Recording
import io.github.kkrampis.radiorecorder.engine.Engine
import io.github.kkrampis.radiorecorder.engine.EngineStatus
import io.github.kkrampis.radiorecorder.service.RecorderService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val engine = Engine.get(app)
    val config: StateFlow<Config> = engine.config.state
    val status: StateFlow<EngineStatus> = engine.status

    private val _recordings = MutableStateFlow<List<Recording>>(emptyList())
    val recordings: StateFlow<List<Recording>> = _recordings

    private val _playing = MutableStateFlow<Long?>(null)
    val playing: StateFlow<Long?> = _playing
    private var player: MediaPlayer? = null

    /** Station being edited (a new one has a blank id); null when the editor is closed. */
    val editing = MutableStateFlow<Station?>(null)
    val toast = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                _recordings.value = engine.db.recent(400)
                if (!engine.isRunning) engine.refreshStatus()
                delay(2000)
            }
        }
    }

    private fun ensureService() = RecorderService.ensure(getApplication())

    // --- stations ---------------------------------------------------------------------------

    fun newStation(url: String = "") {
        editing.value = Station(id = "", name = "", url = url)
    }

    fun edit(st: Station) {
        editing.value = st
    }

    /** Returns an error message, or null when saved. */
    fun saveStation(st: Station): String? {
        val err = engine.config.saveStation(st)
        if (err == null) {
            editing.value = null
            engine.syncRecorders()
            ensureService()
            toast.value = "Station saved"
        }
        return err
    }

    fun deleteStation(id: String) {
        engine.config.deleteStation(id)
        editing.value = null
        engine.syncRecorders()
        ensureService()
        toast.value = "Station deleted. Its recordings are kept."
    }

    fun setEnabled(st: Station, enabled: Boolean) {
        engine.config.saveStation(st.copy(enabled = enabled))
        engine.syncRecorders()
        ensureService()
    }

    fun splitNow(id: String) {
        engine.splitNow(id)
        toast.value = "Started a new file"
    }

    fun setPaused(paused: Boolean) {
        engine.config.update { it.copy(paused = paused) }
        ensureService()
        engine.poke()
    }

    fun startIfNeeded() = ensureService()

    // --- settings ---------------------------------------------------------------------------

    fun saveSettings(archive: ArchiveSettings, secret: String?, cleanup: CleanupSettings) {
        engine.config.update { it.copy(archive = archive.copy(hasSecret = it.archive.hasSecret), cleanup = cleanup) }
        if (!secret.isNullOrBlank()) engine.config.setSecretKey(secret)
        engine.pokeBackground()
        toast.value = "Settings saved"
    }

    fun clearSecret() = engine.config.setSecretKey(null)

    suspend fun testKeys(access: String, secret: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        engine.uploader.checkKeys(access, secret.ifBlank { engine.config.secretKey() })
    }

    fun runCleanup() {
        toast.value = "Cleanup started: uploading pending recordings first"
        viewModelScope.launch(Dispatchers.IO) {
            val r = engine.cleaner.run()
            toast.value = "Cleanup: ${r.uploaded} uploaded, ${r.deleted} deleted, ${r.keptUnuploaded} kept"
        }
    }

    fun uploadNow() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!engine.uploader.enabled()) {
                toast.value = "Turn on uploading and enter your archive.org keys in Settings"
                return@launch
            }
            if (!engine.uploader.networkAllowed()) {
                toast.value = "Waiting for Wi-Fi (uploads are set to Wi-Fi only)"
                return@launch
            }
            toast.value = "Uploading pending recordings"
            val r = engine.uploader.processPending()
            toast.value = "${r.uploaded} uploaded" + if (r.failed > 0) ", ${r.failed} failed" else ""
        }
    }

    fun retry(rec: Recording) {
        engine.db.update(rec.id) { put("status", RecStatus.DONE.name); put("upload_attempts", 0); putNull("last_attempt_ms") }
        uploadNow()
    }

    // --- playback & sharing -----------------------------------------------------------------

    fun togglePlay(rec: Recording) {
        if (_playing.value == rec.id) {
            stopPlayback()
            return
        }
        stopPlayback()
        try {
            player = MediaPlayer().apply {
                setDataSource(rec.path)
                setOnCompletionListener { stopPlayback() }
                prepare()
                start()
            }
            _playing.value = rec.id
        } catch (e: Exception) {
            toast.value = "Can't play this file: ${e.message}"
            stopPlayback()
        }
    }

    fun stopPlayback() {
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        _playing.value = null
    }

    fun shareIntent(rec: Recording): Intent? {
        val ctx = getApplication<Application>()
        return try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", rec.file)
            Intent(Intent.ACTION_SEND).apply {
                type = if (rec.file.extension == "mp3") "audio/mpeg" else "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, listOfNotNull(rec.stationName, rec.program).joinToString(" — "))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.let { Intent.createChooser(it, "Share recording") }
        } catch (e: Exception) {
            toast.value = "Can't share this file: ${e.message}"
            null
        }
    }

    fun openUrl(url: String): Intent? = try {
        Intent(Intent.ACTION_VIEW, Uri.parse(url))
    } catch (e: ActivityNotFoundException) {
        null
    }

    override fun onCleared() {
        stopPlayback()
        super.onCleared()
    }
}
