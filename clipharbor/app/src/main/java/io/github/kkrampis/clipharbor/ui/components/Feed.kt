package io.github.kkrampis.clipharbor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.kkrampis.clipharbor.yt.PageResult
import io.github.kkrampis.clipharbor.yt.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** A paged list of videos with loading and error state, owned by a ViewModel. */
class Feed(private val scope: CoroutineScope) {
    var items by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var canLoadMore by mutableStateOf(false)
        private set

    private var next: (suspend () -> PageResult)? = null
    private var first: (suspend () -> PageResult)? = null
    private var job: Job? = null
    private var generation = 0

    fun start(loader: suspend () -> PageResult) {
        first = loader
        job?.cancel()
        items = emptyList()
        next = null
        canLoadMore = false
        run(loader, append = false)
    }

    fun retry() {
        val n = next
        if (items.isNotEmpty() && n != null) run(n, append = true) else first?.let { start(it) }
    }

    fun loadMore() {
        val n = next ?: return
        if (loading || error != null) return
        run(n, append = true)
    }

    private fun run(loader: suspend () -> PageResult, append: Boolean) {
        val gen = ++generation
        error = null
        loading = true
        job = scope.launch {
            try {
                val r = loader()
                if (gen != generation) return@launch
                items = if (append) (items + r.items).distinctBy { it.url } else r.items
                next = r.next
                canLoadMore = r.next != null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen == generation) error = friendlyError(e)
            } finally {
                if (gen == generation) loading = false
            }
        }
    }
}

fun friendlyError(e: Throwable): String {
    val name = e.javaClass.simpleName
    return when {
        name.contains("ReCaptcha") -> "YouTube is asking for a captcha. Wait a bit and try again."
        name.contains("UnknownHost") || name.contains("ConnectException") -> "No connection."
        name.contains("AgeRestricted") -> "This video is age restricted and needs a login."
        name.contains("GeographicRestriction") -> "This video is not available in your country."
        name.contains("ContentNotAvailable") || name.contains("Private") -> "This video is not available."
        else -> e.message?.takeIf { it.isNotBlank() } ?: name
    }
}

@Composable
fun FeedList(
    feed: Feed,
    onOpen: (VideoItem) -> Unit,
    actionsFor: (VideoItem) -> List<MenuAction>,
    modifier: Modifier = Modifier,
    emptyText: String = "Nothing here yet.",
    header: (LazyListScope.() -> Unit)? = null,
) {
    val state = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.layoutInfo.totalItemsCount - 5
        }
    }
    LaunchedEffect(nearEnd, feed.items.size, feed.canLoadMore) {
        if (nearEnd && feed.canLoadMore) feed.loadMore()
    }
    LazyColumn(
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 6.dp),
    ) {
        header?.invoke(this)
        items(feed.items, key = { it.url }) { v ->
            VideoRow(v, onClick = { onOpen(v) }, actions = actionsFor(v))
        }
        item(key = "footer") {
            FeedFooter(
                loading = feed.loading,
                error = feed.error,
                empty = feed.items.isEmpty(),
                emptyText = emptyText,
                onRetry = { feed.retry() },
            )
        }
    }
}

@Composable
fun FeedFooter(loading: Boolean, error: String?, empty: Boolean, emptyText: String, onRetry: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> CircularProgressIndicator()
            error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("Try again") }
            }
            empty -> Text(
                emptyText,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun CenteredMessage(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
