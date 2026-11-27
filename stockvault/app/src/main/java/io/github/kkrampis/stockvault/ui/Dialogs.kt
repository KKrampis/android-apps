package io.github.kkrampis.stockvault.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kkrampis.stockvault.data.MarketRepository
import io.github.kkrampis.stockvault.model.Library
import io.github.kkrampis.stockvault.model.SearchHit
import kotlinx.coroutines.delay

@Composable
fun TextInputDialog(
    title: String,
    initial: String = "",
    label: String = "Name",
    confirm: String = "Save",
    singleLine: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, label = { Text(label) },
                singleLine = singleLine, minLines = if (singleLine) 1 else 3, modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "Delete", onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Picks a folder (or the top level) to save into or move to. "New folder" creates one inside the
 * highlighted level and selects it. [exclude] hides a folder and its children (when moving a folder).
 */
@Composable
fun FolderPickerDialog(
    library: Library,
    title: String,
    onDismiss: () -> Unit,
    onNewFolder: (name: String, parentId: String?) -> Unit,
    exclude: Set<String> = emptySet(),
    onPick: (String?) -> Unit,
) {
    var creatingIn by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    // Folders listed depth-first with indentation.
    val rows = remember(library, exclude) {
        val out = mutableListOf<Pair<io.github.kkrampis.stockvault.model.Folder, Int>>()
        fun walk(parent: String?, depth: Int) {
            library.childrenOf(parent).filter { it.id !in exclude }.forEach {
                out += it to depth
                walk(it.id, depth + 1)
            }
        }
        walk(null, 0)
        out
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    PickRow(Icons.Default.Home, "Saved (top level)", 0) { onPick(null); onDismiss() }
                }
                items(rows, key = { it.first.id }) { (f, depth) ->
                    PickRow(Icons.Default.Folder, f.name, depth + 1) { onPick(f.id); onDismiss() }
                }
                item {
                    HorizontalDivider()
                    PickRow(Icons.Default.CreateNewFolder, "New folder…", 0) { creatingIn = null; creating = true }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (creating) {
        TextInputDialog(
            title = "New folder",
            onDismiss = { creating = false },
            confirm = "Create",
        ) { name ->
            if (name.isNotBlank()) onNewFolder(name, creatingIn)
        }
    }
}

@Composable
private fun PickRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, depth: Int, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(start = (depth * 16).dp, top = 10.dp, bottom = 10.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(label, modifier = Modifier.padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun PortfolioPickerDialog(
    library: Library,
    onDismiss: () -> Unit,
    onNew: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to portfolio") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(library.portfolios, key = { it.id }) { p ->
                    PickRow(Icons.Default.PieChart, "${p.name} (${p.holdings.size})", 0) { onPick(p.id); onDismiss() }
                }
                item {
                    HorizontalDivider()
                    PickRow(Icons.Default.CreateNewFolder, "New portfolio…", 0) { onNew(); onDismiss() }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Searches Yahoo's symbol lookup as you type and returns the chosen symbol. */
@Composable
fun SymbolSearchDialog(repo: MarketRepository, title: String = "Add a stock", onDismiss: () -> Unit, onPick: (SearchHit) -> Unit) {
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        if (query.isBlank()) { hits = emptyList(); return@LaunchedEffect }
        delay(300)
        busy = true
        error = null
        runCatching { repo.search(query) }
            .onSuccess { hits = it }
            .onFailure { error = it.message }
        busy = false
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Name or symbol") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) },
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(hits, key = { it.symbol }) { h ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(h); onDismiss() }.padding(vertical = 8.dp)) {
                            Text(h.symbol, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${h.name} · ${h.exchangeName} · ${h.type}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            // A symbol typed exactly, e.g. one the search doesn't know.
            if (query.isNotBlank()) {
                TextButton(onClick = {
                    onPick(SearchHit(query.trim().uppercase(), query.trim().uppercase(), "", "", ""))
                    onDismiss()
                }) { Text("Use \"${query.trim().uppercase()}\"") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
