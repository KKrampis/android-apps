package io.github.kkrampis.searchvault.model

import org.json.JSONArray
import org.json.JSONObject

/** What the AI Mode page showed when it was read. */
data class PageCapture(
    val state: State,
    val text: String,
    val links: List<Source>,
) {
    enum class State { Ok, Consent, Captcha, SignIn }
}

/**
 * Turns the raw text read from Google's AI Mode page into the answer to save: drops the page's
 * menus, buttons and footer lines, and the question echoed at the top. Kept free of Android code
 * so it can be unit tested.
 */
object PageText {
    /** Short lines Google shows around the answer, never part of it. Compared case-insensitively. */
    private val boilerplate = setOf(
        "ai mode", "all", "images", "videos", "news", "shopping", "short videos", "forums", "web", "books",
        "maps", "flights", "finance", "more", "tools", "sign in", "settings", "search labs", "labs",
        "ask anything", "ask a follow up", "ask a follow-up", "ask about this", "start new search", "new search",
        "ai responses may include mistakes.", "ai responses may include mistakes. learn more", "learn more",
        "show all", "show more", "show less", "share", "copy", "copied", "good response", "bad response",
        "feedback", "send feedback", "thank you", "privacy", "terms", "delete", "dismiss", "close",
        "accessibility links", "skip to main content", "accessibility help", "accessibility feedback",
        "filters and topics", "search results", "ai overview", "listen", "pause", "edit", "regenerate",
        "use precise location", "update location", "help", "google apps", "main menu", "search modes",
    )

    private val noise = listOf(
        Regex("^\\d+ sites?$", RegexOption.IGNORE_CASE),
        Regex("^\\+\\d+$"),
        Regex("^\\d+$"),
        Regex("^[·•|—–-]+$"),
        Regex("^(your|based on your) (location|places?)\\b.*", RegexOption.IGNORE_CASE),
    )

    const val MAX_CHARS = 20_000

    /** Parses the JSON returned by the page script (already unwrapped from evaluateJavascript's string). */
    fun parse(json: String, question: String): PageCapture {
        val o = runCatching { JSONObject(json) }.getOrNull()
            ?: return PageCapture(PageCapture.State.Ok, "", emptyList())
        val state = when (o.optString("state")) {
            "consent" -> PageCapture.State.Consent
            "captcha" -> PageCapture.State.Captcha
            "signin" -> PageCapture.State.SignIn
            else -> PageCapture.State.Ok
        }
        val drop = o.optJSONArray("drop").strings().map { it.trim().lowercase() }.filter { it.isNotEmpty() && it.length <= 60 }.toSet()
        val links = o.optJSONArray("links")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val l = a.optJSONObject(i) ?: return@mapNotNull null
                val url = l.optString("u")
                if (url.isBlank()) null else Source(l.optString("t").trim(), url)
            }
        }.orEmpty().distinctBy { it.url.substringBefore('#') }
        return PageCapture(state, clean(o.optString("text"), question, drop), links)
    }

    fun clean(raw: String, question: String, drop: Set<String> = emptySet()): String {
        val q = question.trim().lowercase()
        val lines = raw.replace("\r\n", "\n").replace(' ', ' ').lines().map { it.trimEnd() }
        val kept = lines.filter { line ->
            val t = line.trim()
            val lower = t.lowercase()
            t.isEmpty() || !(lower in boilerplate || lower in drop || noise.any { it.matches(t) })
        }.toMutableList()
        // The page repeats the question above the answer.
        while (kept.isNotEmpty() && (kept.first().isBlank() || kept.first().trim().lowercase() == q)) kept.removeAt(0)
        return kept.joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
            .take(MAX_CHARS)
    }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { optString(it) }
}
