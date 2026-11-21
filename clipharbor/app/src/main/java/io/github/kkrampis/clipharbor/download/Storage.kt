package io.github.kkrampis.clipharbor.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Saves finished downloads into shared storage, one subfolder per user-chosen folder:
 * Movies/ClipHarbor/<folder>/ for video and Music/ClipHarbor/<folder>/ for audio.
 * They show up in any gallery, music player or file manager.
 */
object Storage {
    const val ROOT = "ClipHarbor"

    fun cleanFolder(folder: String): String =
        cleanName(folder).ifBlank { "General" }

    fun cleanName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
            .take(90)

    fun displayPath(folder: String, audio: Boolean): String =
        "${if (audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES}/$ROOT/$folder"

    /** Copies [source] into shared storage and returns the URI of the new file. */
    fun save(context: Context, source: File, title: String, folder: String, audio: Boolean): Uri {
        val ext = if (audio) "m4a" else "mp4"
        val mime = if (audio) "audio/mp4" else "video/mp4"
        val fileName = "${cleanName(title).ifBlank { "video" }}.$ext"
        val relative = displayPath(folder, audio)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = if (audio) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$relative/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values)
                ?: error("Could not create the file in shared storage")
            try {
                resolver.openOutputStream(uri)!!.use { out -> source.inputStream().use { it.copyTo(out) } }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            uri
        } else {
            @Suppress("DEPRECATION")
            val base = Environment.getExternalStoragePublicDirectory(
                if (audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES,
            )
            val dir = File(base, "$ROOT/$folder").apply { mkdirs() }
            var target = File(dir, fileName)
            var n = 1
            while (target.exists()) {
                target = File(dir, "${fileName.substringBeforeLast('.')} ($n).$ext")
                n++
            }
            source.copyTo(target)
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
            Uri.fromFile(target)
        }
    }

    fun delete(context: Context, uri: Uri) {
        runCatching {
            if (uri.scheme == "file") {
                uri.path?.let { File(it).delete() }
            } else {
                context.contentResolver.delete(uri, null, null)
            }
        }
    }
}
