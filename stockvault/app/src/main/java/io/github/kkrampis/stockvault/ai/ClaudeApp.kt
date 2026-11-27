package io.github.kkrampis.stockvault.ai

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Hands a question to the Claude app with Android's share intent, the same way ClipHarbor sends
 * transcripts. Android has no way for one app to read another app's answer back, and the Claude
 * app does not offer one, so the round trip is: StockVault opens Claude with the question, you
 * share (or copy) Claude's answer, and StockVault picks the tickers out of it.
 */
object ClaudeApp {
    const val PACKAGE = "com.anthropic.claude"

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Opens the Claude app with [question], or the share sheet when Claude is not installed. */
    fun ask(context: Context, question: String) {
        val text = "$question\n\n(When you're done, share this answer back to StockVault to save the stocks.)"
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
            .setPackage(PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(
                Intent.createChooser(Intent(intent).setPackage(null), "Ask an AI app").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
