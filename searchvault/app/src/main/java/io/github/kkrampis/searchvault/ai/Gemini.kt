package io.github.kkrampis.searchvault.ai

import io.github.kkrampis.searchvault.model.Engine
import io.github.kkrampis.searchvault.model.Source
import io.github.kkrampis.searchvault.model.Turn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Google AI search through the Gemini API with the built-in Google Search tool: Gemini runs real
 * Google searches, answers from what it finds and returns the pages it used. Earlier turns are
 * sent along, so follow-up questions keep their context, as in Google's AI Mode.
 */
class Gemini(private val client: OkHttpClient = defaultClient) {

    class GeminiException(message: String) : Exception(message)

    suspend fun ask(apiKey: String, model: String, history: List<Turn>, question: String): Turn = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw GeminiException("Add your Gemini API key in Settings first.")
        val modelId = model.trim().removePrefix("models/").ifBlank { DEFAULT_MODEL }
        val contents = JSONArray()
        history.forEach { t ->
            contents.put(message("user", t.question))
            contents.put(message("model", stripCitations(t.answer)))
        }
        contents.put(message("user", question))
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM))))
            .put("contents", contents)
            .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))

        val request = Request.Builder()
            .url("$BASE/models/$modelId:generateContent")
            .header("x-goog-api-key", apiKey.trim())
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrNull()
                throw GeminiException("Gemini ${res.code}: ${msg ?: text.ifBlank { res.message }.take(300)}")
            }
            parse(JSONObject(text), question, modelId)
        }
    }

    companion object {
        const val BASE = "https://generativelanguage.googleapis.com/v1beta"

        /** Google's alias for its current Flash model, so the app keeps working as models are retired. */
        const val DEFAULT_MODEL = "gemini-flash-latest"

        private const val SYSTEM =
            "You are a search assistant. Search Google for every question and answer from the results. " +
                "Be accurate and concise, lead with the direct answer, and use Markdown headings, bullet lists " +
                "and bold text where they help."

        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()

        private fun message(role: String, text: String) = JSONObject()
            .put("role", role)
            .put("parts", JSONArray().put(JSONObject().put("text", text)))

        private val citationMarks = Regex(" ?\\[\\d+(, ?\\d+)*]")

        /** Removes the [1] markers the app added, before an answer is sent back as context. */
        fun stripCitations(text: String) = text.replace(citationMarks, "")

        /** Turns a generateContent response into a [Turn]. Public for tests. */
        fun parse(json: JSONObject, question: String, model: String): Turn {
            val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
            if (candidate == null) {
                val reason = json.optJSONObject("promptFeedback")?.optString("blockReason")
                throw GeminiException(if (!reason.isNullOrBlank()) "Blocked by Gemini: $reason" else "Gemini returned no answer")
            }
            val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
            val answer = (0 until parts.length())
                .mapNotNull { parts.optJSONObject(it)?.takeUnless { p -> p.optBoolean("thought") }?.optString("text") }
                .joinToString("")
            if (answer.isBlank()) {
                val finish = candidate.optString("finishReason")
                throw GeminiException("Gemini returned an empty answer${if (finish.isNotBlank()) " ($finish)" else ""}")
            }

            val meta = candidate.optJSONObject("groundingMetadata")
            val chunks = meta?.optJSONArray("groundingChunks") ?: JSONArray()
            val sources = (0 until chunks.length()).map { i ->
                val web = chunks.optJSONObject(i)?.optJSONObject("web")
                Source(web?.optString("title").orEmpty(), web?.optString("uri").orEmpty())
            }
            val queries = meta?.optJSONArray("webSearchQueries")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()

            val supports = meta?.optJSONArray("groundingSupports")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val s = arr.optJSONObject(i) ?: return@mapNotNull null
                    val end = s.optJSONObject("segment")?.optInt("endIndex", -1) ?: -1
                    val idx = s.optJSONArray("groundingChunkIndices")?.let { a -> (0 until a.length()).map { a.optInt(it) } }.orEmpty()
                    if (end < 0 || idx.isEmpty()) null else Citation(end, idx)
                }
            }.orEmpty()

            return Turn(
                question = question,
                answer = addCitations(answer, supports),
                sources = sources,
                queries = queries.filter { it.isNotBlank() },
                engine = Engine.Gemini,
                model = model,
            )
        }

        data class Citation(val endByte: Int, val chunkIndices: List<Int>)

        /**
         * Inserts "[1, 2]" markers after each supported sentence. Gemini gives the positions as
         * UTF-8 byte offsets, so the work is done on bytes.
         */
        fun addCitations(text: String, citations: List<Citation>): String {
            if (citations.isEmpty()) return text
            var bytes = text.toByteArray(Charsets.UTF_8)
            citations
                .groupBy { it.endByte }
                .toSortedMap(compareByDescending { it })
                .forEach { (end, list) ->
                    if (end > bytes.size) return@forEach
                    val nums = list.flatMap { it.chunkIndices }.distinct().sorted().joinToString(", ") { (it + 1).toString() }
                    val mark = " [$nums]".toByteArray(Charsets.UTF_8)
                    bytes = bytes.copyOfRange(0, end) + mark + bytes.copyOfRange(end, bytes.size)
                }
            return String(bytes, Charsets.UTF_8)
        }
    }
}
