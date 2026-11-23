package io.github.kkrampis.bookmarkvault.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kkrampis.bookmarkvault.model.Folder
import io.github.kkrampis.bookmarkvault.model.Vault
import io.github.kkrampis.bookmarkvault.model.allFolders
import io.github.kkrampis.bookmarkvault.model.findFolder

/** Adds a URL scheme when someone types "example.com". */
fun normalizeUrl(raw: String): String {
    val t = raw.trim()
    if (t.isEmpty()) return t
    return if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(t)) t else "https://$t"
}

/**
 * Add or edit a bookmark. When [folderChoice] is set, the person can also pick the folder
 * (used for new bookmarks and shared links).
 */
@Composable
fun BookmarkDialog(
    heading: String,
    initialTitle: String,
    initialUrl: String,
    vault: Vault?,
    initialFolderId: String?,
    onDismiss: () -> Unit,
    onSave: (title: String, url: String, folderId: String?) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var url by remember { mutableStateOf(initialUrl) }
    var folderId by remember { mutableStateOf(initialFolderId) }
    var picking by remember { mutableStateOf(false) }
    val valid = url.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(heading) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (vault != null && folderId != null) {
                    Spacer(Modifier.size(12.dp))
                    Text("Folder", style = MaterialTheme.typography.labelMedium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { picking = true }
                            .padding(vertical = 8.dp),
                    ) {
                        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            vault.findFolder(folderId!!)?.title ?: "Choose…",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text("Change", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val u = normalizeUrl(url)
                onSave(title.trim().ifEmpty { u }, u, folderId)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (picking && vault != null) {
        FolderPickerDialog(vault = vault, excludeId = null, onDismiss = { picking = false }) {
            folderId = it.id
            picking = false
        }
    }
}

@Composable
fun NameDialog(heading: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(heading) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Lists every folder, indented by depth. [excludeId] hides a folder and everything inside it. */
@Composable
fun FolderPickerDialog(vault: Vault, excludeId: String?, onDismiss: () -> Unit, onPick: (Folder) -> Unit) {
    val folders = remember(vault, excludeId) {
        val all = vault.allFolders()
        if (excludeId == null) all else {
            val out = mutableListOf<Pair<Folder, Int>>()
            var skipBelow = -1
            for ((f, depth) in all) {
                if (skipBelow >= 0 && depth > skipBelow) continue
                skipBelow = -1
                if (f.id == excludeId) { skipBelow = depth; continue }
                out += f to depth
            }
            out
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a folder") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(folders, key = { it.first.id }) { (folder, depth) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(folder) }
                            .padding(start = (depth * 16).dp, top = 10.dp, bottom = 10.dp),
                    ) {
                        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(folder.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(heading: String, body: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(heading) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
