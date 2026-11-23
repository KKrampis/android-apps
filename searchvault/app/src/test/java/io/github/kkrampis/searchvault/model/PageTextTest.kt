package io.github.kkrampis.searchvault.model

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTextTest {
    private val page = """
        AI Mode
        All
        Images
        Videos
        why is the sky blue

        The sky looks blue because of **Rayleigh scattering**.

        Shorter blue wavelengths scatter more than red.
        3 sites
        +2
        Show all
        AI responses may include mistakes. Learn more
        Thumbs up
        Ask anything
    """.trimIndent()

    @Test
    fun keepsAnswerAndDropsPageChrome() {
        val text = PageText.clean(page, "why is the sky blue", setOf("thumbs up"))
        assertEquals(
            "The sky looks blue because of **Rayleigh scattering**.\n\nShorter blue wavelengths scatter more than red.",
            text,
        )
    }

    @Test
    fun parsesCaptureJson() {
        val json = JSONObject()
            .put("state", "ok")
            .put("text", page)
            .put("drop", JSONArray(listOf("Thumbs up")))
            .put(
                "links",
                JSONArray()
                    .put(JSONObject().put("t", "Rayleigh scattering - Wikipedia").put("u", "https://en.wikipedia.org/wiki/Rayleigh_scattering"))
                    .put(JSONObject().put("t", "dup").put("u", "https://en.wikipedia.org/wiki/Rayleigh_scattering#x"))
                    .put(JSONObject().put("t", "no url").put("u", "")),
            )
            .toString()
        val c = PageText.parse(json, "why is the sky blue")
        assertEquals(PageCapture.State.Ok, c.state)
        assertEquals(1, c.links.size)
        assertTrue(c.text.startsWith("The sky looks blue"))
        assertFalse(c.text.contains("Thumbs up"))
    }

    @Test
    fun reportsConsentAndCaptcha() {
        assertEquals(PageCapture.State.Consent, PageText.parse("""{"state":"consent","text":""}""", "q").state)
        assertEquals(PageCapture.State.Captcha, PageText.parse("""{"state":"captcha","text":""}""", "q").state)
        assertEquals("", PageText.parse("not json", "q").text)
    }
}
