package io.github.kkrampis.clipharbor.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.github.kkrampis.clipharbor.container
import io.github.kkrampis.clipharbor.data.DownloadEntity
import io.github.kkrampis.clipharbor.data.DownloadStatus
import io.github.kkrampis.clipharbor.download.Storage
import io.github.kkrampis.clipharbor.ui.components.CenteredMessage
import io.github.kkrampis.clipharbor.ui.components.FullscreenEffect
import io.github.kkrampis.clipharbor.ui.components.PauseOnStop
import io.github.kkrampis.clipharbor.ui.components.PlayerSurface
import io.github.kkrampis.clipharbor.ui.components.Thumbnail
import io.github.kkrampis.clipharbor.yt.QualityMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onPlay: (Long) -> Unit, onOpenPage: (String) -> Unit, onSettings: () -> Unit) {
    val repo = LocalContext.current.container.downloads
    val all by repo.all.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf<DownloadEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") } },
            )
        },
    ) { pad ->
        if (all.isEmpty()) {
            CenteredMessage(
                "Downloads you start from a video page land here, sorted into folders.",
                Modifier.padding(pad),
            )
        } else {
            DownloadList(all, pad, onPlay, onOpenPage, onCancel = { id -> scope.launch { repo.cancel(id) } },
                onRetry = { id -> scope.launch { repo.retry(id) } }, onDelete = { confirmDelete = it })
        }
    }

    confirmDelete?.let { d ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete download?") },
            text = { Text("\"${d.title}\" will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch { repo.delete(d) }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DownloadList(
    all: List<DownloadEntity>,
    pad: PaddingValues,
    onPlay: (Long) -> Unit,
    onOpenPage: (String) -> Unit,
    onCancel: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onDelete: (DownloadEntity) -> Unit,
) {
    val groups = all.groupBy { it.folder }
    LazyColumn(Modifier.padding(pad)) {
        groups.forEach { (folder, group) ->
            item(key = "folder:$folder") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(folder, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${Storage.ROOT}/$folder · ${group.size} item${if (group.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(group, key = { it.id }) { d ->
                DownloadRow(
                    d,
                    onClick = {
                        if (d.status == DownloadStatus.DONE) onPlay(d.id) else onOpenPage(d.pageUrl)
                    },
                    onCancel = { onCancel(d.id) },
                    onRetry = { onRetry(d.id) },
                    onDelete = { onDelete(d) },
                )
            }
        }
    }
}

@Composable
private fun DownloadRow(
    d: DownloadEntity,
    onClick: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(d.thumbnail, 0, Modifier.width(120.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(d.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val statusText = when (d.status) {
                DownloadStatus.QUEUED -> "Waiting"
                DownloadStatus.RUNNING -> "Downloading · ${(d.progress * 100).toInt()}%"
                DownloadStatus.PROCESSING -> "Merging audio and video"
                DownloadStatus.DONE -> d.qualityLabel
                DownloadStatus.FAILED -> "Failed: ${d.error ?: "unknown error"}"
                DownloadStatus.CANCELLED -> "Cancelled"
                else -> d.status
            }
            Text(
                statusText,
                style = MaterialTheme.typography.bodySmall,
                color = if (d.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (d.status == DownloadStatus.RUNNING || d.status == DownloadStatus.QUEUED) {
                if (d.progress > 0f) {
                    LinearProgressIndicator(
                        progress = { d.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                } else {
                    LinearProgressIndicator(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                }
            }
        }
        when (d.status) {
            DownloadStatus.RUNNING, DownloadStatus.QUEUED, DownloadStatus.PROCESSING ->
                IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Cancel") }
            DownloadStatus.FAILED, DownloadStatus.CANCELLED -> Row {
                IconButton(onClick = onRetry) { Icon(Icons.Default.Refresh, "Retry") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
            }
            else -> IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
        }
    }
}

/** Plays a finished download from shared storage. Works offline. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LocalPlayerScreen(id: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val item by context.container.downloads.observe(id).collectAsState(initial = null)
    val player = remember { ExoPlayer.Builder(context).build().apply { playWhenReady = true } }
    var fullscreen by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { player.release() } }
    PauseOnStop(player)
    FullscreenEffect(fullscreen)
    BackHandler(enabled = fullscreen) { fullscreen = false }

    val uri = item?.fileUri
    DisposableEffect(uri) {
        if (uri != null) {
            player.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
            player.prepare()
        }
        onDispose { }
    }

    val d = item
    if (fullscreen) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            PlayerSurface(player, true, { fullscreen = it }, Modifier.fillMaxSize())
        }
        return
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PlayerSurface(
            player,
            false,
            { fullscreen = it },
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
        )
        if (d != null) {
            Column(Modifier.padding(16.dp)) {
                Text(d.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${d.uploader} · ${d.qualityLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Stored in ${Storage.displayPath(d.folder, d.mode == QualityMode.AUDIO.name)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                TextButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) { Text("Back to downloads") }
            }
        }
    }
}
