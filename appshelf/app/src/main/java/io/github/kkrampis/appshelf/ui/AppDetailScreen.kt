package io.github.kkrampis.appshelf.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.kkrampis.appshelf.data.AppRow
import io.github.kkrampis.appshelf.data.AppVersion
import io.github.kkrampis.appshelf.data.Versions

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppDetailScreen(vm: AppViewModel, row: AppRow, task: Task?, onBack: () -> Unit) {
    val context = LocalContext.current
    val gate = rememberInstallGate()
    val app = row.app
    val latest = app.latest
    var olderPick by remember { mutableStateOf<AppVersion?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(app.name) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = { openUrl(context, latest.release.htmlUrl.ifBlank { app.source.releasesUrl }) }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open on GitHub")
                }
            },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(row, vm, 64.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.name, style = MaterialTheme.typography.headlineSmall)
                    Text(app.source.key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                buildString {
                    append("Latest ${latest.version}")
                    formatSize(latest.apk.size).takeIf { it.isNotEmpty() }?.let { append(" · $it") }
                    append(" · ${formatWhen(latest.release.publishedAt)}")
                    if (latest.release.prerelease) append(" · pre-release")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            row.installed?.let {
                Text(
                    "Installed ${it.versionName}" + if (row.hasUpdate) ", update available" else ", up to date",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (row.hasUpdate) Palette.update else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppAction(row, task, vm, gate)
                row.installed?.let { inst ->
                    if (row.hasUpdate) {
                        val launch = remember(inst.packageName) { vm.store.installed.launchIntent(inst.packageName) }
                        if (launch != null) OutlinedButton(onClick = { context.startActivity(launch) }) { Text("Open") }
                    }
                    OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${inst.packageName}")))
                        }
                    }) { Text("Uninstall") }
                }
            }
            Text(
                "APK: ${latest.apk.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            if (latest.release.body.isNotBlank()) {
                SectionTitle("What's new in ${latest.version}", Modifier.padding(top = 20.dp, bottom = 6.dp))
                SelectionContainer { Text(cleanNotes(latest.release.body), style = MaterialTheme.typography.bodyMedium) }
            }

            if (app.versions.size > 1) {
                SectionTitle("All versions", Modifier.padding(top = 20.dp, bottom = 4.dp))
                app.versions.forEach { v ->
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(v.version + if (v.release.prerelease) " (pre-release)" else "", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                listOf(formatWhen(v.release.publishedAt), formatSize(v.apk.size), "${v.apk.downloads} downloads")
                                    .filter { it.isNotEmpty() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val isInstalled = row.installed?.versionName == v.version
                        TextButton(
                            enabled = task == null && !isInstalled,
                            onClick = {
                                val inst = row.installed
                                if (inst != null && Versions.compare(v.version, inst.versionName) < 0) {
                                    olderPick = v
                                } else {
                                    gate.run { vm.install(app, v) }
                                }
                            },
                        ) { Text(if (isInstalled) "Installed" else "Install") }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    olderPick?.let { v ->
        AlertDialog(
            onDismissRequest = { olderPick = null },
            title = { Text("Install an older version?") },
            text = {
                Text(
                    "Android doesn't install an older version over a newer one. Uninstall ${app.name} " +
                        "first (its data on the phone is deleted with it), then install ${v.version}.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    olderPick = null
                    row.installed?.let {
                        runCatching { context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${it.packageName}"))) }
                    }
                }) { Text("Uninstall") }
            },
            dismissButton = { TextButton(onClick = { olderPick = null }) { Text("Cancel") } },
        )
    }
}

/** Release notes are Markdown; show them as readable plain text. */
private fun cleanNotes(md: String): String = md
    .replace("\r\n", "\n")
    .lines()
    .joinToString("\n") { line ->
        line.replace(Regex("^#{1,6}\\s*"), "")
            .replace(Regex("^\\s*[-*]\\s+"), "• ")
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace(Regex("`([^`]+)`"), "$1")
            .replace(Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)"), "$1 ($2)")
    }
    .trim()
