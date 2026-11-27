package io.github.kkrampis.stockvault.data

import android.content.Context
import io.github.kkrampis.stockvault.model.Library
import io.github.kkrampis.stockvault.model.LibraryJson
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Folders, saved stocks and portfolios, kept as one StockVault JSON file in the app's private storage. */
class LibraryStore(context: Context) {
    private val file = File(context.filesDir, "library.json")

    fun load(): Library {
        if (!file.exists()) return Library()
        return runCatching { LibraryJson.fromJson(JSONObject(file.readText())) }.getOrElse { Library() }
    }

    @Synchronized
    fun save(lib: Library) = writeAtomic(file, LibraryJson.toJsonString(lib))
}

/** Recently opened symbols, newest first, for the Markets tab. */
class RecentStore(context: Context) {
    private val prefs = context.getSharedPreferences("recent", Context.MODE_PRIVATE)

    fun load(): List<Pair<String, String>> = runCatching {
        val a = JSONArray(prefs.getString("items", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> o.getString("s") to o.optString("n") } }
    }.getOrDefault(emptyList())

    fun add(symbol: String, name: String): List<Pair<String, String>> {
        val list = (listOf(symbol to name) + load().filter { it.first != symbol }).take(12)
        prefs.edit().putString("items", JSONArray(list.map { JSONObject().put("s", it.first).put("n", it.second) }).toString()).apply()
        return list
    }

    fun clear() = prefs.edit().remove("items").apply()
}

internal fun writeAtomic(file: File, text: String) {
    val tmp = File(file.parentFile, file.name + ".tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(file)) {
        file.writeText(text)
        tmp.delete()
    }
}
