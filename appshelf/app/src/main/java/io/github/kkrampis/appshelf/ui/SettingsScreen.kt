package io.github.kkrampis.appshelf.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.kkrampis.appshelf.install.Installer
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, settings: UiSettings) {
    val context = LocalContext.current
    var token by remember { mutableStateOf("") }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) vm.say("Notifications are off, so updates only show in the app.")
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Settings") })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionTitle("Updates")
            ToggleRow(
                "Check for updates in the background",
                "Every 6 hours on any network, with a notification when an installed app has a new version.",
                settings.backgroundChecks,
            ) { on ->
                vm.setBackgroundChecks(on)
                if (on && Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            ToggleRow(
                "Include pre-releases",
                "Show releases marked as pre-release on GitHub as new versions.",
                settings.includePrereleases,
                vm::setIncludePrereleases,
            )
            if (settings.lastCheckedAt > 0) {
                Text(
                    "Last checked " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(settings.lastCheckedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = vm::refresh, modifier = Modifier.padding(top = 8.dp)) { Text("Check now") }

            SectionTitle("Installing", Modifier.padding(top = 24.dp))
            val allowed = Installer.canInstall(context)
            Text(
                if (allowed) "AppShelf is allowed to install apps."
                else "AppShelf isn't allowed to install apps yet. Android asks the first time you tap Install.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row {
                TextButton(onClick = { context.startActivity(Installer.allowInstallsIntent(context)) }) { Text("Install permission") }
                TextButton(onClick = vm::clearDownloads) { Text("Delete downloaded APKs") }
            }

            SectionTitle("GitHub token (optional)", Modifier.padding(top = 24.dp))
            Text(
                "Without a token GitHub allows 60 requests an hour, which is plenty for a few repositories " +
                    "(unchanged answers don't count). A token raises the limit to 5,000 and lets AppShelf read " +
                    "private repositories. Create a fine-grained token on github.com → Settings → Developer settings, " +
                    "with read-only access to Contents. It is stored encrypted on this phone only.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text(if (settings.hasToken) "Token saved. Enter a new one to replace it" else "Token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row {
                TextButton(enabled = token.isNotBlank(), onClick = { vm.setToken(token); token = "" }) { Text("Save token") }
                if (settings.hasToken) TextButton(onClick = { vm.setToken("") }) { Text("Remove token") }
                TextButton(onClick = { openUrl(context, "https://github.com/settings/personal-access-tokens/new") }) { Text("Create one") }
            }

            SectionTitle("About", Modifier.padding(top = 24.dp))
            Text(
                "AppShelf installs apps straight from GitHub Releases. Android checks that every update is signed " +
                    "with the same key as the installed app, so an update from anyone else is refused.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
