package io.github.kkrampis.appshelf.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.kkrampis.appshelf.data.Source
import io.github.kkrampis.appshelf.data.SourceState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(vm: AppViewModel, sources: List<SourceState>, initialText: String = "") {
    val context = LocalContext.current
    var text by rememberSaveable(initialText) { mutableStateOf(initialText) }
    var error by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<Source?>(null) }

    fun add() {
        error = vm.addSource(text)
        if (error == null) text = ""
    }

    val err = error
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Sources") })
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "AppShelf lists the APKs published as GitHub Releases in these repositories. " +
                            "Add any repository that publishes Android apps that way.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it; error = null },
                        label = { Text("GitHub repository") },
                        placeholder = { Text("https://github.com/owner/repo") },
                        singleLine = true,
                        isError = err != null,
                        supportingText = if (err != null) {
                            { Text(err) }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { add() }),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    Row(Modifier.padding(top = 4.dp, bottom = 12.dp)) {
                        Button(onClick = { add() }, enabled = text.isNotBlank()) {
                            Icon(Icons.Default.Add, null, Modifier.padding(end = 4.dp))
                            Text("Add")
                        }
                        if (sources.none { it.source.key.equals(Source.DEFAULT.key, ignoreCase = true) }) {
                            OutlinedButton(onClick = { vm.addSource(Source.DEFAULT.key) }, modifier = Modifier.padding(start = 8.dp)) {
                                Text("Add ${Source.DEFAULT.key}")
                            }
                        }
                    }
                }
            }
            itemsIndexed(sources, key = { _, s -> s.source.key }) { i, state ->
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(state.source.key, style = MaterialTheme.typography.titleMedium)
                        val failure = state.error
                        val line = when {
                            failure != null -> failure
                            state.apps.isEmpty() -> state.warning ?: "Not read yet"
                            else -> state.apps.joinToString(", ") { it.name } + (state.warning?.let { "\n$it" } ?: "")
                        }
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { openUrl(context, state.source.releasesUrl) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open on GitHub") }
                    IconButton(enabled = i > 0, onClick = { vm.moveSource(state.source, -1) }) { Icon(Icons.Default.ArrowUpward, "Move up") }
                    IconButton(enabled = i < sources.lastIndex, onClick = { vm.moveSource(state.source, 1) }) { Icon(Icons.Default.ArrowDownward, "Move down") }
                    IconButton(onClick = { removing = state.source }) { Icon(Icons.Default.Delete, "Remove") }
                }
            }
        }
    }

    removing?.let { s ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${s.key}?") },
            text = { Text("Its apps stop showing here. Apps already installed stay on the phone.") },
            confirmButton = { TextButton(onClick = { vm.removeSource(s); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}
