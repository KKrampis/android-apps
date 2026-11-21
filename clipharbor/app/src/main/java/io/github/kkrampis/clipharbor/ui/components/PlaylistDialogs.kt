package io.github.kkrampis.clipharbor.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.kkrampis.clipharbor.container
import io.github.kkrampis.clipharbor.yt.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Holds which video, if any, the "add to playlist" dialog is open for. */
class VideoMenuState {
    var playlistTarget by mutableStateOf<VideoItem?>(null)
}

@Composable
fun rememberVideoMenu() = remember { VideoMenuState() }

/** The usual long-press style actions for a video in a list. */
@Composable
fun videoActions(
    menu: VideoMenuState,
    extra: (VideoItem) -> List<MenuAction> = { emptyList() },
    includeSave: Boolean = true,
): (VideoItem) -> List<MenuAction> {
    val context = LocalContext.current
    val library = context.container.library
    val scope = rememberCoroutineScope()
    return { v ->
        buildList {
            if (includeSave) {
                add(
                    MenuAction("Save") {
                        scope.launch {
                            library.setSaved(v, true)
                            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
            add(MenuAction("Add to playlist") { menu.playlistTarget = v })
            addAll(extra(v))
        }
    }
}

@Composable
fun VideoMenuDialogs(menu: VideoMenuState) {
    menu.playlistTarget?.let { v ->
        AddToPlaylistDialog(video = v, onDismiss = { menu.playlistTarget = null })
    }
}

@Composable
fun AddToPlaylistDialog(video: VideoItem, onDismiss: () -> Unit) {
    val library = LocalContext.current.container.library
    val playlists by library.playlists.collectAsState(initial = emptyList())
    val containing by library.playlistIdsContaining(video.url).collectAsState(initial = emptyList())
    val scope: CoroutineScope = rememberCoroutineScope()
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(playlists, key = { it.id }) { p ->
                        val checked = p.id in containing
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        if (checked) library.removeFromPlaylist(p.id, video.url)
                                        else library.addToPlaylist(p.id, video)
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text("${p.name}  (${p.count})", Modifier.padding(start = 8.dp))
                        }
                    }
                }
                if (playlists.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New playlist") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            val name = newName
                            newName = ""
                            scope.launch {
                                val id = library.createPlaylist(name)
                                library.addToPlaylist(id, video)
                            }
                        },
                    ) { Text("Create") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
fun NameDialog(
    title: String,
    initial: String = "",
    confirm: String = "Save",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name) }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
