package io.github.kkrampis.clipharbor.ui.screens

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import coil.compose.AsyncImage
import io.github.kkrampis.clipharbor.container
import io.github.kkrampis.clipharbor.ui.components.AddToPlaylistDialog
import io.github.kkrampis.clipharbor.ui.components.DownloadDialog
import io.github.kkrampis.clipharbor.ui.components.FullscreenEffect
import io.github.kkrampis.clipharbor.ui.components.PauseOnStop
import io.github.kkrampis.clipharbor.ui.components.PlayerSurface
import io.github.kkrampis.clipharbor.ui.components.VideoRow
import io.github.kkrampis.clipharbor.ui.components.friendlyError
import io.github.kkrampis.clipharbor.ui.viewsLine
import io.github.kkrampis.clipharbor.yt.BROWSER_USER_AGENT
import io.github.kkrampis.clipharbor.yt.Quality
import io.github.kkrampis.clipharbor.yt.QualityMode
import io.github.kkrampis.clipharbor.yt.QualityOption
import io.github.kkrampis.clipharbor.yt.StreamDetails
import io.github.kkrampis.clipharbor.yt.VideoItem
import io.github.kkrampis.clipharbor.yt.YouTube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
class WatchViewModel(app: Application, val url: String) : AndroidViewModel(app) {
    private val c = app.container

    var details by mutableStateOf<StreamDetails?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var options by mutableStateOf<List<QualityOption>>(emptyList())
        private set
    var current by mutableStateOf<QualityOption?>(null)
        private set
    var fullscreen by mutableStateOf(false)

    val isSaved = c.library.isSaved(url).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val player: ExoPlayer = ExoPlayer.Builder(app)
        .setAudioAttributes(AudioAttributes.DEFAULT, true)
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply { playWhenReady = true }

    private val dataSource = DefaultDataSource.Factory(
        app,
        OkHttpDataSource.Factory(c.http).setUserAgent(BROWSER_USER_AGENT),
    )

    init {
        load()
    }

    fun load() {
        error = null
        viewModelScope.launch {
            try {
                val d = YouTube.stream(url)
                details = d
                options = Quality.options(d)
                if (d.isLive && d.hlsUrl != null) {
                    player.setMediaSource(HlsMediaSource.Factory(dataSource).createMediaSource(MediaItem.fromUri(d.hlsUrl)))
                    player.prepare()
                } else {
                    Quality.defaultForPlayback(options)?.let { play(it, 0L) }
                        ?: run { error = "No playable stream was found for this video." }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = friendlyError(e)
            }
        }
    }

    fun selectQuality(o: QualityOption) {
        play(o, player.currentPosition)
    }

    private fun progressive(url: String): MediaSource =
        ProgressiveMediaSource.Factory(dataSource).createMediaSource(MediaItem.fromUri(url))

    private fun play(o: QualityOption, position: Long) {
        val source = when (o.mode) {
            QualityMode.MERGE -> MergingMediaSource(progressive(o.video!!.content), progressive(o.audio!!.content))
            QualityMode.MUXED -> progressive(o.video!!.content)
            QualityMode.AUDIO -> progressive(o.audio!!.content)
        }
        current = o
        player.setMediaSource(source, position)
        player.prepare()
    }

    fun toggleSaved() {
        val d = details ?: return
        viewModelScope.launch { c.library.setSaved(d.video, !isSaved.value) }
    }

    override fun onCleared() {
        player.release()
    }
}

@Composable
fun WatchScreen(
    url: String,
    onOpen: (VideoItem) -> Unit,
    onChannel: (String) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: WatchViewModel = viewModel(key = "watch:$url") { WatchViewModel(app, url) }
    val saved by vm.isSaved.collectAsState()
    val folders by context.container.downloads.folders.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var showQuality by remember { mutableStateOf(false) }
    var showDownload by remember { mutableStateOf(false) }
    var showPlaylist by remember { mutableStateOf(false) }
    var pendingDownload by remember { mutableStateOf<Pair<QualityOption, String>?>(null) }

    PauseOnStop(vm.player)
    FullscreenEffect(vm.fullscreen)
    BackHandler(enabled = vm.fullscreen) { vm.fullscreen = false }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val (option, folder) = pendingDownload ?: return@rememberLauncherForActivityResult
        pendingDownload = null
        val d = vm.details ?: return@rememberLauncherForActivityResult
        scope.launch {
            context.container.downloads.enqueue(d.video, option, folder)
            Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
        }
    }

    if (vm.fullscreen) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            PlayerSurface(vm.player, true, { vm.fullscreen = it }, Modifier.fillMaxSize())
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        PlayerSurface(
            vm.player,
            false,
            { vm.fullscreen = it },
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
        )
        val d = vm.details
        val loadError = vm.error
        if (d == null) {
            if (loadError != null) {
                Column(Modifier.padding(24.dp)) {
                    Text(loadError, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { vm.load() }) { Text("Try again") }
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "info") {
                    VideoInfo(
                        d = d,
                        saved = saved,
                        quality = vm.current?.label,
                        error = vm.error,
                        onChannel = { d.video.uploaderUrl?.let(onChannel) },
                        onSave = { vm.toggleSaved() },
                        onPlaylist = { showPlaylist = true },
                        onDownload = { showDownload = true },
                        onQuality = { showQuality = true },
                        onShare = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, d.video.url)
                                putExtra(Intent.EXTRA_SUBJECT, d.video.title)
                            }
                            context.startActivity(Intent.createChooser(send, "Share video"))
                        },
                    )
                }
                if (d.related.isNotEmpty()) {
                    item(key = "related-title") {
                        Text(
                            "Up next",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }
                }
                items(d.related, key = { "rel:" + it.url }) { v -> VideoRow(v, onClick = { onOpen(v) }) }
            }
        }
    }

    val d = vm.details
    if (showQuality && vm.options.isNotEmpty()) {
        QualityDialog(vm.options, vm.current, onDismiss = { showQuality = false }) {
            showQuality = false
            vm.selectQuality(it)
        }
    }
    if (showPlaylist && d != null) {
        AddToPlaylistDialog(d.video, onDismiss = { showPlaylist = false })
    }
    if (showDownload && d != null) {
        DownloadDialog(
            options = vm.options,
            defaultFolder = d.video.uploader.ifBlank { "General" },
            existingFolders = folders,
            onDismiss = { showDownload = false },
        ) { option, folder ->
            showDownload = false
            pendingDownload = option to folder
            val perms = buildList {
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                if (Build.VERSION.SDK_INT < 29) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            permissionLauncher.launch(perms.toTypedArray())
        }
    }
}

@Composable
private fun VideoInfo(
    d: StreamDetails,
    saved: Boolean,
    quality: String?,
    error: String?,
    onChannel: () -> Unit,
    onSave: () -> Unit,
    onPlaylist: () -> Unit,
    onDownload: () -> Unit,
    onQuality: () -> Unit,
    onShare: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(d.video.title, style = MaterialTheme.typography.titleMedium)
        Text(
            viewsLine(d.views, d.uploaded),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
        }
        Row(
            Modifier
                .padding(top = 12.dp)
                .clickable(onClick = onChannel),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = d.uploaderAvatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Text(
                d.video.uploader,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 10.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            item {
                AssistChip(
                    onClick = onSave,
                    label = { Text(if (saved) "Saved" else "Save") },
                    leadingIcon = {
                        Icon(
                            if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            null,
                            Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                )
            }
            item {
                AssistChip(
                    onClick = onPlaylist,
                    label = { Text("Playlist") },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, Modifier.size(AssistChipDefaults.IconSize))
                    },
                )
            }
            if (!d.isLive) {
                item {
                    AssistChip(
                        onClick = onDownload,
                        label = { Text("Download") },
                        leadingIcon = {
                            Icon(Icons.Default.Download, null, Modifier.size(AssistChipDefaults.IconSize))
                        },
                    )
                }
                item {
                    AssistChip(
                        onClick = onQuality,
                        label = { Text(quality ?: "Quality") },
                        leadingIcon = {
                            Icon(Icons.Default.HighQuality, null, Modifier.size(AssistChipDefaults.IconSize))
                        },
                    )
                }
            }
            item {
                AssistChip(
                    onClick = onShare,
                    label = { Text("Share") },
                    leadingIcon = { Icon(Icons.Default.Share, null, Modifier.size(AssistChipDefaults.IconSize)) },
                )
            }
        }
        if (d.description.isNotBlank()) {
            Text(
                d.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .animateContentSize()
                    .clickable { expanded = !expanded },
            )
        }
        HorizontalDivider(Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun QualityDialog(
    options: List<QualityOption>,
    current: QualityOption?,
    onDismiss: () -> Unit,
    onPick: (QualityOption) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback quality") },
        text = {
            LazyColumn {
                items(options) { o ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(o) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = o == current, onClick = { onPick(o) })
                        Text(o.label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
