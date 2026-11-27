package io.github.kkrampis.stockvault.ai

/**
 * Finds stock symbols in an AI answer (from Google AI Mode, the Claude app or the Claude API) so
 * the app can offer them as buttons to open or save.
 *
 * Best signal first: a `TICKERS: AMD, NVDA` line (the prompts ask for one), then exchange-prefixed
 * mentions like "(NASDAQ: AMD)", then symbols in parentheses or bold, e.g. "Advanced Micro Devices (AMD)".
 */
object Tickers {
    private val tickerLine = Regex("""(?im)^\W*tickers?\s*[:：]\s*(.+)$""")
    private val exchangePrefixed = Regex(
        """\b(?:NASDAQ|NYSE(?:\s?American|\s?Arca)?|AMEX|OTC|TSX|LSE|LON|ASX|TSE|XETRA|ETR|EPA|HKEX|NSE|BSE)\s*[:：]\s*([A-Z][A-Z0-9]{0,5}(?:[.\-][A-Z0-9]{1,3})?)""",
        RegexOption.IGNORE_CASE,
    )
    private val parenthesised = Regex("""\(\s*\$?([A-Z][A-Z0-9]{0,4}(?:[.\-][A-Z0-9]{1,3})?)\s*[),;]""")
    private val dollar = Regex("""(?<![A-Za-z0-9])\$([A-Z]{1,5}(?:\.[A-Z])?)\b""")
    private val bold = Regex("""\*\*([A-Z]{1,5}(?:[.\-][A-Z]{1,2})?)\*\*""")

    /** Short capitals that are usually words, units or acronyms rather than symbols. */
    private val notTickers = setOf(
        "A", "I", "AI", "US", "USA", "UK", "EU", "CEO", "CFO", "CTO", "ETF", "ETFS", "IPO", "EPS", "PE", "P", "E",
        "TTM", "YOY", "QOQ", "GDP", "CPI", "FED", "SEC", "NYSE", "NASDAQ", "OTC", "AMEX", "USD", "EUR", "GBP",
        "JPY", "API", "GPU", "CPU", "EV", "EVS", "SAAS", "IT", "AND", "OR", "THE", "FOR", "NOT", "NOTE", "LLC",
        "INC", "LTD", "PLC", "AG", "SA", "NV", "CO", "CORP", "ROE", "ROI", "ROA", "FCF", "EBIT", "EBITDA", "ESG",
        "R", "D", "RD", "M", "B", "T", "K", "Q", "Q1", "Q2", "Q3", "Q4", "FY", "HQ", "TICKER", "TICKERS", "N", "NA",
        "AR", "VR", "IOT", "UI", "OS", "PC", "TV", "LLM", "LLMS", "DC", "AWS", "GCP", "ETN", "REIT", "REITS",
    )

    fun extract(text: String, limit: Int = 25): List<String> {
        val out = LinkedHashSet<String>()
        fun add(raw: String) {
            val t = raw.trim().trimEnd('.', ',', ';').uppercase()
            if (t.isEmpty() || t.length > 10 || t in notTickers || t.all(Char::isDigit)) return
            out += t
        }
        tickerLine.findAll(text).forEach { m ->
            m.groupValues[1].split(',', ';', '|', '/', ' ').map { it.trim().removePrefix("$") }
                .filter { it.matches(Regex("[A-Za-z][A-Za-z0-9]{0,5}([.\\-][A-Za-z0-9]{1,3})?")) }
                .forEach(::add)
        }
        exchangePrefixed.findAll(text).forEach { add(it.groupValues[1]) }
        parenthesised.findAll(text).forEach { add(it.groupValues[1]) }
        dollar.findAll(text).forEach { add(it.groupValues[1]) }
        bold.findAll(text).forEach { add(it.groupValues[1]) }
        return out.take(limit)
    }
}

/** The questions the app sends to Google AI Mode, the Claude app and the Claude API. */
object Prompts {
    const val TICKER_INSTRUCTION =
        "End your answer with one line that starts with \"TICKERS:\" and lists every stock symbol you mention, comma separated."

    fun similar(symbol: String, name: String, sector: String? = null): String = buildString {
        append("Find publicly traded stocks similar to $name ($symbol)")
        if (!sector.isNullOrBlank()) append(", which is in $sector")
        append(". For each, give the ticker, the company name, and one line on why it is similar ")
        append("(business model, products, customers, size, or how the stock tends to move). ")
        append("Include direct competitors and a few less obvious alternatives. ")
        append(TICKER_INSTRUCTION)
    }

    fun analyze(symbol: String, name: String, summary: String): String = buildString {
        append("Give me a balanced analysis of $name ($symbol) as an investment: business, recent performance, ")
        append("valuation compared with peers, main risks and what to watch next. Here is what my app shows:\n")
        append(summary)
        append("\n\nThis is for research, not a recommendation. ")
        append(TICKER_INSTRUCTION)
    }

    fun portfolio(name: String, summary: String): String = buildString {
        append("Review this model portfolio, \"$name\". Comment on diversification, concentration, risk, ")
        append("and what has driven its past returns, and suggest holdings that would diversify it. ")
        append("My app's backtest:\n")
        append(summary)
        append("\n\n")
        append(TICKER_INSTRUCTION)
    }

    fun withTickerLine(question: String): String =
        if (question.contains("TICKERS:", ignoreCase = true)) question else "$question\n\n$TICKER_INSTRUCTION"
}
