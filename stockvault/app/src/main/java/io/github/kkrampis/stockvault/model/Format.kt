package io.github.kkrampis.stockvault.model

import java.util.Currency
import java.util.Locale
import kotlin.math.abs

/** Number formatting shared by the screens and the data parsers. */
object Format {
    fun price(v: Double?, currency: String = ""): String {
        if (v == null || v.isNaN()) return "—"
        val digits = when {
            abs(v) >= 1000 -> 2
            abs(v) >= 1 -> 2
            abs(v) >= 0.01 -> 4
            else -> 6
        }
        return affix(String.format(Locale.US, "%,.${digits}f", v), currency)
    }

    private fun affix(body: String, currency: String): String {
        val sym = symbolOf(currency)
        return when {
            sym.isEmpty() -> body
            sym == "p" -> "${body}p"
            sym.length == 1 || sym.endsWith("$") -> "$sym$body"
            else -> "$body $sym"
        }
    }

    fun money(v: Double?, currency: String = ""): String {
        if (v == null || v.isNaN()) return "—"
        val s = affix(String.format(Locale.US, "%,.0f", abs(v)), currency)
        return if (v < 0) "-$s" else s
    }

    fun symbolOf(currency: String): String = if (currency == "GBp" || currency == "GBX") "p" else when (currency.uppercase()) {
        "" -> ""
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        "JPY" -> "¥"
        "INR" -> "₹"
        "CAD" -> "CA$"
        "AUD" -> "A$"
        "HKD" -> "HK$"
        else -> runCatching { Currency.getInstance(currency.uppercase()).getSymbol(Locale.US) }.getOrDefault(currency)
    }

    fun pct(v: Double?, signed: Boolean = true, digits: Int = 2): String {
        if (v == null || v.isNaN()) return "—"
        val s = String.format(Locale.US, "%.${digits}f%%", v)
        return if (signed && v > 0) "+$s" else s
    }

    fun signed(v: Double?, digits: Int = 2): String {
        if (v == null || v.isNaN()) return "—"
        val s = String.format(Locale.US, "%,.${digits}f", v)
        return if (v > 0) "+$s" else s
    }

    fun num(v: Double?, digits: Int = 2): String =
        if (v == null || v.isNaN()) "—" else String.format(Locale.US, "%,.${digits}f", v)

    /** 1.23T, 456.7B, 12.3M, 45.6K. */
    fun big(v: Double?): String {
        if (v == null || v.isNaN()) return "—"
        val a = abs(v)
        return when {
            a >= 1e12 -> String.format(Locale.US, "%.2fT", v / 1e12)
            a >= 1e9 -> String.format(Locale.US, "%.2fB", v / 1e9)
            a >= 1e6 -> String.format(Locale.US, "%.2fM", v / 1e6)
            a >= 1e4 -> String.format(Locale.US, "%.1fK", v / 1e3)
            else -> String.format(Locale.US, "%,.0f", v)
        }
    }
}
