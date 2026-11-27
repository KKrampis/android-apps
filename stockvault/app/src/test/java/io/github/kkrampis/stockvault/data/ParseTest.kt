package io.github.kkrampis.stockvault.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseTest {
    private val chart = """
    {"chart":{"result":[{"meta":{"currency":"USD","symbol":"AAPL","exchangeName":"NMS","fullExchangeName":"NasdaqGS",
      "instrumentType":"EQUITY","gmtoffset":-18000,"exchangeTimezoneName":"America/New_York","regularMarketPrice":231.5,
      "chartPreviousClose":229.0,"fiftyTwoWeekHigh":260.1,"fiftyTwoWeekLow":164.08,"longName":"Apple Inc."},
      "timestamp":[1732545000,1732631400,1732717800],
      "events":{"dividends":{"1731335400":{"amount":0.25,"date":1731335400}}},
      "indicators":{"quote":[{"open":[228,229,null],"high":[230,232,null],"low":[227,228,null],"close":[229.5,231.5,null],"volume":[100,200,null]}],
      "adjclose":[{"adjclose":[229.0,231.0,null]}]}}],"error":null}}
    """.trimIndent()

    @Test
    fun parsesChart() {
        val c = YahooParse.chart(chart)
        assertEquals("Apple Inc.", c.name)
        assertEquals(2, c.bars.size) // the null bar is skipped
        assertEquals(229.0, c.bars[0].adjClose, 1e-9)
        assertEquals("2024-11-25", c.bars[0].date.toString())
        assertEquals(2.5, c.change!!, 1e-9)
        assertEquals(1, c.dividends.size)
    }

    @Test(expected = DataException::class)
    fun chartErrorIsReported() {
        YahooParse.chart("""{"chart":{"result":null,"error":{"code":"Not Found","description":"No data found, symbol may be delisted"}}}""")
    }

    @Test
    fun parsesSearchAndSummary() {
        val (hits, news) = YahooParse.search(
            """{"quotes":[{"symbol":"AAPL","shortname":"Apple Inc.","exchange":"NMS","exchDisp":"NASDAQ","typeDisp":"Equity","sector":"Technology"}],
               "news":[{"title":"Apple news","publisher":"X","link":"https://x.com/a","providerPublishTime":1732545000}]}""",
        )
        assertEquals("AAPL", hits.single().symbol)
        assertEquals("Technology", hits.single().sector)
        assertEquals("https://x.com/a", news.single().url)

        val d = YahooParse.quoteSummary(
            """{"quoteSummary":{"result":[{"summaryDetail":{"marketCap":{"raw":3.5E12,"fmt":"3.5T"},"trailingPE":{"raw":35.1,"fmt":"35.10"}},
               "assetProfile":{"sector":"Technology","city":"Cupertino","state":"CA","country":"United States","fullTimeEmployees":164000,"longBusinessSummary":"Apple designs phones."},
               "financialData":{"recommendationKey":"strong_buy","targetMeanPrice":{"fmt":"250.00"},"numberOfAnalystOpinions":{"fmt":"40"}},
               "calendarEvents":{"earningsDate":[{"fmt":"2025-01-30"}]}}],"error":null}}""",
        )
        assertEquals("3.5T", d.stats.first { it.label == "Market cap" }.value)
        assertEquals("Cupertino, CA, United States", d.headquarters)
        assertEquals("Strong buy", d.recommendation)
        assertEquals("250.00 (40 analysts)", d.analystTarget)
        assertEquals("2025-01-30", d.stats.first { it.label == "Next earnings" }.value)
    }

    @Test
    fun parsesRecommendations() {
        assertEquals(listOf("MSFT", "GOOG"), YahooParse.recommendations(
            """{"finance":{"result":[{"symbol":"AAPL","recommendedSymbols":[{"symbol":"MSFT","score":0.1},{"symbol":"GOOG","score":0.1}]}],"error":null}}""",
        ))
    }

    @Test
    fun mapsGoogleSymbols() {
        assertEquals("AAPL:NASDAQ", GoogleFinanceParse.googleSymbol("AAPL", "NMS"))
        assertEquals("BRK.B:NYSE", GoogleFinanceParse.googleSymbol("BRK-B", "NYQ"))
        assertEquals("SHEL:LON", GoogleFinanceParse.googleSymbol("SHEL.L", "LSE"))
        assertEquals(".INX:INDEXSP", GoogleFinanceParse.googleSymbol("^GSPC", "SNP"))
        assertEquals("EUR-USD", GoogleFinanceParse.googleSymbol("EURUSD=X", "CCY"))
    }

    @Test
    fun parsesGoogleStats() {
        val html = """
          <div class="gyFHrc"><span class="iYuiXc"><div class="mfs7Fc">Previous close</div><div class="EY8ABd-OWXEXe-TAWMXe" role="tooltip">The last closing price</div></span><div class="P6K39c">${'$'}229.87</div></div>
          <div class="gyFHrc"><span class="iYuiXc"><div class="mfs7Fc">Market cap</div></span><div class="P6K39c">3.47T USD</div></div>
          <div class="gyFHrc"><span class="iYuiXc"><div class="mfs7Fc">CEO</div></span><div class="P6K39c"><a href="x">Tim Cook</a></div></div>
          <div class="bLLb2d">Apple Inc. is an American company &amp; more.<a>Wikipedia</a></div>
        """.trimIndent()
        val d = GoogleFinanceParse.details(html)
        assertEquals("$229.87", d.stats.first { it.label == "Previous close" }.value)
        assertEquals("3.47T USD", d.stats.first { it.label == "Market cap" }.value)
        assertEquals("Tim Cook", d.stats.first { it.label == "CEO" }.value)
        assertTrue(d.about!!.startsWith("Apple Inc. is an American company & more."))
        assertNull(d.website)
    }
}
