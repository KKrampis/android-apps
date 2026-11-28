package io.github.kkrampis.radiorecorder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kkrampis.radiorecorder.core.Schedule
import io.github.kkrampis.radiorecorder.core.ScheduleMode
import io.github.kkrampis.radiorecorder.core.Window
import java.time.ZonedDateTime

private data class Item(val station: String, val window: Window?, val active: Boolean)

@Composable
fun ScheduleScreen(vm: AppViewModel) {
    val config by vm.config.collectAsState()
    vm.status.collectAsState().value // refresh with the engine tick
    val now = ZonedDateTime.now()
    val enabled = config.stations.filter { it.enabled }
    val always = enabled.filter { it.schedule.mode == ScheduleMode.ALWAYS }.map { Item(it.name, null, true) }
    val windows = enabled.flatMap { st ->
        Schedule.upcoming(st, 12).map { w -> Item(st.name, w, !w.start.isAfter(now) && now.isBefore(w.end)) }
    }.sortedBy { it.window!!.start.toInstant() }.take(40)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (always.isEmpty() && windows.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nothing scheduled", style = MaterialTheme.typography.titleMedium)
                    Hint("Add weekly program slots or one-off recordings in a station's editor.")
                }
            }
        }
        items(always) { Row24(it.station) }
        items(windows) { WindowRow(it) }
    }
}

@Composable
private fun Row24(station: String) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(station, style = MaterialTheme.typography.titleSmall)
                Hint("Recording continuously (24/7)")
            }
            Chip("24/7", StatusColors.ok)
        }
    }
}

@Composable
private fun WindowRow(item: Item) {
    val w = item.window!!
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(w.program.ifBlank { "(no program name)" }, style = MaterialTheme.typography.titleSmall)
                Hint(item.station)
                Text("${Fmt.local(w.start)} – ${Fmt.local(w.end)}", style = MaterialTheme.typography.bodySmall)
            }
            when {
                item.active -> Chip("now", StatusColors.recording)
                w.once -> Chip("once", StatusColors.waiting)
                else -> Chip("weekly", StatusColors.muted)
            }
        }
    }
}
