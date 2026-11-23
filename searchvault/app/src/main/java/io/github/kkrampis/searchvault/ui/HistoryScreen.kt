package io.github.kkrampis.searchvault.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kkrampis.searchvault.model.Archive
import io.github.kkrampis.searchvault.model.Engine
import io.github.kkrampis.searchvault.model.Search

/** Strips Markdown and citation marks for one-line previews. */
private fun preview(md: String) = md
    .replace(Regex(" ?\\[\\d+(, ?\\d+)*]"), "")
    .replace(Regex("[#*_`>]+"), "")
    .replace(Regex("\\s+"), " ")
    .trim()

@Composable
fun HistoryScreen(vm: SearchViewModel, archive: Archive, continueSearch: (Search) -> Unit) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val open = archive.searches.firstOrNull { it.id == openId }
    if (open != null) {
        BackHandler { openId = null }
        SearchDetail(vm, open, onBack = { openId = null }, continueSearch = continueSearch)
    } else {
        SearchList(archive, vm) { openId = it }
    }
}

@Composable
private fun SearchList(archive: Archive, vm: SearchViewModel, onOpen: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("") }
    var starredOnly by rememberSaveable { mutableStateOf(false) }
    val shown = remember(archive, filter, starredOnly) {
        val f = filter.trim()
        archive.searches.filter { s ->
            (!starredOnly || s.starred) && (
                f.isEmpty() || s.title.contains(f, true) || s.note.contains(f, true) ||
                    s.turns.any { it.question.contains(f, true) || it.answer.contains(f, true) }
                )
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Saved searches",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        Text(
            "${archive.searches.size} on this phone",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            placeholder = { Text("Search your searches") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp)) {
            FilterChip(
                selected = starredOnly,
                onClick = { starredOnly = !starredOnly },
                label = { Text("Starred") },
                leadingIcon = { Icon(Icons.Default.Bookmark, null, Modifier.size(18.dp)) },
            )
        }
        if (shown.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(40.dp))
                Text(
                    if (archive.searches.isEmpty()) "Your searches will appear here" else "Nothing matches",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), modifier = Modifier.fillMaxSize()) {
            items(shown, key = { it.id }) { s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(s.id) }
                        .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(s.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val snippet = preview(s.lastAnswer)
                        if (snippet.isNotEmpty()) {
                            Text(
                                snippet,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Text(
                            buildString {
                                append(formatIso(s.updatedAt))
                                append(" · ").append(if (s.turns.any { it.engine == Engine.AiMode }) "AI Mode" else "Gemini")
                                if (s.turns.size > 1) append(" · ${s.turns.size} questions")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    IconButton(onClick = { vm.toggleStar(s.id) }) {
                        Icon(
                            if (s.starred) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            "Star",
                            tint = if (s.starred) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchDetail(vm: SearchViewModel, search: Search, onBack: () -> Unit, continueSearch: (Search) -> Unit) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val isAiMode = search.turns.any { it.engine == Engine.AiMode }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(search.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = { vm.toggleStar(search.id) }) {
                    Icon(
                        if (search.starred) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        "Star",
                        tint = if (search.starred) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { vm.markdown(search.id)?.let { shareText(context, search.title, it) } }) {
                    Icon(Icons.Default.Share, "Share")
                }
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (isAiMode) "Ask Gemini about this" else "Ask a follow-up") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Chat, null) },
                        onClick = { menu = false; continueSearch(search) },
                    )
                    DropdownMenuItem(
                        text = { Text("Open in Google AI Mode") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                        onClick = { menu = false; runCatching { uri.openUri(aiModeUrl(search.turns.lastOrNull()?.question ?: search.title)) } },
                    )
                    DropdownMenuItem(
                        text = { Text("Copy as Markdown") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                        onClick = { menu = false; vm.markdown(search.id)?.let { copyText(context, it) } },
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menu = false; renaming = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { menu = false; confirmDelete = true },
                    )
                }
            },
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(search.turns, key = { it.at + it.question }) { TurnView(it) }
            item { Spacer(Modifier.size(24.dp)) }
        }
    }

    if (renaming) {
        var title by remember { mutableStateOf(search.title) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename search") },
            text = { OutlinedTextField(title, { title = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { vm.rename(search.id, title); renaming = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this search?") },
            text = { Text("It is removed from this phone, and from JSONBin at the next sync.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onBack(); vm.delete(search.id) }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
