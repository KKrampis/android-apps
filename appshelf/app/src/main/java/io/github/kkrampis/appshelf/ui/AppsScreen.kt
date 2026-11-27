package io.github.kkrampis.appshelf.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kkrampis.appshelf.data.AppRow
import io.github.kkrampis.appshelf.data.SourceState

enum class Filter(val label: String) { All("All"), Updates("Updates"), Installed("Installed") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    vm: AppViewModel,
    sources: List<SourceState>,
    rows: List<AppRow>,
    tasks: Map<String, Task>,
    loading: Boolean,
    filter: Filter,
    onFilter: (Filter) -> Unit,
    onOpen: (String) -> Unit,
    onSources: () -> Unit,
) {
    val gate = rememberInstallGate()
    val updates = rows.count { it.hasUpdate }
    val shown = when (filter) {
        Filter.All -> rows
        Filter.Updates -> rows.filter { it.hasUpdate }
        Filter.Installed -> rows.filter { it.installed != null }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("AppShelf") },
            actions = {
                IconButton(onClick = vm::refresh, enabled = !loading) { Icon(Icons.Default.Refresh, "Check for updates") }
            },
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Filter.entries.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { onFilter(f) },
                    label = { Text(if (f == Filter.Updates && updates > 0) "Updates ($updates)" else f.label) },
                )
            }
            Spacer(Modifier.weight(1f))
            if (updates > 0) {
                Button(onClick = { gate.run(vm::updateAll) }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(Icons.Default.SystemUpdate, null, Modifier.padding(end = 4.dp))
                    Text("Update all")
                }
            }
        }
        if (loading && rows.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))

        PullToRefreshBox(isRefreshing = loading && rows.isNotEmpty(), onRefresh = vm::refresh, modifier = Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                for (state in sources) {
                    val inSource = shown.filter { it.app.source == state.source }
                    if (inSource.isEmpty() && state.error == null && filter != Filter.All) continue
                    item(key = "h:" + state.source.key) {
                        SourceHeader(state, onSources)
                    }
                    items(inSource, key = { it.app.id }) { row ->
                        AppListRow(row, tasks[row.app.id], vm, gate, onOpen)
                    }
                }
                if (!loading && shown.isEmpty()) {
                    item {
                        Text(
                            when {
                                sources.isEmpty() -> "No repositories yet. Add one on the Sources tab."
                                filter == Filter.Updates -> "Everything is up to date."
                                filter == Filter.Installed -> "None of these apps are installed yet."
                                else -> "No apps found."
                            },
                            Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceHeader(state: SourceState, onSources: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
        SectionTitle(state.source.key)
        val problem = state.error ?: state.warning
        if (problem != null) {
            Row(Modifier.clickable(onClick = onSources).padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.WarningAmber, null, Modifier.padding(end = 4.dp),
                    tint = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                )
                Text(problem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AppListRow(row: AppRow, task: Task?, vm: AppViewModel, gate: InstallGate, onOpen: (String) -> Unit) {
    val latest = row.app.latest
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(row.app.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(row, vm)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(row.app.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val status = when {
                row.installed == null -> "${latest.version} · ${formatSize(latest.apk.size)}"
                row.hasUpdate -> "${row.installed.versionName} → ${latest.version}"
                else -> "Installed ${row.installed.versionName}"
            }
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = if (row.hasUpdate) Palette.update else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                "Released " + formatWhen(latest.release.publishedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        AppAction(row, task, vm, gate)
    }
}
