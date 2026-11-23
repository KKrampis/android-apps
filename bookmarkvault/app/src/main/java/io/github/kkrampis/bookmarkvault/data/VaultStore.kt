package io.github.kkrampis.bookmarkvault.data

import android.content.Context
import io.github.kkrampis.bookmarkvault.model.Vault
import io.github.kkrampis.bookmarkvault.model.VaultJson
import java.io.File

/**
 * The phone's bookmark library, saved as one BookmarkVault JSON file in the app's private storage.
 * Android has no shared bookmark store that apps can write to, so the app keeps its own.
 */
class VaultStore(context: Context) {
    private val file = File(context.filesDir, "bookmarks.json")

    fun load(device: String): Vault {
        if (!file.exists()) return Vault.empty(device)
        return runCatching { VaultJson.parse(file.readText()) }.getOrElse { Vault.empty(device) }
            .let { if (it.roots.isEmpty()) Vault.empty(device) else it }
    }

    fun save(vault: Vault) {
        val tmp = File(file.parentFile, "bookmarks.json.tmp")
        tmp.writeText(VaultJson.toJsonString(vault))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }
}
