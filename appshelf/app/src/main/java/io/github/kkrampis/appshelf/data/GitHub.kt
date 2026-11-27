package io.github.kkrampis.appshelf.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

class GitHubException(message: String) : IOException(message)

/** What reading one repository gave: its releases, and whether they came from the offline copy. */
data class Fetched(val releases: List<Release>, val fromCache: Boolean, val warning: String? = null)

/**
 * Reads releases from the GitHub REST API. Without a token GitHub allows 60 requests an hour per
 * IP address, so every answer is kept on the phone with its ETag: asking again with the ETag
 * costs nothing when nothing changed (GitHub answers 304 and does not count it), and the copy is
 * used when the phone is offline.
 */
class GitHub(private val cacheDir: File, private val token: () -> String) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun releases(source: Source, pages: Int = 3): Fetched {
        val all = mutableListOf<Release>()
        var anyFromCache = false
        var warning: String? = null
        for (page in 1..pages) {
            val url = "https://api.github.com/repos/${source.owner}/${source.repo}/releases?per_page=100&page=$page"
            val (body, cached, warn) = getCached(url)
            anyFromCache = anyFromCache || cached
            if (warn != null) warning = warn
            val list = GitHubParse.releases(body)
            all += list
            if (list.size < 100) break
        }
        return Fetched(all, anyFromCache, warning)
    }

    private data class Body(val text: String, val fromCache: Boolean, val warning: String?)

    private fun getCached(url: String): Body {
        cacheDir.mkdirs()
        val file = File(cacheDir, sha1(url + "|" + token().isNotBlank()) + ".json")
        val etagFile = File(file.path + ".etag")
        val cachedBody = file.takeIf { it.exists() }?.readText()
        val etag = etagFile.takeIf { it.exists() && cachedBody != null }?.readText()

        val request = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .apply { if (etag != null) header("If-None-Match", etag) }
            .apply { token().takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") } }
            .build()

        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            if (cachedBody != null) return Body(cachedBody, true, "Offline, showing what was saved last time.")
            throw GitHubException("Couldn't reach GitHub: ${e.message ?: e.javaClass.simpleName}")
        }
        response.use { r ->
            when {
                r.code == 304 && cachedBody != null -> return Body(cachedBody, false, null)
                r.isSuccessful -> {
                    val text = r.body?.string().orEmpty()
                    file.writeText(text)
                    r.header("ETag")?.let { etagFile.writeText(it) } ?: etagFile.delete()
                    return Body(text, false, null)
                }
                else -> {
                    val message = errorMessage(r)
                    if (cachedBody != null && r.code != 404) return Body(cachedBody, true, "$message Showing what was saved last time.")
                    throw GitHubException(message)
                }
            }
        }
    }

    private fun errorMessage(r: Response): String {
        val remaining = r.header("X-RateLimit-Remaining")
        return when {
            (r.code == 403 || r.code == 429) && remaining == "0" -> {
                val reset = r.header("X-RateLimit-Reset")?.toLongOrNull()
                val at = reset?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it * 1000)) }
                "GitHub's hourly limit for requests without a token is used up" +
                    (at?.let { ", it resets at $it" } ?: "") + ". Adding a token in Settings raises it."
            }
            r.code == 404 -> "Repository not found. If it is private, add a GitHub token in Settings."
            r.code == 401 -> "GitHub didn't accept the token in Settings."
            else -> "GitHub answered ${r.code}."
        }
    }

    /** Builds the request that downloads an APK: the API link with a token, the public link without. */
    fun downloadRequest(asset: Asset): Request {
        val t = token()
        return if (t.isNotBlank() && asset.apiUrl.isNotBlank()) {
            // OkHttp drops the Authorization header when GitHub redirects to its file storage.
            Request.Builder().url(asset.apiUrl)
                .header("Accept", "application/octet-stream")
                .header("Authorization", "Bearer $t")
                .build()
        } else {
            Request.Builder().url(asset.downloadUrl).build()
        }
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

object GitHubParse {
    fun releases(json: String): List<Release> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val assets = o.optJSONArray("assets") ?: JSONArray()
            Release(
                tag = o.optString("tag_name"),
                name = o.optString("name").takeUnless { it == "null" }.orEmpty(),
                body = o.optString("body").takeUnless { it == "null" }.orEmpty(),
                publishedAt = o.optString("published_at").takeUnless { it == "null" }
                    ?: o.optString("created_at"),
                prerelease = o.optBoolean("prerelease"),
                draft = o.optBoolean("draft"),
                htmlUrl = o.optString("html_url"),
                assets = (0 until assets.length()).map { j ->
                    val a = assets.getJSONObject(j)
                    Asset(
                        name = a.optString("name"),
                        downloadUrl = a.optString("browser_download_url"),
                        apiUrl = a.optString("url"),
                        size = a.optLong("size"),
                        downloads = a.optInt("download_count"),
                    )
                },
            )
        }
    }
}
