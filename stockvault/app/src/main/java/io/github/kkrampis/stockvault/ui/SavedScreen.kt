package io.github.kkrampis.stockvault.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kkrampis.stockvault.model.Format
import io.github.kkrampis.stockvault.model.Library
import io.github.kkrampis.stockvault.model.Quote
import io.github.kkrampis.stockvault.model.SavedStock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(vm: AppViewModel, library: Library) {
    var folderId by rememberSaveable { mutableStateOf<String?>(null) }
    // A folder deleted elsewhere (or by sync) sends you back to the top.
    LaunchedEffect(library.folders) { if (folderId != null && library.folders.none { it.id == folderId }) folderId = null }
    val folder = library.folders.firstOrNull { it.id == folderId }
    val subfolders = library.childrenOf(folderId)
    val stocks = library.stocksIn(folderId)
    var quotes by remember { mutableStateOf<Map<String, Quote>>(emptyMap()) }
    var refresh by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var moveFolder by remember { mutableStateOf(false) }
    var deleteFolder by remember { mutableStateOf(false) }
    var stockMenu by remember { mutableStateOf<SavedStock?>(null) }
    var noteFor by remember { mutableStateOf<SavedStock?>(null) }
    var moveStock by remember { mutableStateOf<SavedStock?>(null) }

    LaunchedEffect(stocks.map { it.symbol }, refresh) {
        if (stocks.isNotEmpty()) quotes = quotes + vm.repo.quotes(stocks.map { it.symbol })
    }
    BackHandler(enabled = folderId != null) { folderId = folder?.parentId }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(folder?.name ?: "Saved", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (folder != null) SmallLabel(library.pathOf(folderId))
                }
            },
            navigationIcon = {
                if (folder != null) IconButton(onClick = { folderId = folder.parentId }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Up") }
            },
            actions = {
                IconButton(onClick = { refresh++ }) { Icon(Icons.Default.Refresh, "Refresh") }
                IconButton(onClick = { newFolder = true }) { Icon(Icons.Default.CreateNewFolder, "New folder") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Backtest these as a portfolio") },
                            leadingIcon = { Icon(Icons.Default.PieChart, null) },
                            enabled = stocks.isNotEmpty(),
                            onClick = { menu = false; vm.portfolioFromFolder(folderId) },
                        )
                        if (folder != null) {
                            DropdownMenuItem(text = { Text("Rename folder") }, leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                                onClick = { menu = false; rename = true })
                            DropdownMenuItem(text = { Text("Move folder") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) },
                                onClick = { menu = false; moveFolder = true })
                            DropdownMenuItem(text = { Text("Delete folder") }, leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = { menu = false; deleteFolder = true })
                        }
                    }
                }
            },
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(subfolders, key = { "f-" + it.id }) { f ->
                val tree = library.subtree(f.id)
                val count = library.saved.count { it.folderId in tree }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { folderId = f.id }.padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                    Text(f.name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).padding(start = 16.dp))
                    SmallLabel("$count")
                }
            }
            if (subfolders.isNotEmpty() && stocks.isNotEmpty()) item { HorizontalDivider() }
            items(stocks, key = { "s-" + it.id }) { s ->
                val q = quotes[s.symbol]
                val since = if (q?.price != null && s.priceAtSave != null && s.priceAtSave > 0) (q.price / s.priceAtSave - 1) * 100 else null
                val sub = listOfNotNull(
                    s.priceAtSave?.let { "Saved at ${Format.price(it, s.currency)}" + (since?.let { p -> " · ${Format.pct(p, digits = 1)} since" } ?: "") },
                    s.note.ifBlank { null },
                ).joinToString("\n").ifBlank { null }
                Box {
                    QuoteRow(
                        s.symbol, s.name.ifBlank { q?.name.orEmpty() }, q, subtitle = sub,
                        trailing = {
                            IconButton(onClick = { stockMenu = s }) { Icon(Icons.Default.MoreVert, "Options") }
                        },
                    ) { vm.openStock(s.symbol, s.name) }
                    DropdownMenu(expanded = stockMenu?.id == s.id, onDismissRequest = { stockMenu = null }) {
                        DropdownMenuItem(text = { Text(if (s.note.isBlank()) "Add note" else "Edit note") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, null) },
                            onClick = { stockMenu = null; noteFor = s })
                        DropdownMenuItem(text = { Text("Move to folder") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) },
                            onClick = { stockMenu = null; moveStock = s })
                        DropdownMenuItem(text = { Text("Remove") }, leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { stockMenu = null; vm.removeSaved(s.id) })
                    }
                }
            }
            if (subfolders.isEmpty() && stocks.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (folder == null) "Nothing saved yet" else "This folder is empty", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Open a stock and tap the bookmark to save it here. Make folders to group ideas, " +
                                "for example \"To research\" or \"Dividend picks\", with folders inside folders.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }

    if (newFolder) {
        TextInputDialog(title = if (folder == null) "New folder" else "New folder in ${folder.name}", confirm = "Create",
            onDismiss = { newFolder = false }) { if (it.isNotBlank()) vm.addFolder(it, folderId) }
    }
    if (rename && folder != null) {
        TextInputDialog(title = "Rename folder", initial = folder.name, onDismiss = { rename = false }) { vm.renameFolder(folder.id, it) }
    }
    if (moveFolder && folder != null) {
        FolderPickerDialog(library, "Move ${folder.name} into…", onDismiss = { moveFolder = false },
            onNewFolder = { n, p -> vm.addFolder(n, p) }, exclude = library.subtree(folder.id)) { vm.moveFolder(folder.id, it) }
    }
    if (deleteFolder && folder != null) {
        val tree = library.subtree(folder.id)
        val n = library.saved.count { it.folderId in tree }
        ConfirmDialog(
            title = "Delete ${folder.name}?",
            text = "This deletes the folder, ${tree.size - 1} folders inside it and $n saved stocks.",
            onDismiss = { deleteFolder = false },
        ) {
            folderId = folder.parentId
            vm.deleteFolder(folder.id)
        }
    }
    noteFor?.let { s ->
        TextInputDialog(title = "Note for ${s.symbol}", initial = s.note, label = "Note", singleLine = false,
            onDismiss = { noteFor = null }) { vm.setNote(s.id, it.trim()) }
    }
    moveStock?.let { s ->
        FolderPickerDialog(library, "Move ${s.symbol} to…", onDismiss = { moveStock = null },
            onNewFolder = { n, p -> vm.addFolder(n, p) }) { vm.moveSaved(s.id, it) }
    }
}
