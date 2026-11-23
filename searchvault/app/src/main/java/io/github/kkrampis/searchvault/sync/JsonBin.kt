package io.github.kkrampis.searchvault.sync

import io.github.kkrampis.searchvault.model.Archive
import io.github.kkrampis.searchvault.model.ArchiveJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * JSONBin.io v3, used the same way as in BookmarkVault (same keys work for both apps):
 *
 * - Push: `POST /v3/b` the first time (creates a private bin named `SearchVault-<device>`),
 *   then `PUT /v3/b/<id>`. The body is `{ "searchvault": <archive> }`. Needs the master key.
 * - Pull: `GET /v3/b/<id>` with `X-Bin-Meta: false`, using the master key, or else the access key,
 *   or no key at all for a public bin.
 */
class JsonBin(private val client: OkHttpClient = defaultClient) {

    class SyncException(message: String) : Exception(message)

    suspend fun push(archive: Archive, binId: String?, masterKey: String): String = withContext(Dispatchers.IO) {
        if (masterKey.isBlank()) throw SyncException("Master Key required. Add it in the Sync tab.")
        val isUpdate = !binId.isNullOrBlank()
        val body = JSONObject().put(ArchiveJson.ROOT_KEY, ArchiveJson.toJson(archive)).toString()
            .toRequestBody("application/json".toMediaType())
        val builder = Request.Builder()
            .url(if (isUpdate) "$BASE/${binId!!.trim()}" else BASE)
            .header("Content-Type", "application/json")
            .header("X-Master-Key", masterKey.trim())
        if (isUpdate) {
            builder.put(body)
        } else {
            builder.header("X-Bin-Name", "SearchVault-${archive.device}".take(128))
            builder.header("X-Bin-Private", "true")
            builder.post(body)
        }
        client.newCall(builder.build()).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw SyncException("Push ${res.code}: ${errorText(text, res.message)}")
            val newId = runCatching { JSONObject(text).optJSONObject("metadata")?.optString("id") }.getOrNull()
            newId?.takeIf { it.isNotBlank() } ?: binId.orEmpty()
        }
    }

    suspend fun pull(binId: String, masterKey: String?, accessKey: String?): Archive = withContext(Dispatchers.IO) {
        if (binId.isBlank()) throw SyncException("No Sync ID provided.")
        val builder = Request.Builder().url("$BASE/${binId.trim()}").header("X-Bin-Meta", "false")
        when {
            !masterKey.isNullOrBlank() -> builder.header("X-Master-Key", masterKey.trim())
            !accessKey.isNullOrBlank() -> builder.header("X-Access-Key", accessKey.trim())
        }
        client.newCall(builder.get().build()).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw SyncException("Pull ${res.code}: ${errorText(text, res.message)}")
            try {
                ArchiveJson.parse(text)
            } catch (e: ArchiveJson.FormatException) {
                throw SyncException("That bin has no SearchVault data. Check the Sync ID.")
            }
        }
    }

    private fun errorText(body: String, fallback: String) =
        runCatching { JSONObject(body).optString("message") }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: body.ifBlank { fallback }.take(300)

    companion object {
        const val BASE = "https://api.jsonbin.io/v3/b"
        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
