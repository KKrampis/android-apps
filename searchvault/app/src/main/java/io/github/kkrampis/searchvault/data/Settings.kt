package io.github.kkrampis.searchvault.data

import android.content.Context
import android.os.Build
import io.github.kkrampis.searchvault.ai.Gemini
import io.github.kkrampis.searchvault.model.Engine

/**
 * Plain settings live in shared preferences; the Gemini API key and the two JSONBin keys go
 * through [SecureStore], encrypted with a key held in the Android Keystore.
 */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val secure = SecureStore(context)

    var deviceName: String
        get() = prefs.getString("deviceName", null)?.takeIf { it.isNotBlank() } ?: defaultDeviceName()
        set(v) = prefs.edit().putString("deviceName", v.trim()).apply()

    var engine: Engine
        get() = Engine.from(prefs.getString("engine", null))
        set(v) = prefs.edit().putString("engine", v.id).apply()

    var model: String
        get() = prefs.getString("model", null)?.takeIf { it.isNotBlank() } ?: Gemini.DEFAULT_MODEL
        set(v) = prefs.edit().putString("model", v.trim()).apply()

    var geminiKey: String
        get() = secure.get("geminiKey").orEmpty()
        set(v) = secure.put("geminiKey", v.trim())

    var syncBinId: String?
        get() = prefs.getString("syncBinId", null)
        set(v) = prefs.edit().putString("syncBinId", v).apply()

    var lastSyncedAt: String?
        get() = prefs.getString("lastSyncedAt", null)
        set(v) = prefs.edit().putString("lastSyncedAt", v).apply()

    /** Push to JSONBin after every saved search, once a master key is set. */
    var autoSync: Boolean
        get() = prefs.getBoolean("autoSync", true)
        set(v) = prefs.edit().putBoolean("autoSync", v).apply()

    var masterKey: String
        get() = secure.get("masterKey").orEmpty()
        set(v) = secure.put("masterKey", v.trim())

    var accessKey: String
        get() = secure.get("accessKey").orEmpty()
        set(v) = secure.put("accessKey", v.trim())

    /** Forgets the bin and the JSONBin keys. Searches on the phone are kept. */
    fun clearSync() {
        prefs.edit().remove("syncBinId").remove("lastSyncedAt").apply()
        secure.put("masterKey", null)
        secure.put("accessKey", null)
    }

    private fun defaultDeviceName(): String {
        val maker = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty()
        return when {
            model.isBlank() -> "My Android"
            model.startsWith(maker, ignoreCase = true) -> model
            else -> "$maker $model".trim()
        }
    }
}
