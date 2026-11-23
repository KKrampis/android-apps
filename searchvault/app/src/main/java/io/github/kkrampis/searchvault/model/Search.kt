package io.github.kkrampis.searchvault.model

import java.time.Instant
import java.util.UUID

/** Which service answered a turn. */
enum class Engine(val id: String, val label: String) {
    /** Google's own AI Mode page, opened anonymously in the app; the answer is read from the page. */
    AiMode("aimode", "Google AI Mode"),

    /** Gemini API with the Google Search tool: an AI answer grounded in live Google results. Needs an API key. */
    Gemini("gemini", "Gemini + Google Search");

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: Gemini
    }
}

/** A web page the answer was based on. */
data class Source(val title: String, val url: String)

/** One question and its answer. A search starts with one turn and grows with follow-ups. */
data class Turn(
    val question: String,
    val answer: String,
    val sources: List<Source> = emptyList(),
    /** The Google queries the model actually ran, shown as search suggestions. */
    val queries: List<String> = emptyList(),
    val engine: Engine = Engine.Gemini,
    val model: String = "",
    val at: String = Instant.now().toString(),
)

/** A saved search: the first question, its follow-ups and everything they returned. */
data class Search(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val turns: List<Turn> = emptyList(),
    val starred: Boolean = false,
    val note: String = "",
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = createdAt,
) {
    val lastAnswer: String get() = turns.lastOrNull()?.answer.orEmpty()
}

/** Remembers a deleted search so a pull from another device does not bring it back. */
data class Tombstone(val id: String, val at: String)

/** Everything the app saves: the local file and the JSONBin payload have this same shape. */
data class Archive(
    val searches: List<Search> = emptyList(),
    val deleted: List<Tombstone> = emptyList(),
    val device: String = "",
    val exportedAt: String = "",
) {
    /** Newest activity first. */
    fun sorted() = copy(searches = searches.sortedByDescending { it.updatedAt })

    fun upsert(search: Search) = copy(
        searches = listOf(search) + searches.filterNot { it.id == search.id },
    ).sorted()

    fun remove(id: String, at: String = Instant.now().toString()) = copy(
        searches = searches.filterNot { it.id == id },
        deleted = (deleted.filterNot { it.id == id } + Tombstone(id, at)).takeLast(MAX_TOMBSTONES),
    )

    /**
     * Combines this archive with one pulled from JSONBin. For a search present on both sides the
     * newer [Search.updatedAt] wins; a search deleted on either side after its last change stays deleted.
     */
    fun merge(other: Archive): MergeResult {
        val tombs = (deleted + other.deleted)
            .groupBy { it.id }
            .mapValues { (_, list) -> list.maxBy { it.at } }
        val mine = searches.associateBy { it.id }
        var added = 0
        var updated = 0
        val merged = mine.toMutableMap()
        for (theirs in other.searches) {
            val local = mine[theirs.id]
            when {
                local == null -> { merged[theirs.id] = theirs; added++ }
                theirs.updatedAt > local.updatedAt -> { merged[theirs.id] = theirs; updated++ }
            }
        }
        var removed = 0
        for ((id, tomb) in tombs) {
            val s = merged[id] ?: continue
            if (tomb.at >= s.updatedAt) {
                merged.remove(id)
                if (mine.containsKey(id)) removed++ else added--
            }
        }
        val archive = copy(
            searches = merged.values.toList(),
            deleted = tombs.values.sortedBy { it.at }.takeLast(MAX_TOMBSTONES),
        ).sorted()
        return MergeResult(archive, added, updated, removed)
    }

    companion object {
        const val MAX_TOMBSTONES = 500
    }
}

data class MergeResult(val archive: Archive, val added: Int, val updated: Int, val removed: Int)
