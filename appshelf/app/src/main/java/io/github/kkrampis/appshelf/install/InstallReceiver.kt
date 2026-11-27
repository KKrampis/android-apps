package io.github.kkrampis.appshelf.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

/** Gets each install's status from Android: asks for confirmation, then reports the result. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appId = intent.getStringExtra(Installer.EXTRA_APP_ID).orEmpty()
        val name = intent.getStringExtra(Installer.EXTRA_APP_NAME) ?: "The app"
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm != null) {
                    try {
                        context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        Installer.report(InstallResult(appId, false, "Couldn't show the install prompt for $name. Open AppShelf and try again."))
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS ->
                Installer.report(InstallResult(appId, true, "$name installed."))
            PackageInstaller.STATUS_FAILURE_ABORTED ->
                Installer.report(InstallResult(appId, false, "Install of $name cancelled."))
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                Installer.report(
                    InstallResult(
                        appId, false,
                        "$name couldn't be updated: the installed copy is signed with a different key, " +
                            "or is a newer version. Uninstall it first, then install again.",
                    ),
                )
            PackageInstaller.STATUS_FAILURE_STORAGE ->
                Installer.report(InstallResult(appId, false, "Not enough storage to install $name."))
            else ->
                Installer.report(InstallResult(appId, false, "$name wasn't installed" + (detail?.let { ": $it" } ?: ".")))
        }
    }
}
