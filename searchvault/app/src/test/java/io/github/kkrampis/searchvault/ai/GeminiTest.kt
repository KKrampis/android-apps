package io.github.kkrampis.searchvault.ai

import io.github.kkrampis.searchvault.model.Engine
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiTest {
    @Test
    fun parsesGroundedAnswer() {
        val response = """
        {
          "candidates": [{
            "content": {"role": "model", "parts": [{"text": "Paris is the capital. It is in France."}]},
            "finishReason": "STOP",
            "groundingMetadata": {
              "webSearchQueries": ["capital of france"],
              "groundingChunks": [
                {"web": {"uri": "https://example.com/a", "title": "example.com"}},
                {"web": {"uri": "https://example.org/b", "title": "example.org"}}
              ],
              "groundingSupports": [
                {"segment": {"startIndex": 0, "endIndex": 21, "text": "Paris is the capital."}, "groundingChunkIndices": [0, 1]},
                {"segment": {"startIndex": 23, "endIndex": 38, "text": "It is in France."}, "groundingChunkIndices": [1]}
              ]
            }
          }]
        }
        """.trimIndent()
        val turn = Gemini.parse(JSONObject(response), "capital of france?", "gemini-flash-latest")
        assertEquals("Paris is the capital. [1, 2] It is in France. [2]", turn.answer)
        assertEquals(2, turn.sources.size)
        assertEquals("https://example.com/a", turn.sources[0].url)
        assertEquals(listOf("capital of france"), turn.queries)
        assertEquals(Engine.Gemini, turn.engine)
    }

    @Test
    fun citationOffsetsAreUtf8Bytes() {
        // "café" is 5 bytes in UTF-8, so the sentence ends at byte 15, not char 14.
        val text = "I like a café. Next."
        val out = Gemini.addCitations(text, listOf(Gemini.Companion.Citation(15, listOf(0))))
        assertEquals("I like a café. [1] Next.", out)
    }

    @Test
    fun stripsCitationsForContext() {
        assertEquals("A. B.", Gemini.stripCitations("A. [1, 2] B. [3]"))
    }

    @Test(expected = Gemini.GeminiException::class)
    fun reportsBlockedPrompt() {
        Gemini.parse(JSONObject("""{"promptFeedback": {"blockReason": "SAFETY"}}"""), "q", "m")
    }
}
