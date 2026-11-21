package io.github.kkrampis.clipharbor.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kkrampis.clipharbor.download.Storage
import io.github.kkrampis.clipharbor.yt.QualityOption

@Composable
fun DownloadDialog(
    options: List<QualityOption>,
    defaultFolder: String,
    existingFolders: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (QualityOption, String) -> Unit,
) {
    var selected by remember { mutableStateOf(options.firstOrNull { !it.isAudioOnly && it.height <= 1080 } ?: options.firstOrNull()) }
    var folder by remember { mutableStateOf(defaultFolder) }
    val suggestions = (listOf(defaultFolder) + existingFolders).filter { it.isNotBlank() }.distinct()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download") },
        text = {
            Column {
                Text("Quality", style = MaterialTheme.typography.labelLarge)
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(options) { o ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selected = o },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = o == selected, onClick = { selected = o })
                            Text(o.label)
                        }
                    }
                }
                Text(
                    "Folder",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                OutlinedTextField(
                    value = folder,
                    onValueChange = { folder = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    items(suggestions) { s -> AssistChip(onClick = { folder = s }, label = { Text(s) }) }
                }
                val s = selected
                if (s != null) {
                    Text(
                        "Saved to ${Storage.displayPath(Storage.cleanFolder(folder), s.isAudioOnly)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected != null,
                onClick = { selected?.let { onConfirm(it, folder) } },
            ) { Text("Download") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
