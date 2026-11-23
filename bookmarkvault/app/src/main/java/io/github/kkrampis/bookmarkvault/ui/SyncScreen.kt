package io.github.kkrampis.bookmarkvault.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.kkrampis.bookmarkvault.model.Vault

@Composable
fun SyncScreen(vm: VaultViewModel, vault: Vault, state: SyncUiState, contentPadding: PaddingValues) {
    val context = LocalContext.current
    var masterKey by rememberSaveable { mutableStateOf(state.masterKey) }
    var accessKey by rememberSaveable { mutableStateOf(state.accessKey) }
    var pullId by rememberSaveable { mutableStateOf(state.binId.orEmpty()) }
    var showKeys by rememberSaveable { mutableStateOf(false) }

    // After the first push the new bin id becomes the default to pull from, like in the extension.
    LaunchedEffect(state.binId) { if (pullId.isBlank() && state.binId != null) pullId = state.binId }

    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        Text(
            "Sync",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            if (state.lastSyncedAt != null) "Last push ${formatIso(state.lastSyncedAt)}" else "Never synced",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Section(
            "JSONBin keys",
            "The same keys you use in the Chrome extension. Find them on jsonbin.io under API Keys. " +
                "They stay on this phone, encrypted.",
        ) {
            val transform = if (showKeys) VisualTransformation.None else PasswordVisualTransformation()
            OutlinedTextField(
                value = masterKey,
                onValueChange = { masterKey = it },
                label = { Text("Master Key (needed to push)") },
                singleLine = true,
                visualTransformation = transform,
                trailingIcon = {
                    IconButton(onClick = { showKeys = !showKeys }) {
                        Icon(if (showKeys) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Show keys")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = accessKey,
                onValueChange = { accessKey = it },
                label = { Text("Access Key (optional, pull only)") },
                singleLine = true,
                visualTransformation = transform,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { vm.saveKeys(masterKey, accessKey) }, modifier = Modifier.align(Alignment.End)) {
                Text("Save keys")
            }
        }

        Section("Push", "Uploads this phone's ${vault.totalCount} bookmarks to your bin. The first push creates a private bin.") {
            Button(
                onClick = { vm.push(masterKey, accessKey) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.CloudUpload, null)
                Spacer(Modifier.width(8.dp))
                Text("Push bookmarks")
            }
            if (state.binId != null) {
                Spacer(Modifier.height(12.dp))
                Text("Your Sync ID", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SelectionContainer(Modifier.weight(1f)) {
                        Text(state.binId, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = { copyText(context, state.binId) }) { Icon(Icons.Default.ContentCopy, "Copy Sync ID") }
                }
            }
        }

        Section("Pull", "Paste the Sync ID (Bin ID) shown in the Chrome extension after a push.") {
            OutlinedTextField(
                value = pullId,
                onValueChange = { pullId = it },
                label = { Text("Sync ID") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.pull(pullId.trim(), masterKey, accessKey) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.CloudDownload, null)
                Spacer(Modifier.width(8.dp))
                Text("Pull bookmarks")
            }
        }

        if (state.busy) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
        }

        Section("Push history") {
            if (state.history.isEmpty()) {
                Text("No push history yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.history.forEachIndexed { i, entry ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(formatIso(entry.pushedAt), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${entry.device.ifBlank { "Unknown" }} · ${entry.count} bookmarks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(enabled = !state.busy, onClick = {
                        pullId = entry.binId
                        vm.pull(entry.binId, masterKey, accessKey)
                    }) { Text("Pull") }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
