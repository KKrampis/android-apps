package io.github.kkrampis.searchvault.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveTest {
    private fun search(id: String, updated: String, title: String = id) = Search(
        id = id,
        title = title,
        turns = listOf(Turn(question = title, answer = "answer $title", at = updated)),
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = updated,
    )

    @Test
    fun jsonRoundTripKeepsEverything() {
        val s = Search(
            id = "a",
            title = "Why is the sky blue?",
            starred = true,
            note = "for class",
            createdAt = "2026-11-01T10:00:00Z",
            updatedAt = "2026-11-01T10:05:00Z",
            turns = listOf(
                Turn(
                    question = "Why is the sky blue?",
                    answer = "Rayleigh **scattering** [1]",
                    sources = listOf(Source("Wikipedia", "https://en.wikipedia.org/wiki/Rayleigh_scattering")),
                    queries = listOf("why is the sky blue"),
                    engine = Engine.Gemini,
                    model = "gemini-flash-latest",
                    at = "2026-11-01T10:00:00Z",
                ),
                Turn(question = "And sunsets?", answer = "", engine = Engine.AiMode, at = "2026-11-01T10:05:00Z"),
            ),
        )
        val archive = Archive(listOf(s), listOf(Tombstone("gone", "2026-10-01T00:00:00Z")), "Pixel", "2026-11-01T11:00:00Z")
        val back = ArchiveJson.parse(ArchiveJson.toJsonString(archive))
        assertEquals(archive, back)
    }

    @Test
    fun parsesJsonBinWrapper() {
        val inner = ArchiveJson.toJson(Archive(listOf(search("a", "2026-01-02T00:00:00Z"))))
        val wrapped = org.json.JSONObject().put("searchvault", inner).toString()
        assertEquals(1, ArchiveJson.parse(wrapped).searches.size)
        val withRecord = org.json.JSONObject().put("record", org.json.JSONObject(wrapped)).toString()
        assertEquals(1, ArchiveJson.parse(withRecord).searches.size)
    }

    @Test(expected = ArchiveJson.FormatException::class)
    fun rejectsOtherJson() {
        ArchiveJson.parse("""{"bookmarkvault": {"roots": []}}""")
    }

    @Test
    fun mergeTakesNewerAndAddsMissing() {
        val local = Archive(listOf(search("a", "2026-01-02T00:00:00Z", "old"), search("b", "2026-01-05T00:00:00Z")))
        val remote = Archive(listOf(search("a", "2026-01-03T00:00:00Z", "new"), search("b", "2026-01-04T00:00:00Z"), search("c", "2026-01-01T00:00:00Z")))
        val r = local.merge(remote)
        assertEquals(1, r.added)
        assertEquals(1, r.updated)
        assertEquals(0, r.removed)
        assertEquals("new", r.archive.searches.first { it.id == "a" }.title)
        assertEquals("2026-01-05T00:00:00Z", r.archive.searches.first { it.id == "b" }.updatedAt)
        // Newest first.
        assertEquals(listOf("b", "a", "c"), r.archive.searches.map { it.id })
    }

    @Test
    fun deletionsPropagateUnlessEditedLater() {
        val local = Archive(listOf(search("a", "2026-01-02T00:00:00Z"), search("b", "2026-01-09T00:00:00Z")))
        val remote = Archive(deleted = listOf(Tombstone("a", "2026-01-03T00:00:00Z"), Tombstone("b", "2026-01-03T00:00:00Z")))
        val r = local.merge(remote)
        assertEquals(listOf("b"), r.archive.searches.map { it.id })
        assertEquals(1, r.removed)

        // A search deleted here is not brought back by a pull of an older copy.
        val here = Archive(listOf(search("x", "2026-01-01T00:00:00Z"))).remove("x", "2026-01-02T00:00:00Z")
        val again = here.merge(Archive(listOf(search("x", "2026-01-01T00:00:00Z"))))
        assertTrue(again.archive.searches.isEmpty())
        assertEquals(0, again.added)
    }

    @Test
    fun upsertMovesToTop() {
        val a = Archive(listOf(search("a", "2026-01-02T00:00:00Z"), search("b", "2026-01-01T00:00:00Z")))
        val updated = a.upsert(search("b", "2026-01-03T00:00:00Z"))
        assertEquals(listOf("b", "a"), updated.searches.map { it.id })
    }

    @Test
    fun markdownExportListsSources() {
        val md = ArchiveJson.toMarkdown(
            Search(title = "T", turns = listOf(Turn("Q?", "A.", listOf(Source("Site", "https://x.example"))))),
        )
        assertTrue(md.contains("**Q:** Q?"))
        assertTrue(md.contains("1. [Site](https://x.example)"))
    }
}
