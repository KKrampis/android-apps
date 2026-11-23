package io.github.kkrampis.searchvault.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.kkrampis.searchvault.model.Archive
import java.time.LocalDate

@Composable
fun SyncScreen(vm: SearchViewModel, archive: Archive, state: SyncUiState) {
    val context = LocalContext.current
    var masterKey by rememberSaveable(state.masterKey) { mutableStateOf(state.masterKey) }
    var accessKey by rememberSaveable(state.accessKey) { mutableStateOf(state.accessKey) }
    var linkId by rememberSaveable { mutableStateOf("") }
    var showKeys by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.writeTo(it, vm.exportJson()) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importFrom(it) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        Text("Sync", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Text(
            if (state.lastSyncedAt != null) "Last sync ${formatIso(state.lastSyncedAt)}" else "Never synced",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Section(
            "JSONBin keys",
            "The same JSONBin.io keys you use in BookmarkVault (jsonbin.io → API Keys). They stay on this phone, encrypted.",
        ) {
            val transform = if (showKeys) VisualTransformation.None else PasswordVisualTransformation()
            OutlinedTextField(
                value = masterKey,
                onValueChange = { masterKey = it },
                label = { Text("Master Key (needed to save to JSONBin)") },
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
                label = { Text("Access Key (optional, read only)") },
                singleLine = true,
                visualTransformation = transform,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { vm.saveKeys(masterKey, accessKey) }, modifier = Modifier.align(Alignment.End)) {
                Text("Save keys")
            }
        }

        Section(
            "Sync searches",
            "Sync reads the bin, merges its searches with this phone's, then saves the result back. " +
                "The first sync creates a private bin.",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sync after every search", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Each new search and answer goes to JSONBin straight away",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.autoSync, onCheckedChange = { vm.setAutoSync(it) })
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (masterKey != state.masterKey || accessKey != state.accessKey) vm.saveKeys(masterKey, accessKey)
                    vm.syncNow()
                },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.Sync, null)
                }
                Spacer(Modifier.width(8.dp))
                Text("Sync ${archive.searches.size} searches now")
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

        Section(
            "Use an existing bin",
            "To share one history between phones, paste the Sync ID shown on the other phone. " +
                "Both phones then sync into the same bin.",
        ) {
            OutlinedTextField(
                value = linkId,
                onValueChange = { linkId = it },
                label = { Text("Sync ID") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    if (masterKey != state.masterKey || accessKey != state.accessKey) vm.saveKeys(masterKey, accessKey)
                    vm.linkBin(linkId)
                    linkId = ""
                },
                enabled = !state.busy && linkId.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Link, null)
                Spacer(Modifier.width(8.dp))
                Text("Use this bin and sync")
            }
        }

        Section("Backup file", "Save every search as a SearchVault JSON file, or merge one back in.") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { exportLauncher.launch("SearchVault-${LocalDate.now()}.json") },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.FileDownload, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Export")
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.FileUpload, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Import")
                }
            }
        }

        if (state.binId != null || state.masterKey.isNotBlank()) {
            TextButton(onClick = { confirmClear = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("Clear sync data", color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear sync data?") },
            text = { Text("Forgets the Sync ID and the JSONBin keys on this phone. Your searches and the bin itself are kept.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; vm.clearSync() }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}
