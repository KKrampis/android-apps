package io.github.kkrampis.radiorecorder.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kkrampis.radiorecorder.data.RecStatus
import io.github.kkrampis.radiorecorder.data.Recording

@Composable
fun RecordingsScreen(vm: AppViewModel) {
    val all by vm.recordings.collectAsState()
    val config by vm.config.collectAsState()
    val status by vm.status.collectAsState()
    val playing by vm.playing.collectAsState()
    var filter by remember { mutableStateOf<String?>(null) }
    val list = all.filter { filter == null || it.stationId == filter }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(filter == null, { filter = null }, label = { Text("All") })
                config.stations.forEach { st -> FilterChip(filter == st.id, { filter = st.id }, label = { Text(st.name) }) }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Hint("${list.size} recordings · ${Fmt.bytes(status.freeBytes)} free", )
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = vm::uploadNow) {
                    Icon(Icons.Filled.CloudUpload, null)
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 6.dp))
                    Text("Upload now")
                }
            }
            status.upload?.let { u ->
                Column(Modifier.padding(top = 6.dp)) {
                    Hint("Uploading ${u.fileName}")
                    LinearProgressIndicator(progress = { if (u.size > 0) u.sent.toFloat() / u.size else 0f }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (list.isEmpty()) item { Hint("No recordings yet.") }
        items(list, key = { it.id }) { RecordingRow(vm, it, playing == it.id) }
    }
}

@Composable
private fun RecordingRow(vm: AppViewModel, r: Recording, isPlaying: Boolean) {
    val ctx = LocalContext.current
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.program ?: "—", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Hint(r.stationName)
            }
            RecStatusChip(r.status)
        }
        val titles = r.titles.map { it.title }.filter { it.isNotBlank() }.distinct()
        if (titles.size > 1) Hint(titles.take(4).joinToString(" · ") + if (titles.size > 4) " …" else "")
        val len = r.endMs?.let { Fmt.duration(it - r.startMs) } ?: "recording…"
        Text(
            listOf(Fmt.epoch(r.startMs), len, Fmt.bytes(r.bytes), r.format).filterNotNull().joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
        )
        r.lastError?.let { if (r.status == RecStatus.FAILED || r.status == RecStatus.DELETED) Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2) }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (r.hasLocalFile) {
                IconButton(onClick = { vm.togglePlay(r) }) {
                    Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (isPlaying) "Pause" else "Play")
                }
                IconButton(onClick = { vm.shareIntent(r)?.let { ctx.startActivity(it) } }) { Icon(Icons.Filled.Share, "Share") }
            }
            r.iaUrl?.let { url ->
                IconButton(onClick = { vm.openUrl(url)?.let { runCatching { ctx.startActivity(it) } } }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open on archive.org")
                }
            }
            if (r.status == RecStatus.FAILED) {
                IconButton(onClick = { vm.retry(r) }) { Icon(Icons.Filled.Refresh, "Retry upload") }
            }
        }
    }
}
