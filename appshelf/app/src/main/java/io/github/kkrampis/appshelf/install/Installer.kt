package io.github.kkrampis.appshelf.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.File

/** The outcome of an install, sent by [InstallReceiver]. */
data class InstallResult(val appId: String, val success: Boolean, val message: String)

/** Hands downloaded APKs to Android's package installer, which asks you to confirm. */
object Installer {
    const val EXTRA_APP_ID = "io.github.kkrampis.appshelf.APP_ID"
    const val EXTRA_APP_NAME = "io.github.kkrampis.appshelf.APP_NAME"

    private val _results = MutableSharedFlow<InstallResult>(extraBufferCapacity = 16)
    val results: SharedFlow<InstallResult> = _results

    internal fun report(result: InstallResult) {
        _results.tryEmit(result)
    }

    /** Whether Android lets this app install APKs ("Install unknown apps" in Settings). */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun allowInstallsIntent(context: Context): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(android.net.Uri.parse("package:${context.packageName}"))

    fun install(context: Context, apk: File, appId: String, appName: String, packageName: String?) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        packageName?.let { params.setAppPackageName(it) }
        if (Build.VERSION.SDK_INT >= 31) {
            // Updates to apps AppShelf installed can go through without asking again (Android 12+).
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        params.setSize(apk.length())
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val intent = Intent(context, InstallReceiver::class.java)
                    .setPackage(context.packageName)
                    .putExtra(EXTRA_APP_ID, appId)
                    .putExtra(EXTRA_APP_NAME, appName)
                // Mutable: the system adds the install status to this intent.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
    }
}
