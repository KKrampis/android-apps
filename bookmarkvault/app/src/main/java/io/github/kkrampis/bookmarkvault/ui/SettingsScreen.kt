package io.github.kkrampis.bookmarkvault.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(vm: VaultViewModel, deviceName: String, contentPadding: PaddingValues) {
    var name by rememberSaveable(deviceName) { mutableStateOf(deviceName) }
    var confirmClearSync by remember { mutableStateOf(false) }
    var confirmClearLibrary by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Section("Device name", "Shown with each push and in the import folder name, so you can tell your devices apart.") {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { vm.setDeviceName(name) }, modifier = Modifier.align(Alignment.End)) { Text("Save") }
        }

        Section("Sync data", "Forgets the Sync ID, both keys and the push history. Your bookmarks stay.") {
            OutlinedButton(
                onClick = { confirmClearSync = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Clear sync data") }
        }

        Section("Library", "Removes every bookmark from this phone. Bins and exported files are not touched.") {
            OutlinedButton(
                onClick = { confirmClearLibrary = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Empty library") }
        }

        Section("About") {
            Text(
                "BookmarkVault for Android works with the BookmarkVault Chrome extension: same JSON format, " +
                    "same JSONBin bins. Share a page from any browser to bookmark it here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmClearSync) {
        ConfirmDialog("Clear sync data?", "The Sync ID, keys and push history will be removed from this phone.", "Clear",
            onDismiss = { confirmClearSync = false }) {
            vm.clearSync()
            confirmClearSync = false
        }
    }
    if (confirmClearLibrary) {
        ConfirmDialog("Empty library?", "Every bookmark on this phone will be deleted.", "Empty",
            onDismiss = { confirmClearLibrary = false }) {
            vm.clearLibrary()
            confirmClearLibrary = false
        }
    }
}
