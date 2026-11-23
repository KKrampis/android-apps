package io.github.kkrampis.bookmarkvault.sync

import io.github.kkrampis.bookmarkvault.model.Vault
import io.github.kkrampis.bookmarkvault.model.VaultJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * JSONBin.io v3, used exactly the way the Chrome extension uses it, so a bin pushed from Chrome
 * can be pulled here and the other way round.
 *
 * - Push: `POST /v3/b` the first time (creates a private bin named `BookmarkVault-<device>`),
 *   then `PUT /v3/b/<id>`. The body is `{ "bookmarkvault": <vault> }`. Needs the master key.
 * - Pull: `GET /v3/b/<id>` with `X-Bin-Meta: false`, using the master key, or else the access key,
 *   or no key at all for a public bin.
 */
class JsonBin(private val client: OkHttpClient = defaultClient) {

    class SyncException(message: String) : Exception(message)

    data class PushResult(val binId: String)

    suspend fun push(vault: Vault, binId: String?, masterKey: String): PushResult = withContext(Dispatchers.IO) {
        if (masterKey.isBlank()) throw SyncException("Master Key required. Add it in the Sync tab.")
        val isUpdate = !binId.isNullOrBlank()
        val body = JSONObject().put("bookmarkvault", VaultJson.toJson(vault)).toString()
            .toRequestBody("application/json".toMediaType())
        val builder = Request.Builder()
            .url(if (isUpdate) "$BASE/$binId" else BASE)
            .header("Content-Type", "application/json")
            .header("X-Master-Key", masterKey.trim())
        if (isUpdate) {
            builder.put(body)
        } else {
            builder.header("X-Bin-Name", "BookmarkVault-${vault.device}")
            builder.header("X-Bin-Private", "true")
            builder.post(body)
        }
        client.newCall(builder.build()).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw SyncException("Push ${res.code}: ${text.ifBlank { res.message }}")
            val newId = runCatching { JSONObject(text).optJSONObject("metadata")?.optString("id") }.getOrNull()
            PushResult(newId?.takeIf { it.isNotBlank() } ?: binId.orEmpty())
        }
    }

    suspend fun pull(binId: String, masterKey: String?, accessKey: String?): Vault = withContext(Dispatchers.IO) {
        if (binId.isBlank()) throw SyncException("No Sync ID provided.")
        val builder = Request.Builder().url("$BASE/${binId.trim()}").header("X-Bin-Meta", "false")
        when {
            !masterKey.isNullOrBlank() -> builder.header("X-Master-Key", masterKey.trim())
            !accessKey.isNullOrBlank() -> builder.header("X-Access-Key", accessKey.trim())
        }
        client.newCall(builder.get().build()).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw SyncException("Pull ${res.code}: ${text.ifBlank { res.message }}")
            try {
                VaultJson.parse(text)
            } catch (e: VaultJson.FormatException) {
                throw SyncException("Response did not contain bookmark data. Check the Bin ID.")
            }
        }
    }

    companion object {
        const val BASE = "https://api.jsonbin.io/v3/b"
        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
