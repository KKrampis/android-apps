package io.github.kkrampis.bookmarkvault.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kkrampis.bookmarkvault.model.importChoices

/**
 * The extension's folder picker: every top-level folder of the pulled data with a checkbox,
 * then import the chosen ones into a dated folder, or replace the whole library.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSheet(vm: VaultViewModel, pending: PendingImport) {
    val choices = remember(pending) { pending.vault.importChoices() }
    var selected by remember(pending) { mutableStateOf(choices.map { it.key }.toSet()) }
    var confirmReplace by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val chosenCount = choices.filter { it.key in selected }.sumOf { it.count }

    ModalBottomSheet(onDismissRequest = { vm.cancelImport() }, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Import bookmarks", style = MaterialTheme.typography.titleLarge)
                Text(
                    "From ${pending.vault.device.ifBlank { "Unknown" }} · ${pending.vault.totalCount} bookmarks" +
                        (if (pending.vault.exportedAt.isNotBlank()) " · ${formatIso(pending.vault.exportedAt)}" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${selected.size}/${choices.size} · $chosenCount bookmarks",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { selected = choices.map { it.key }.toSet() }) { Text("All") }
                    TextButton(onClick = { selected = emptySet() }) { Text("None") }
                }
            }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                var lastRoot: String? = null
                choices.forEach { c ->
                    if (c.rootTitle != lastRoot) {
                        lastRoot = c.rootTitle
                        item(key = "root-${c.key}") {
                            Text(
                                c.rootTitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
                            )
                        }
                    }
                    item(key = c.key) {
                        val checked = c.key in selected
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = if (checked) selected - c.key else selected + c.key }
                                .padding(horizontal = 8.dp),
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { selected = if (it) selected + c.key else selected - c.key })
                            Icon(if (c.isLoose) Icons.Default.AttachFile else Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(10.dp))
                            Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("${c.count}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
                        }
                    }
                }
                if (choices.isEmpty()) item { EmptyNote("This data has no bookmarks.") }
            }
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.importSelected(selected) },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Import selected into Other bookmarks") }
                OutlinedButton(onClick = { confirmReplace = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Replace my library with this")
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }

    if (confirmReplace) {
        ConfirmDialog(
            heading = "Replace your library?",
            body = "Every bookmark on this phone is swapped for the ${pending.vault.totalCount} bookmarks from " +
                "${pending.vault.device.ifBlank { "this data" }}. Use this to mirror one browser exactly.",
            confirm = "Replace",
            onDismiss = { confirmReplace = false },
        ) {
            confirmReplace = false
            vm.replaceLibrary()
        }
    }
}
