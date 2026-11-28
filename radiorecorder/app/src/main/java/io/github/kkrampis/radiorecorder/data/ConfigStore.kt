package io.github.kkrampis.radiorecorder.data

import android.content.Context
import io.github.kkrampis.radiorecorder.core.Config
import io.github.kkrampis.radiorecorder.core.Names
import io.github.kkrampis.radiorecorder.core.Station
import io.github.kkrampis.radiorecorder.core.Validation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Settings in filesDir/config.json; the archive.org secret key in [SecureStore]. */
class ConfigStore(context: Context) {
    private val file = File(context.filesDir, "config.json")
    private val secure = SecureStore(context)
    private val lock = Any()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Config> = _state
    val current: Config get() = _state.value

    private fun load(): Config {
        val hasSecret = !secure.get(SECRET).isNullOrEmpty()
        return try {
            if (file.exists()) ConfigJson.fromJson(JSONObject(file.readText()), hasSecret) else Config(archive = Config().archive.copy(hasSecret = hasSecret))
        } catch (e: Exception) {
            Config()
        }
    }

    fun update(transform: (Config) -> Config): Config = synchronized(lock) {
        val next = transform(_state.value)
        val tmp = File(file.parentFile, "config.json.tmp")
        tmp.writeText(ConfigJson.toJson(next).toString(2))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
        _state.value = next
        next
    }

    fun station(id: String): Station? = current.stations.firstOrNull { it.id == id }

    /** Adds or replaces a station; returns an error message if it isn't valid. */
    fun saveStation(st: Station): String? {
        val withId = if (st.id.isBlank()) st.copy(id = "${Names.slug(st.name, 24)}-${UUID.randomUUID().toString().take(4)}") else st
        val clean = withId.copy(name = withId.name.trim(), url = withId.url.trim(), timezone = withId.timezone.trim())
        Validation.station(clean)?.let { return it }
        update { c ->
            val list = c.stations.toMutableList()
            val i = list.indexOfFirst { it.id == clean.id }
            if (i >= 0) list[i] = clean else list += clean
            c.copy(stations = list)
        }
        return null
    }

    fun deleteStation(id: String) = update { c -> c.copy(stations = c.stations.filterNot { it.id == id }) }

    fun secretKey(): String = secure.get(SECRET).orEmpty()

    fun setSecretKey(value: String?) {
        secure.put(SECRET, value?.trim())
        update { it.copy(archive = it.archive.copy(hasSecret = !value.isNullOrBlank())) }
    }

    private companion object {
        const val SECRET = "ia_secret_key"
    }
}
