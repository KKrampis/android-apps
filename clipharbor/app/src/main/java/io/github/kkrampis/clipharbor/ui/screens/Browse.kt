package io.github.kkrampis.clipharbor.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import io.github.kkrampis.clipharbor.ui.components.Feed
import io.github.kkrampis.clipharbor.ui.components.FeedList
import io.github.kkrampis.clipharbor.ui.components.VideoMenuDialogs
import io.github.kkrampis.clipharbor.ui.components.friendlyError
import io.github.kkrampis.clipharbor.ui.components.rememberVideoMenu
import io.github.kkrampis.clipharbor.ui.components.videoActions
import io.github.kkrampis.clipharbor.ui.formatCount
import io.github.kkrampis.clipharbor.yt.ChannelDetails
import io.github.kkrampis.clipharbor.yt.VideoItem
import io.github.kkrampis.clipharbor.yt.YouTube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------- Home ----------

val HomeTopics = listOf("Trending", "Music", "Gaming", "News", "Science", "Cooking", "Travel", "Sports", "Comedy")

class HomeViewModel : ViewModel() {
    val feed = Feed(viewModelScope)
    var topic by mutableStateOf(HomeTopics.first())
        private set

    init {
        select(topic)
    }

    fun select(t: String) {
        topic = t
        feed.start {
            if (t == "Trending") YouTube.trending() else YouTube.search(t)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (VideoItem) -> Unit, onSettings: () -> Unit) {
    val vm: HomeViewModel = viewModel()
    val menu = rememberVideoMenu()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ClipHarbor") },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(HomeTopics) { t ->
                    FilterChip(selected = t == vm.topic, onClick = { vm.select(t) }, label = { Text(t) })
                }
            }
            FeedList(
                feed = vm.feed,
                onOpen = onOpen,
                actionsFor = videoActions(menu),
            )
        }
    }
    VideoMenuDialogs(menu)
}

// ---------- Search ----------

class SearchViewModel : ViewModel() {
    val feed = Feed(viewModelScope)
    var query by mutableStateOf("")
        private set
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    var showingResults by mutableStateOf(false)
        private set
    var recent by mutableStateOf<List<String>>(emptyList())
        private set

    private var suggestJob: Job? = null

    fun onQueryChange(q: String) {
        query = q
        showingResults = false
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            delay(250)
            suggestions = try {
                YouTube.suggestions(q)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun submit(q: String = query) {
        val text = q.trim()
        if (text.isEmpty()) return
        query = text
        suggestJob?.cancel()
        suggestions = emptyList()
        showingResults = true
        recent = (listOf(text) + recent.filter { it != text }).take(8)
        feed.start { YouTube.search(text) }
    }
}

@Composable
fun SearchScreen(onOpen: (VideoItem) -> Unit) {
    val vm: SearchViewModel = viewModel()
    val menu = rememberVideoMenu()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (!vm.showingResults) runCatching { focus.requestFocus() }
    }
    Scaffold { pad ->
        Column(Modifier.padding(pad)) {
            OutlinedTextField(
                value = vm.query,
                onValueChange = vm::onQueryChange,
                placeholder = { Text("Search YouTube") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (vm.query.isNotEmpty()) {
                        IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Default.Close, "Clear") }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    vm.submit()
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .focusRequester(focus),
            )
            if (vm.showingResults) {
                FeedList(
                    feed = vm.feed,
                    onOpen = onOpen,
                    actionsFor = videoActions(menu),
                    emptyText = "No results.",
                )
            } else {
                val rows = if (vm.query.isBlank()) vm.recent else vm.suggestions
                LazyColumn(Modifier.fillMaxSize()) {
                    items(rows) { s ->
                        ListItem(
                            headlineContent = { Text(s) },
                            leadingContent = {
                                Icon(if (vm.query.isBlank()) Icons.Default.History else Icons.Default.Search, null)
                            },
                            modifier = Modifier.clickable {
                                keyboard?.hide()
                                vm.submit(s)
                            },
                        )
                    }
                }
            }
        }
    }
    VideoMenuDialogs(menu)
}

// ---------- Channel ----------

class ChannelViewModel(private val url: String) : ViewModel() {
    val feed = Feed(viewModelScope)
    var details by mutableStateOf<ChannelDetails?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        feed.start {
            try {
                val d = YouTube.channel(url)
                details = d
                d.first
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = friendlyError(e)
                throw e
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(url: String, onOpen: (VideoItem) -> Unit, onBack: () -> Unit) {
    val vm: ChannelViewModel = viewModel(key = "channel:$url") { ChannelViewModel(url) }
    val menu = rememberVideoMenu()
    val d = vm.details
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(d?.name ?: "Channel", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { pad ->
        FeedList(
            feed = vm.feed,
            onOpen = onOpen,
            actionsFor = videoActions(menu),
            modifier = Modifier.padding(pad),
            emptyText = "This channel has no videos.",
            header = {
                if (d != null) {
                    item(key = "channel-header") {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = d.avatar,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape),
                            )
                            Column(Modifier.padding(start = 16.dp)) {
                                Text(d.name, style = MaterialTheme.typography.titleLarge)
                                if (d.subscribers >= 0) {
                                    Text(
                                        "${formatCount(d.subscribers)} subscribers",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
        )
    }
    VideoMenuDialogs(menu)
}
