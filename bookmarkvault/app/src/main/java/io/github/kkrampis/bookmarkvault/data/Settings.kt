package io.github.kkrampis.bookmarkvault.data

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/** One push, as listed under Sync history (the extension keeps the last 10). */
data class PushRecord(val binId: String, val pushedAt: String, val device: String, val count: Int)

/**
 * Plain settings live in shared preferences; the two JSONBin keys go through [SecureStore],
 * encrypted with a key held in the Android Keystore.
 */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val secure = SecureStore(context)

    var deviceName: String
        get() = prefs.getString("deviceName", null) ?: defaultDeviceName()
        set(v) = prefs.edit().putString("deviceName", v.trim()).apply()

    var syncBinId: String?
        get() = prefs.getString("syncBinId", null)
        set(v) = prefs.edit().putString("syncBinId", v).apply()

    var lastSyncedAt: String?
        get() = prefs.getString("lastSyncedAt", null)
        set(v) = prefs.edit().putString("lastSyncedAt", v).apply()

    var masterKey: String
        get() = secure.get("masterKey").orEmpty()
        set(v) = secure.put("masterKey", v.trim())

    var accessKey: String
        get() = secure.get("accessKey").orEmpty()
        set(v) = secure.put("accessKey", v.trim())

    var history: List<PushRecord>
        get() = runCatching {
            val arr = JSONArray(prefs.getString("syncHistory", "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PushRecord(o.optString("binId"), o.optString("pushedAt"), o.optString("device"), o.optInt("count"))
            }
        }.getOrDefault(emptyList())
        set(v) {
            val arr = JSONArray()
            v.take(10).forEach {
                arr.put(JSONObject().put("binId", it.binId).put("pushedAt", it.pushedAt).put("device", it.device).put("count", it.count))
            }
            prefs.edit().putString("syncHistory", arr.toString()).apply()
        }

    /** Same as the extension's "Clear sync data": forgets the bin, keys and history. */
    fun clearSync() {
        prefs.edit().remove("syncBinId").remove("lastSyncedAt").remove("syncHistory").apply()
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
