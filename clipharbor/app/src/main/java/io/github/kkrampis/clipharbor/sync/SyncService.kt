package io.github.kkrampis.clipharbor.sync

import android.util.Base64
import io.github.kkrampis.clipharbor.data.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.DateFormat
import java.util.Date

/** Pushes and pulls the playlists document to a GitHub repository and/or jsonbin.io. */
class SyncService(
    private val http: OkHttpClient,
    private val settings: SyncSettings,
    private val library: LibraryRepository,
) {
    private val json = "application/json; charset=utf-8".toMediaType()

    /** Pushes to every destination that is configured. Returns a short summary. */
    suspend fun pushAll(): String {
        val cfg = settings.load()
        val results = mutableListOf<String>()
        if (cfg.githubReady) results += runCatching { pushGitHub(cfg) }.fold({ it }, { "GitHub: ${it.message}" })
        if (cfg.jsonbinReady) results += runCatching { pushJsonBin(cfg) }.fold({ it }, { "jsonbin: ${it.message}" })
        val summary = if (results.isEmpty()) "Nothing is set up yet" else results.joinToString("\n")
        settings.setLastSync("${stamp()}\n$summary")
        return summary
    }

    suspend fun pushGitHub(cfg: SyncConfig = settings.load()): String = withContext(Dispatchers.IO) {
        require(cfg.githubReady) { "Add a repository (owner/name) and a token first" }
        val body = library.exportJson().toString(2)
        val url = contentsUrl(cfg)
        val sha = http.newCall(github(cfg, "$url?ref=${cfg.githubBranch}").get().build()).execute().use { resp ->
            when (resp.code) {
                200 -> JSONObject(resp.body!!.string()).optString("sha").takeIf { it.isNotBlank() }
                404 -> null
                else -> throw IOException(githubError(resp.code, resp.body?.string()))
            }
        }
        val payload = JSONObject()
            .put("message", "Update playlists from ClipHarbor")
            .put("content", Base64.encodeToString(body.toByteArray(), Base64.NO_WRAP))
            .put("branch", cfg.githubBranch)
        if (sha != null) payload.put("sha", sha)
        http.newCall(github(cfg, url).put(payload.toString().toRequestBody(json)).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException(githubError(resp.code, resp.body?.string()))
        }
        "GitHub: pushed to ${cfg.githubRepo}/${cfg.githubPath}"
    }

    suspend fun pullGitHub(cfg: SyncConfig = settings.load()): String {
        require(cfg.githubReady) { "Add a repository (owner/name) and a token first" }
        val doc = withContext(Dispatchers.IO) {
            val req = github(cfg, "${contentsUrl(cfg)}?ref=${cfg.githubBranch}").get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException(githubError(resp.code, resp.body?.string()))
                val content = JSONObject(resp.body!!.string()).getString("content")
                JSONObject(String(Base64.decode(content, Base64.DEFAULT)))
            }
        }
        val added = library.importJson(doc)
        return "GitHub: imported, $added new playlist entries".also { settings.setLastSync("${stamp()}\n$it") }
    }

    suspend fun pushJsonBin(cfg: SyncConfig = settings.load()): String = withContext(Dispatchers.IO) {
        require(cfg.jsonbinReady) { "Add a jsonbin.io key first" }
        val body = library.exportJson().toString().toRequestBody(json)
        if (cfg.jsonbinBinId.isBlank()) {
            val req = jsonbin(cfg, "https://api.jsonbin.io/v3/b")
                .header("X-Bin-Name", "clipharbor-playlists")
                .header("X-Bin-Private", "true")
                .post(body)
                .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string()
                if (!resp.isSuccessful) throw IOException(jsonbinError(resp.code, text))
                val id = JSONObject(text ?: "{}").optJSONObject("metadata")?.optString("id")
                    ?: throw IOException("jsonbin.io did not return a bin id")
                settings.setJsonbinBinId(id)
                "jsonbin: created bin $id"
            }
        } else {
            val req = jsonbin(cfg, "https://api.jsonbin.io/v3/b/${cfg.jsonbinBinId}").put(body).build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException(jsonbinError(resp.code, resp.body?.string()))
            }
            "jsonbin: updated bin ${cfg.jsonbinBinId}"
        }
    }

    suspend fun pullJsonBin(cfg: SyncConfig = settings.load()): String {
        require(cfg.jsonbinReady && cfg.jsonbinBinId.isNotBlank()) { "Add a jsonbin.io key and bin id first" }
        val doc = withContext(Dispatchers.IO) {
            val req = jsonbin(cfg, "https://api.jsonbin.io/v3/b/${cfg.jsonbinBinId}/latest").get().build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string()
                if (!resp.isSuccessful) throw IOException(jsonbinError(resp.code, text))
                JSONObject(text ?: "{}").getJSONObject("record")
            }
        }
        val added = library.importJson(doc)
        return "jsonbin: imported, $added new playlist entries".also { settings.setLastSync("${stamp()}\n$it") }
    }

    private fun contentsUrl(cfg: SyncConfig) =
        "https://api.github.com/repos/${cfg.githubRepo}/contents/${cfg.githubPath}"

    private fun github(cfg: SyncConfig, url: String) = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer ${cfg.githubToken}")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("User-Agent", "ClipHarbor-Android")

    private fun jsonbin(cfg: SyncConfig, url: String) = Request.Builder()
        .url(url)
        .header("X-Master-Key", cfg.jsonbinKey)
        .header("Content-Type", "application/json")

    private fun githubError(code: Int, body: String?): String {
        val msg = runCatching { JSONObject(body ?: "").optString("message") }.getOrNull().orEmpty()
        return when (code) {
            401 -> "the token was rejected (401)"
            403 -> "the token cannot write to this repository (403) $msg".trim()
            404 -> "repository, branch or file not found (404). Check owner/name and that the token can see it"
            else -> "HTTP $code $msg".trim()
        }
    }

    private fun jsonbinError(code: Int, body: String?): String {
        val msg = runCatching { JSONObject(body ?: "").optString("message") }.getOrNull().orEmpty()
        return "HTTP $code $msg".trim()
    }

    private fun stamp() = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date())
}
