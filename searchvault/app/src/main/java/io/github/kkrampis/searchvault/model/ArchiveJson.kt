package io.github.kkrampis.searchvault.model

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Reads and writes the SearchVault JSON format. The same document is the local save file,
 * the export file and, wrapped as `{ "searchvault": … }`, the JSONBin payload.
 */
object ArchiveJson {
    const val VERSION = 1
    const val ROOT_KEY = "searchvault"

    class FormatException(message: String) : Exception(message)

    fun toJson(a: Archive): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("app", "SearchVault")
        .put("device", a.device)
        .put("exportedAt", a.exportedAt)
        .put("searches", JSONArray().apply { a.searches.forEach { put(searchToJson(it)) } })
        .put("deleted", JSONArray().apply { a.deleted.forEach { put(JSONObject().put("id", it.id).put("at", it.at)) } })

    fun toJsonString(a: Archive): String = toJson(a).toString(2)

    /** Accepts the bare document or the JSONBin wrapper (with or without JSONBin's `record`/`metadata`). */
    fun parse(text: String): Archive {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw FormatException("Not valid JSON")
        }
        val doc = root.optJSONObject(ROOT_KEY)
            ?: root.optJSONObject("record")?.let { it.optJSONObject(ROOT_KEY) ?: it }
            ?: root
        val arr = doc.optJSONArray("searches")
            ?: throw FormatException("No SearchVault data found")
        return Archive(
            searches = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::searchFromJson) },
            deleted = doc.optJSONArray("deleted")?.let { d ->
                (0 until d.length()).mapNotNull { i ->
                    d.optJSONObject(i)?.let { o -> o.optString("id").takeIf { it.isNotBlank() }?.let { Tombstone(it, o.optString("at")) } }
                }
            }.orEmpty(),
            device = doc.optString("device"),
            exportedAt = doc.optString("exportedAt"),
        ).sorted()
    }

    private fun searchToJson(s: Search) = JSONObject()
        .put("id", s.id)
        .put("title", s.title)
        .put("starred", s.starred)
        .put("note", s.note)
        .put("createdAt", s.createdAt)
        .put("updatedAt", s.updatedAt)
        .put("turns", JSONArray().apply { s.turns.forEach { put(turnToJson(it)) } })

    private fun turnToJson(t: Turn) = JSONObject()
        .put("question", t.question)
        .put("answer", t.answer)
        .put("engine", t.engine.id)
        .put("model", t.model)
        .put("at", t.at)
        .put("queries", JSONArray(t.queries))
        .put("sources", JSONArray().apply { t.sources.forEach { put(JSONObject().put("title", it.title).put("url", it.url)) } })

    private fun searchFromJson(o: JSONObject): Search? {
        val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
        val turns = o.optJSONArray("turns")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::turnFromJson) }
        }.orEmpty()
        val created = o.optString("createdAt")
        return Search(
            id = id,
            title = o.optString("title").ifBlank { turns.firstOrNull()?.question.orEmpty() },
            turns = turns,
            starred = o.optBoolean("starred"),
            note = o.optString("note"),
            createdAt = created,
            updatedAt = o.optString("updatedAt").ifBlank { created },
        )
    }

    private fun turnFromJson(o: JSONObject) = Turn(
        question = o.optString("question"),
        answer = o.optString("answer"),
        engine = Engine.from(o.optString("engine")),
        model = o.optString("model"),
        at = o.optString("at"),
        queries = o.optJSONArray("queries")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(),
        sources = o.optJSONArray("sources")?.let { a ->
            (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Source(it.optString("title"), it.optString("url")) } }
        }.orEmpty(),
    )

    /** Plain Markdown export of one search, for sharing. */
    fun toMarkdown(s: Search): String = buildString {
        append("# ").append(s.title).append("\n\n")
        s.turns.forEachIndexed { i, t ->
            if (i > 0) append("\n---\n\n")
            append("**Q:** ").append(t.question).append("\n\n")
            append(t.answer.trim()).append("\n")
            if (t.sources.isNotEmpty()) {
                append("\nSources:\n")
                t.sources.forEachIndexed { n, src -> append("${n + 1}. [").append(src.title.ifBlank { src.url }).append("](").append(src.url).append(")\n") }
            }
        }
    }
}
