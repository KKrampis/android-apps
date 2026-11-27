package io.github.kkrampis.stockvault.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TickersTest {
    @Test
    fun readsTickerLineFirst() {
        val text = "Some peers are listed below.\n\nTICKERS: AMD, INTC, \$ARM, TSM"
        assertEquals(listOf("AMD", "INTC", "ARM", "TSM"), Tickers.extract(text))
    }

    @Test
    fun readsExchangePrefixesAndParentheses() {
        val text = """
            1. Advanced Micro Devices (NASDAQ: AMD) makes CPUs and GPUs.
            2. Broadcom (AVGO) sells networking chips to the same AI customers.
            3. **MRVL** Marvell also designs custom AI chips. The CEO said the ETF (SMH) holds it.
            Berkshire Hathaway (BRK.B) is not similar.
        """.trimIndent()
        val t = Tickers.extract(text)
        assertTrue(t.containsAll(listOf("AMD", "AVGO", "MRVL", "SMH", "BRK.B")))
        assertFalse("CEO" in t)
        assertFalse("ETF" in t)
    }

    @Test
    fun ignoresCommonAcronyms() {
        assertTrue(Tickers.extract("Revenue grew (YoY) and the (AI) market (USD)").isEmpty())
    }
}
