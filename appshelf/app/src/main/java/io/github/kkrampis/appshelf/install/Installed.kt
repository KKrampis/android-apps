package io.github.kkrampis.appshelf.install

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import io.github.kkrampis.appshelf.data.CatalogApp
import io.github.kkrampis.appshelf.data.Catalog
import io.github.kkrampis.appshelf.data.Settings

data class InstalledApp(val packageName: String, val versionName: String, val versionCode: Long)

/** Finds which catalog apps are on the phone, and at which version. */
class Installed(private val context: Context, private val settings: Settings) {
    private val pm: PackageManager get() = context.packageManager

    fun info(packageName: String): InstalledApp? = try {
        val p = pm.getPackageInfoCompat(packageName)
        InstalledApp(p.packageName, p.versionName.orEmpty(), versionCodeOf(p))
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /**
     * Looks for the app's package: the one learned when it was downloaded, then the usual naming
     * for GitHub projects (`io.github.<owner>.<app>`), then an installed app with the same name.
     */
    fun find(app: CatalogApp, labels: Map<String, String>): InstalledApp? {
        settings.packageFor(app.id)?.let { pkg -> info(pkg)?.let { return it } }
        val guess = "io.github.${app.source.owner.lowercase().replace("-", "")}.${app.key}"
        info(guess)?.let { return it }
        val byLabel = labels.entries.firstOrNull { (_, label) -> Catalog.keyOf(label) == app.key }?.key
        return byLabel?.let { info(it) }
    }

    /** Package name → label for every app the user installed (not the system ones). */
    fun userLabels(): Map<String, String> {
        val apps = try {
            pm.getInstalledApplications(0)
        } catch (e: Exception) {
            emptyList()
        }
        return apps.filter { it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 }
            .associate { it.packageName to pm.getApplicationLabel(it).toString() }
    }

    /** The package name inside a downloaded APK. */
    fun packageOfArchive(path: String): String? = try {
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(0))?.packageName
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(path, 0)?.packageName
        }
    } catch (e: Exception) {
        null
    }

    fun launchIntent(packageName: String): Intent? = pm.getLaunchIntentForPackage(packageName)

    fun icon(packageName: String) = try {
        pm.getApplicationIcon(packageName)
    } catch (e: Exception) {
        null
    }

    private fun PackageManager.getPackageInfoCompat(name: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= 33) {
            getPackageInfo(name, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            getPackageInfo(name, 0)
        }

    private fun versionCodeOf(p: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else @Suppress("DEPRECATION") p.versionCode.toLong()
}
