package io.github.kkrampis.stockvault.data

import android.content.Context
import android.os.Build
import io.github.kkrampis.stockvault.ai.ClaudeApi

/**
 * Plain settings live in shared preferences; the Anthropic API key and the JSONBin keys go
 * through [SecureStore], encrypted with a key held in the Android Keystore.
 */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val secure = SecureStore(context)

    var deviceName: String
        get() = prefs.getString("deviceName", null)?.takeIf { it.isNotBlank() } ?: defaultDeviceName()
        set(v) = prefs.edit().putString("deviceName", v.trim()).apply()

    /** Wipe Google's cookies and site data before every AI Mode search. */
    var privateMode: Boolean
        get() = prefs.getBoolean("privateMode", true)
        set(v) = prefs.edit().putBoolean("privateMode", v).apply()

    /** Symbols on the Markets tab, in order. */
    var marketSymbols: List<String>
        get() = prefs.getString("marketSymbols", null)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: DEFAULT_MARKETS
        set(v) = prefs.edit().putString("marketSymbols", v.joinToString(",")).apply()

    var benchmark: String
        get() = prefs.getString("benchmark", null)?.takeIf { it.isNotBlank() } ?: "SPY"
        set(v) = prefs.edit().putString("benchmark", v.trim().uppercase()).apply()

    var claudeKey: String
        get() = secure.get("claudeKey").orEmpty()
        set(v) = secure.put("claudeKey", v.trim())

    var claudeModel: String
        get() = prefs.getString("claudeModel", null)?.takeIf { it.isNotBlank() } ?: ClaudeApi.DEFAULT_MODEL
        set(v) = prefs.edit().putString("claudeModel", v.trim()).apply()

    var syncBinId: String?
        get() = prefs.getString("syncBinId", null)
        set(v) = prefs.edit().putString("syncBinId", v).apply()

    var lastSyncedAt: String?
        get() = prefs.getString("lastSyncedAt", null)
        set(v) = prefs.edit().putString("lastSyncedAt", v).apply()

    /** Push to JSONBin after every change to folders or portfolios, once a master key is set. */
    var autoSync: Boolean
        get() = prefs.getBoolean("autoSync", true)
        set(v) = prefs.edit().putBoolean("autoSync", v).apply()

    var masterKey: String
        get() = secure.get("masterKey").orEmpty()
        set(v) = secure.put("masterKey", v.trim())

    var accessKey: String
        get() = secure.get("accessKey").orEmpty()
        set(v) = secure.put("accessKey", v.trim())

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

    companion object {
        val DEFAULT_MARKETS = listOf("^GSPC", "^DJI", "^IXIC", "^RUT", "^VIX", "^TNX", "GC=F", "CL=F", "EURUSD=X", "BTC-USD")
    }
}
