package io.github.kkrampis.clipharbor.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kkrampis.clipharbor.container
import io.github.kkrampis.clipharbor.data.PlaylistSummary
import io.github.kkrampis.clipharbor.ui.components.CenteredMessage
import io.github.kkrampis.clipharbor.ui.components.MenuAction
import io.github.kkrampis.clipharbor.ui.components.NameDialog
import io.github.kkrampis.clipharbor.ui.components.Thumbnail
import io.github.kkrampis.clipharbor.ui.components.VideoMenuDialogs
import io.github.kkrampis.clipharbor.ui.components.VideoRow
import io.github.kkrampis.clipharbor.ui.components.rememberVideoMenu
import io.github.kkrampis.clipharbor.ui.components.videoActions
import io.github.kkrampis.clipharbor.yt.VideoItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpen: (VideoItem) -> Unit,
    onPlaylist: (Long) -> Unit,
    onSettings: () -> Unit,
) {
    val library = LocalContext.current.container.library
    val saved by library.saved.collectAsState(initial = emptyList())
    val playlists by library.playlists.collectAsState(initial = emptyList())
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val menu = rememberVideoMenu()
    val actions = videoActions(
        menu,
        includeSave = false,
        extra = { v -> listOf(MenuAction("Remove from saved") { scope.launch { library.setSaved(v, false) } }) },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") } },
            )
        },
        floatingActionButton = {
            if (tab == 1) {
                ExtendedFloatingActionButton(
                    onClick = { creating = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("New playlist") },
                )
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Saved (${saved.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Playlists (${playlists.size})") })
            }
            if (tab == 0) {
                if (saved.isEmpty()) {
                    CenteredMessage("Videos you save show up here.")
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(saved, key = { it.url }) { v -> VideoRow(v, { onOpen(v) }, actions(v)) }
                    }
                }
            } else {
                if (playlists.isEmpty()) {
                    CenteredMessage("Make a playlist here, or from any video.")
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(playlists, key = { it.id }) { p -> PlaylistRow(p) { onPlaylist(p.id) } }
                    }
                }
            }
        }
    }
    if (creating) {
        NameDialog(title = "New playlist", confirm = "Create", onDismiss = { creating = false }) { name ->
            creating = false
            scope.launch { library.createPlaylist(name) }
        }
    }
    VideoMenuDialogs(menu)
}

@Composable
private fun PlaylistRow(p: PlaylistSummary, onClick: () -> Unit) {
    Row(
        Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(p.thumbnail, 0, Modifier.width(120.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${p.count} video${if (p.count == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(id: Long, onOpen: (VideoItem) -> Unit, onBack: () -> Unit) {
    val library = LocalContext.current.container.library
    val playlist by library.playlist(id).collectAsState(initial = null)
    val videos by library.playlistVideos(id).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val menu = rememberVideoMenu()
    val actions = videoActions(
        menu,
        extra = { v -> listOf(MenuAction("Remove from playlist") { scope.launch { library.removeFromPlaylist(id, v.url) } }) },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Default.Edit, "Rename") }
                    IconButton(onClick = { deleting = true }) { Icon(Icons.Default.Delete, "Delete") }
                },
            )
        },
    ) { pad ->
        if (videos.isEmpty()) {
            CenteredMessage("This playlist is empty. Add videos from their page or the ⋮ menu.", Modifier.padding(pad))
        } else {
            LazyColumn(Modifier.padding(pad)) {
                items(videos, key = { it.url }) { v -> VideoRow(v, { onOpen(v) }, actions(v)) }
            }
        }
    }
    if (renaming) {
        NameDialog("Rename playlist", initial = playlist?.name ?: "", onDismiss = { renaming = false }) { name ->
            renaming = false
            scope.launch { library.renamePlaylist(id, name) }
        }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete this playlist?") },
            text = { Text("The videos stay in your saved list and downloads.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    scope.launch {
                        library.deletePlaylist(id)
                        onBack()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
    VideoMenuDialogs(menu)
}
