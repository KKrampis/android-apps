package io.github.kkrampis.searchvault.data

import android.content.Context
import io.github.kkrampis.searchvault.model.Archive
import io.github.kkrampis.searchvault.model.ArchiveJson
import java.io.File

/** Every saved search, kept as one SearchVault JSON file in the app's private storage. */
class SearchStore(context: Context) {
    private val file = File(context.filesDir, "searches.json")

    fun load(): Archive {
        if (!file.exists()) return Archive()
        return runCatching { ArchiveJson.parse(file.readText()) }.getOrElse { Archive() }
    }

    @Synchronized
    fun save(archive: Archive) {
        val tmp = File(file.parentFile, "searches.json.tmp")
        tmp.writeText(ArchiveJson.toJsonString(archive))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }
}
