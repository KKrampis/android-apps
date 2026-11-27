package io.github.kkrampis.appshelf.data

import android.content.Context
import android.os.Build
import io.github.kkrampis.appshelf.install.Installed
import io.github.kkrampis.appshelf.install.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

/** One repository and the apps found in it (or why none could be read). */
data class SourceState(
    val source: Source,
    val apps: List<CatalogApp> = emptyList(),
    val error: String? = null,
    val warning: String? = null,
)

/** A catalog app with what is installed on the phone. */
data class AppRow(val app: CatalogApp, val installed: InstalledApp?) {
    val hasUpdate: Boolean
        get() = installed != null && Versions.isNewer(app.latest.version, installed.versionName)
}

/** Everything the screen and the background check share: reading repositories and downloading APKs. */
class Store(context: Context) {
    private val appContext = context.applicationContext
    val settings = Settings(appContext)
    val github = GitHub(File(appContext.cacheDir, "github")) { settings.token }
    val installed = Installed(appContext, settings)
    private val apkDir = File(appContext.cacheDir, "apks")

    suspend fun load(): List<SourceState> = withContext(Dispatchers.IO) {
        val include = settings.includePrereleases
        val abis = Build.SUPPORTED_ABIS?.toList().orEmpty()
        coroutineScope {
            settings.sources.map { source ->
                async {
                    try {
                        val fetched = github.releases(source)
                        val apps = Catalog.build(source, fetched.releases, include, abis)
                        SourceState(
                            source, apps,
                            warning = fetched.warning
                                ?: if (apps.isEmpty()) "No releases with an APK in this repository." else null,
                        )
                    } catch (e: IOException) {
                        SourceState(source, error = e.message)
                    } catch (e: Exception) {
                        SourceState(source, error = "Couldn't read the releases: ${e.message}")
                    }
                }
            }.awaitAll()
        }.also { settings.lastCheckedAt = System.currentTimeMillis() }
    }

    /** Matches catalog apps with installed ones. Reads the installed app list once. */
    suspend fun rows(states: List<SourceState>): List<AppRow> = withContext(Dispatchers.IO) {
        val labels = installed.userLabels()
        states.flatMap { it.apps }.map { AppRow(it, installed.find(it, labels)) }
    }

    /** Downloads an APK into the app's cache, reporting progress from 0 to 1 (or -1 when the size is unknown). */
    suspend fun download(app: CatalogApp, version: AppVersion, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            apkDir.mkdirs()
            // Only one APK is kept per app.
            apkDir.listFiles()?.filter { it.name.startsWith(app.key + "-") }?.forEach { it.delete() }
            val target = File(apkDir, "${app.key}-${version.version}.apk".replace(Regex("[^A-Za-z0-9._-]"), "_"))
            val part = File(target.path + ".part")
            github.http.newCall(github.downloadRequest(version.apk)).execute().use { r ->
                if (!r.isSuccessful) throw IOException("Download failed: GitHub answered ${r.code}.")
                val body = r.body ?: throw IOException("Download failed: empty answer.")
                val total = body.contentLength().takeIf { it > 0 } ?: version.apk.size.takeIf { it > 0 } ?: -1L
                body.byteStream().use { input ->
                    part.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        var lastReported = -1
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != lastReported) {
                                    lastReported = pct
                                    onProgress(done.toFloat() / total)
                                }
                            } else {
                                onProgress(-1f)
                            }
                        }
                    }
                }
            }
            if (!part.renameTo(target)) throw IOException("Couldn't save the APK.")
            installed.packageOfArchive(target.path)?.let { settings.rememberPackage(app.id, it) }
                ?: run {
                    target.delete()
                    throw IOException("The downloaded file isn't a valid APK.")
                }
            target
        }

    fun clearDownloads() {
        apkDir.listFiles()?.forEach { it.delete() }
    }
}
