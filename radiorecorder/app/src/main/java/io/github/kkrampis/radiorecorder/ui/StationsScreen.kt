package io.github.kkrampis.radiorecorder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kkrampis.radiorecorder.core.ScheduleMode
import io.github.kkrampis.radiorecorder.core.Station
import io.github.kkrampis.radiorecorder.engine.RecState
import io.github.kkrampis.radiorecorder.engine.RecorderStatus

@Composable
fun StationsScreen(vm: AppViewModel, batteryOptimized: Boolean, onFixBattery: () -> Unit) {
    val config by vm.config.collectAsState()
    val status by vm.status.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (config.paused) {
            item {
                Banner("Recording is stopped", "You stopped it from the notification. Scheduled recordings won't start until you resume.") {
                    Button(onClick = { vm.setPaused(false) }) { Text("Resume recording") }
                }
            }
        }
        if (batteryOptimized && config.stations.any { it.enabled }) {
            item {
                Banner(
                    "Allow background recording",
                    "Android may pause long recordings to save battery. Let Radio Recorder run unrestricted so recordings aren't cut.",
                ) { OutlinedButton(onClick = onFixBattery) { Text("Allow") } }
            }
        }
        if (config.stations.isEmpty()) {
            item { Empty() }
        }
        items(config.stations, key = { it.id }) { st ->
            StationCard(vm, st, status.stations[st.id], config.archive.enabled, config.archive.defaultCollection)
        }
    }
}

@Composable
private fun Banner(title: String, text: String, action: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            action()
        }
    }
}

@Composable
private fun Empty() {
    Column(
        Modifier.fillMaxWidth().padding(top = 80.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Radio, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("No stations yet", style = MaterialTheme.typography.titleMedium)
        Hint("Tap Add station and paste a stream link (MP3/AAC, .pls or .m3u). You can also share a link to Radio Recorder from your browser.")
    }
}

@Composable
private fun StationCard(vm: AppViewModel, st: Station, rs: RecorderStatus?, archiveOn: Boolean, defaultCollection: String) {
    val state = rs?.state ?: if (st.enabled) RecState.STARTING else RecState.DISABLED
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state == RecState.RECORDING) {
                RecDot()
                Spacer(Modifier.width(8.dp))
            }
            Text(st.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            StateChip(state)
            Spacer(Modifier.weight(1f))
            Switch(checked = st.enabled, onCheckedChange = { vm.setEnabled(st, it) })
        }
        Text(st.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.padding(top = 2.dp))
        if (rs != null) {
            if (state == RecState.RECORDING) {
                KeyValue("Program", rs.program ?: "—")
                KeyValue("Stream title", rs.title ?: if (rs.hasMetadata == false) "no metadata (splitting by time)" else "—")
                rs.fileName?.let {
                    val since = rs.sinceMs?.let { s -> Fmt.duration(System.currentTimeMillis() - s) } ?: ""
                    KeyValue("File", "$it · $since · ${Fmt.bytes(rs.bytesIn)} in")
                }
            }
            if (state == RecState.WAITING) KeyValue("Next", rs.nextStart?.let { Fmt.local(it) } ?: "nothing scheduled")
            if (rs.error != null && state != RecState.RECORDING) KeyValue("Error", rs.error, MaterialTheme.colorScheme.error)
            rs.note?.let { KeyValue("Note", it, StatusColors.waiting) }
        }
        KeyValue("Quality", st.quality.label())
        KeyValue("Splits", (if (st.split.onMetadata) "on program change, " else "") + "max ${st.split.maxMinutes.toInt()} min")
        KeyValue(
            "Schedule",
            if (st.schedule.mode == ScheduleMode.ALWAYS) "24/7" + (if (st.schedule.slots.isNotEmpty()) ", ${st.schedule.slots.size} named program slot(s)" else "")
            else "${st.schedule.slots.size} weekly slot(s), ${st.schedule.oneOff.size} one-off",
        )
        KeyValue("Archive", if (archiveOn && st.archive.enabled) st.archive.collection.ifBlank { defaultCollection } else "off")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { vm.edit(st) }) {
                Icon(Icons.Filled.Edit, null)
                Spacer(Modifier.width(6.dp))
                Text("Edit")
            }
            if (state == RecState.RECORDING) {
                TextButton(onClick = { vm.splitNow(st.id) }) {
                    Icon(Icons.Filled.ContentCut, null)
                    Spacer(Modifier.width(6.dp))
                    Text("New file now")
                }
            }
        }
    }
}
