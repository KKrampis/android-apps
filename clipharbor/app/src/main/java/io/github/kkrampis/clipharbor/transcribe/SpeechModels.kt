package io.github.kkrampis.clipharbor.transcribe

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/** A downloadable offline speech model from the Vosk project (alphacephei.com/vosk/models). */
data class SpeechModel(val id: String, val language: String, val archive: String, val sizeMb: Int) {
    val url: String get() = "https://alphacephei.com/vosk/models/$archive.zip"
    val label: String get() = "$language · $sizeMb MB"
}

object SpeechModels {
    val all = listOf(
        SpeechModel("en", "English", "vosk-model-small-en-us-0.15", 40),
        SpeechModel("es", "Spanish", "vosk-model-small-es-0.42", 39),
        SpeechModel("fr", "French", "vosk-model-small-fr-0.22", 41),
        SpeechModel("de", "German", "vosk-model-small-de-0.15", 45),
        SpeechModel("it", "Italian", "vosk-model-small-it-0.22", 48),
        SpeechModel("pt", "Portuguese", "vosk-model-small-pt-0.3", 31),
        SpeechModel("nl", "Dutch", "vosk-model-small-nl-0.22", 39),
        SpeechModel("ru", "Russian", "vosk-model-small-ru-0.22", 45),
        SpeechModel("tr", "Turkish", "vosk-model-small-tr-0.3", 35),
        SpeechModel("hi", "Hindi", "vosk-model-small-hi-0.22", 42),
        SpeechModel("ja", "Japanese", "vosk-model-small-ja-0.22", 48),
        SpeechModel("zh", "Chinese", "vosk-model-small-cn-0.22", 42),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }

    private fun root(context: Context) = File(context.filesDir, "speech-models")

    private fun dir(context: Context, m: SpeechModel) = File(root(context), m.archive)

    private fun marker(context: Context, m: SpeechModel) = File(root(context), "${m.archive}.ready")

    fun isInstalled(context: Context, m: SpeechModel) = marker(context, m).exists() && dir(context, m).isDirectory

    fun installed(context: Context) = all.filter { isInstalled(context, it) }

    fun sizeOnDisk(context: Context, m: SpeechModel): Long =
        dir(context, m).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun delete(context: Context, m: SpeechModel) {
        marker(context, m).delete()
        dir(context, m).deleteRecursively()
    }

    /** Returns the model folder, downloading and unpacking it first if needed. */
    suspend fun ensure(
        context: Context,
        client: OkHttpClient,
        m: SpeechModel,
        onProgress: suspend (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        if (isInstalled(context, m)) return@withContext dir(context, m)
        val root = root(context).apply { mkdirs() }
        val zip = File(context.cacheDir, "${m.archive}.zip")
        val req = Request.Builder().url(m.url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Model download failed (HTTP ${resp.code})")
            val body = resp.body ?: throw IOException("Empty model download")
            val total = body.contentLength().takeIf { it > 0 } ?: (m.sizeMb * 1024L * 1024L)
            var done = 0L
            var last = 0L
            zip.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (done - last > 512 * 1024) {
                            last = done
                            onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        }
        dir(context, m).deleteRecursively()
        val rootPath = root.canonicalPath + File.separator
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                val target = File(root, entry.name)
                // Refuse entries that would land outside the models folder.
                if (!target.canonicalPath.startsWith(rootPath)) throw IOException("Unexpected path in model archive")
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zin.copyTo(it) }
                }
            }
        }
        zip.delete()
        if (!dir(context, m).isDirectory) throw IOException("The model archive had an unexpected layout")
        marker(context, m).writeText("ok")
        dir(context, m)
    }
}
