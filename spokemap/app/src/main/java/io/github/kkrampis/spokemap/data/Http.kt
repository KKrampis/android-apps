package io.github.kkrampis.spokemap.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * One HTTP client for every data source. There are no accounts, keys or cookies, and the only
 * thing identifying the app is a generic User-Agent (Overpass asks clients to send one).
 */
object Http {
    private const val USER_AGENT = "SpokeMap/1.0 (Android; open-data bike map)"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

    suspend fun get(url: String): String = execute(Request.Builder().url(url).build())

    suspend fun postForm(url: String, fields: Map<String, String>): String {
        val body = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        return execute(Request.Builder().url(url).post(body).build())
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} from ${request.url.host}")
            }
            response.body?.string() ?: throw IOException("Empty response from ${request.url.host}")
        }
    }
}
