package io.github.kkrampis.clipharbor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.kkrampis.clipharbor.BuildConfig
import io.github.kkrampis.clipharbor.container
import io.github.kkrampis.clipharbor.transcribe.SpeechModels
import io.github.kkrampis.clipharbor.ui.components.friendlyError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val c = LocalContext.current.container
    var cfg by remember { mutableStateOf(c.syncSettings.load()) }
    var status by remember { mutableStateOf(cfg.lastSyncMessage) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun perform(action: suspend () -> String) {
        c.syncSettings.save(cfg)
        busy = true
        status = "Working…"
        scope.launch {
            status = try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Failed: ${friendlyError(e)}"
            }
            cfg = c.syncSettings.load()
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & sync") },
                navigationIcon = {
                    IconButton(onClick = {
                        c.syncSettings.save(cfg)
                        onBack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Playlists and saved videos can be pushed to a GitHub repository, to jsonbin.io, or both. " +
                    "Keys stay on this phone, encrypted with the Android Keystore, and are only sent to the service they belong to.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Section("GitHub") {
                Field("Repository (owner/name)", cfg.githubRepo) { cfg = cfg.copy(githubRepo = it) }
                Field("Branch", cfg.githubBranch) { cfg = cfg.copy(githubBranch = it) }
                Field("File path in the repo", cfg.githubPath) { cfg = cfg.copy(githubPath = it) }
                SecretField("Personal access token", cfg.githubToken) { cfg = cfg.copy(githubToken = it) }
                Hint("Use a fine-grained token limited to that one repository, with Contents: read and write.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy && cfg.githubReady, onClick = { perform { c.sync.pushGitHub() } }) { Text("Push") }
                    OutlinedButton(enabled = !busy && cfg.githubReady, onClick = { perform { c.sync.pullGitHub() } }) {
                        Text("Import")
                    }
                }
            }

            Section("jsonbin.io") {
                SecretField("Master key (X-Master-Key)", cfg.jsonbinKey) { cfg = cfg.copy(jsonbinKey = it) }
                Field("Bin id (filled in after the first push)", cfg.jsonbinBinId) { cfg = cfg.copy(jsonbinBinId = it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy && cfg.jsonbinReady, onClick = { perform { c.sync.pushJsonBin() } }) { Text("Push") }
                    OutlinedButton(
                        enabled = !busy && cfg.jsonbinReady && cfg.jsonbinBinId.isNotBlank(),
                        onClick = { perform { c.sync.pullJsonBin() } },
                    ) { Text("Import") }
                }
            }

            Section("Automatic sync") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Push after every playlist change", Modifier.weight(1f))
                    Switch(checked = cfg.autoSync, onCheckedChange = {
                        cfg = cfg.copy(autoSync = it)
                        c.syncSettings.save(cfg)
                    })
                }
                Hint("Runs a few seconds after a change, once the phone is online, to every service set up above.")
            }

            Section("Speech models for transcripts") {
                val context = LocalContext.current
                var installed by remember { mutableStateOf(SpeechModels.installed(context)) }
                if (installed.isEmpty()) {
                    Hint("None downloaded yet. A model downloads the first time you transcribe a video on the phone.")
                } else {
                    installed.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val mb = SpeechModels.sizeOnDisk(context, m) / (1024 * 1024)
                            Text("${m.language} · $mb MB", Modifier.weight(1f))
                            TextButton(onClick = {
                                SpeechModels.delete(context, m)
                                installed = SpeechModels.installed(context)
                            }) { Text("Delete") }
                        }
                    }
                }
            }

            if (status.isNotBlank()) {
                Card(Modifier.fillMaxWidth()) {
                    Text(status, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }

            Text(
                "ClipHarbor ${BuildConfig.VERSION_NAME}. Browsing is anonymous: no Google account, no API key.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SecretField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
