package io.github.kkrampis.clipharbor.transcribe

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import io.github.kkrampis.clipharbor.download.Storage
import java.io.File

/** What to ask the AI app to do with the transcript. The text goes before the transcript. */
enum class AiPrompt(val label: String, val text: String?) {
    SUMMARY("Summarize", "Summarize this YouTube video from its transcript. Start with a one-paragraph overview, then the main points as a list."),
    KEY_POINTS("Key points", "List the key points, facts and takeaways from this YouTube video transcript, with the timestamp of each."),
    QUESTIONS("Ask about it", "Here is the transcript of a YouTube video. Read it, then answer my questions about it."),
    NONE("Transcript only", null),
}

/**
 * Sends a transcript to other apps with Android's share sheet: the Claude app directly when it is
 * installed, or any app that accepts shared text, including AI apps that run models on the phone.
 */
object TranscriptShare {
    const val CLAUDE_PACKAGE = "com.anthropic.claude"

    /** Above this, text is shared as a file: very large extras can crash the share. */
    private const val MAX_TEXT_CHARS = 200_000

    fun compose(title: String, url: String, transcript: String, prompt: AiPrompt): String = buildString {
        prompt.text?.let { append(it).append("\n\n") }
        append("Title: ").append(title).append('\n')
        append("Video: ").append(url).append("\n\n")
        append("Transcript:\n").append(transcript)
    }

    fun isClaudeInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(CLAUDE_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Opens the Claude app with the text, falling back to the share sheet. */
    fun toClaude(context: Context, title: String, text: String) {
        val intent = textIntent(context, title, text).setPackage(CLAUDE_PACKAGE)
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            shareText(context, title, text)
        }
    }

    fun shareText(context: Context, title: String, text: String) {
        context.startActivity(
            Intent.createChooser(textIntent(context, title, text), "Send transcript to…")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Shares the transcript as a .txt file, for apps that take documents or to save it in Files. */
    fun shareFile(context: Context, title: String, text: String) {
        val intent = fileIntent(context, title, text)
        context.startActivity(
            Intent.createChooser(intent, "Share transcript file").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun textIntent(context: Context, title: String, text: String): Intent =
        if (text.length > MAX_TEXT_CHARS) {
            fileIntent(context, title, text)
        } else {
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Transcript: $title")
                .putExtra(Intent.EXTRA_TEXT, text)
        }

    private fun fileIntent(context: Context, title: String, text: String): Intent {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "${Storage.cleanName(title).ifBlank { "transcript" }} - transcript.txt")
        file.writeText(text)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Transcript: $title")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
