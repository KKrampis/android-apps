package io.github.kkrampis.bookmarkvault.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kkrampis.bookmarkvault.model.Bookmark
import io.github.kkrampis.bookmarkvault.model.Folder
import io.github.kkrampis.bookmarkvault.model.Node
import io.github.kkrampis.bookmarkvault.model.Vault
import io.github.kkrampis.bookmarkvault.model.allBookmarks
import io.github.kkrampis.bookmarkvault.model.countBookmarks
import io.github.kkrampis.bookmarkvault.model.findFolder
import io.github.kkrampis.bookmarkvault.model.mobileRoot
import io.github.kkrampis.bookmarkvault.model.pathTo

private sealed interface Editing {
    data class NewBookmark(val folderId: String) : Editing
    data class NewFolder(val folderId: String) : Editing
    data class EditBookmark(val bookmark: Bookmark) : Editing
    data class RenameFolder(val folder: Folder) : Editing
    data class Move(val node: Node) : Editing
    data class Delete(val node: Node) : Editing
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(vm: VaultViewModel, vault: Vault, contentPadding: PaddingValues) {
    val context = LocalContext.current
    var currentId by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Editing?>(null) }
    var addMenu by remember { mutableStateOf(false) }

    // A folder may vanish (deleted, or the library was replaced by a pull); fall back to the top.
    val current: Folder? = currentId?.let { vault.findFolder(it) }
    val path = remember(vault, currentId) { currentId?.let { vault.pathTo(it) }.orEmpty() }

    BackHandler(enabled = searching || current != null) {
        when {
            searching -> { searching = false; query = "" }
            else -> currentId = path.dropLast(1).lastOrNull()?.id
        }
    }

    Scaffold(
        modifier = Modifier.padding(contentPadding),
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search bookmarks") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(current?.title ?: "BookmarkVault", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    if (current != null && !searching) {
                        IconButton(onClick = { currentId = path.dropLast(1).lastOrNull()?.id }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Up")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) query = ""
                    }) {
                        Icon(if (searching) Icons.Default.Close else Icons.Default.Search, "Search")
                    }
                },
            )
        },
        floatingActionButton = {
            if (!searching) {
                Box {
                    FloatingActionButton(onClick = { addMenu = true }) { Icon(Icons.Default.Add, "Add") }
                    DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                        val target = current ?: vault.mobileRoot()
                        DropdownMenuItem(
                            text = { Text("Bookmark") },
                            leadingIcon = { Icon(Icons.Default.BookmarkAdd, null) },
                            onClick = { addMenu = false; target?.let { editing = Editing.NewBookmark(it.id) } },
                        )
                        DropdownMenuItem(
                            text = { Text("Folder") },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                            onClick = { addMenu = false; target?.let { editing = Editing.NewFolder(it.id) } },
                        )
                    }
                }
            }
        },
    ) { inner ->
        if (searching) {
            val results = remember(vault, query) {
                val q = query.trim()
                if (q.isEmpty()) emptyList() else vault.allBookmarks().filter { (b, _) ->
                    b.title.contains(q, ignoreCase = true) || b.url.contains(q, ignoreCase = true)
                }
            }
            LazyColumn(Modifier.fillMaxSize().padding(inner), contentPadding = PaddingValues(bottom = 96.dp)) {
                if (query.isNotBlank() && results.isEmpty()) {
                    item { EmptyNote("No bookmarks match \"$query\"") }
                }
                items(results, key = { it.first.id }) { (b, trail) ->
                    BookmarkRow(
                        bookmark = b,
                        subtitle = trail.joinToString(" › "),
                        onOpen = { openUrl(context, b.url) },
                        onEdit = { editing = Editing.EditBookmark(b) },
                        onMove = { editing = Editing.Move(b) },
                        onDelete = { editing = Editing.Delete(b) },
                    )
                }
            }
        } else {
            val children: List<Node> = current?.children ?: vault.roots
            LazyColumn(Modifier.fillMaxSize().padding(inner), contentPadding = PaddingValues(bottom = 96.dp)) {
                if (current == null) {
                    item { StatsCard(vault) }
                } else if (path.size > 1) {
                    item {
                        Text(
                            path.dropLast(1).joinToString(" › ") { it.title },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
                if (children.isEmpty()) {
                    item { EmptyNote("Nothing here yet. Tap + to add a bookmark, or share a page to BookmarkVault from your browser.") }
                }
                items(children, key = { it.id }) { node ->
                    when (node) {
                        is Folder -> FolderRow(
                            folder = node,
                            isRoot = current == null,
                            onOpen = { currentId = node.id },
                            onRename = { editing = Editing.RenameFolder(node) },
                            onMove = { editing = Editing.Move(node) },
                            onDelete = { editing = Editing.Delete(node) },
                        )
                        is Bookmark -> BookmarkRow(
                            bookmark = node,
                            subtitle = hostOf(node.url),
                            onOpen = { openUrl(context, node.url) },
                            onEdit = { editing = Editing.EditBookmark(node) },
                            onMove = { editing = Editing.Move(node) },
                            onDelete = { editing = Editing.Delete(node) },
                        )
                    }
                }
            }
        }
    }

    when (val e = editing) {
        null -> {}
        is Editing.NewBookmark -> BookmarkDialog(
            heading = "Add bookmark",
            initialTitle = "",
            initialUrl = "",
            vault = vault,
            initialFolderId = e.folderId,
            onDismiss = { editing = null },
        ) { title, url, folderId ->
            vm.add(folderId ?: e.folderId, Bookmark(title = title, url = url))
            editing = null
        }
        is Editing.NewFolder -> NameDialog("New folder", "", { editing = null }) {
            vm.add(e.folderId, Folder(title = it))
            editing = null
        }
        is Editing.EditBookmark -> BookmarkDialog(
            heading = "Edit bookmark",
            initialTitle = e.bookmark.title,
            initialUrl = e.bookmark.url,
            vault = null,
            initialFolderId = null,
            onDismiss = { editing = null },
        ) { title, url, _ ->
            vm.update(e.bookmark.copy(title = title, url = url))
            editing = null
        }
        is Editing.RenameFolder -> NameDialog("Rename folder", e.folder.title, { editing = null }) {
            vm.update(e.folder.copy(title = it))
            editing = null
        }
        is Editing.Move -> FolderPickerDialog(
            vault = vault,
            excludeId = (e.node as? Folder)?.id,
            onDismiss = { editing = null },
        ) {
            vm.move(e.node.id, it.id)
            editing = null
        }
        is Editing.Delete -> ConfirmDialog(
            heading = "Delete ${if (e.node is Folder) "folder" else "bookmark"}?",
            body = when (val n = e.node) {
                is Folder -> "\"${n.title}\" and the ${countBookmarks(n)} bookmarks inside it will be removed from this phone."
                is Bookmark -> "\"${n.title}\" will be removed from this phone."
            },
            confirm = "Delete",
            onDismiss = { editing = null },
        ) {
            vm.delete(e.node.id)
            editing = null
        }
    }
}

@Composable
private fun StatsCard(vault: Vault) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat(vault.totalCount, "Bookmarks")
            Stat(vault.folderCount, "Folders")
            Stat(vault.roots.size, "Roots")
        }
    }
}

@Composable
private fun Stat(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyNote(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
    )
}

@Composable
private fun FolderRow(
    folder: Folder,
    isRoot: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        leadingContent = { Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(folder.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("${countBookmarks(folder)} bookmarks") },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onRename() })
                    // Top-level folders mirror Chrome's own and stay put.
                    if (!isRoot) {
                        DropdownMenuItem(text = { Text("Move") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) }, onClick = { menu = false; onMove() })
                        DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun BookmarkRow(
    bookmark: Bookmark,
    subtitle: String,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        leadingContent = { Icon(Icons.Default.Bookmark, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(22.dp)) },
        headlineContent = { Text(bookmark.title.ifBlank { bookmark.url }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = {
                        menu = false
                        shareText(context, bookmark.url, bookmark.title)
                    })
                    DropdownMenuItem(text = { Text("Copy link") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = {
                        menu = false
                        copyText(context, bookmark.url)
                    })
                    DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Move") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) }, onClick = { menu = false; onMove() })
                    DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; onDelete() })
                }
            }
        },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

fun hostOf(url: String): String = runCatching { Uri.parse(url).host }.getOrNull()?.removePrefix("www.") ?: url

fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show()
    }
}

fun shareText(context: Context, text: String, subject: String? = null) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    if (!subject.isNullOrBlank()) send.putExtra(Intent.EXTRA_SUBJECT, subject)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("BookmarkVault", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
