package io.github.kkrampis.searchvault.ui

import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.kkrampis.searchvault.model.Archive
import io.github.kkrampis.searchvault.model.Engine
import org.json.JSONArray
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableIntStateOf
import io.github.kkrampis.searchvault.model.PageCapture
import io.github.kkrampis.searchvault.model.PageText
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** A question handed to the app from outside (share, text selection), to run once. */
data class Incoming(val text: String, val nonce: Long = System.nanoTime())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskScreen(
    vm: SearchViewModel,
    archive: Archive,
    settings: SettingsUiState,
    currentId: String?,
    asking: String?,
    webView: WebView,
    incoming: Incoming?,
    onIncomingHandled: () -> Unit,
    openSettings: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                Engine.entries.forEachIndexed { i, e ->
                    SegmentedButton(
                        selected = settings.engine == e,
                        onClick = { vm.setEngine(e) },
                        shape = SegmentedButtonDefaults.itemShape(i, Engine.entries.size),
                        icon = {},
                    ) { Text(if (e == Engine.Gemini) "Gemini (key)" else "AI Mode") }
                }
            }
            IconButton(onClick = { vm.newSearch() }) { Icon(Icons.Default.AddComment, "New search") }
        }
        when (settings.engine) {
            Engine.Gemini -> GeminiPane(vm, archive, settings, currentId, asking, incoming, onIncomingHandled, openSettings)
            Engine.AiMode -> AiModePane(vm, webView, settings.privateMode, incoming, onIncomingHandled)
        }
    }
}

@Composable
private fun GeminiPane(
    vm: SearchViewModel,
    archive: Archive,
    settings: SettingsUiState,
    currentId: String?,
    asking: String?,
    incoming: Incoming?,
    onIncomingHandled: () -> Unit,
    openSettings: () -> Unit,
) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val search = archive.searches.firstOrNull { it.id == currentId }
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val itemCount = (search?.turns?.size ?: 0) + (if (asking != null) 1 else 0)

    LaunchedEffect(incoming) {
        val q = incoming?.text ?: return@LaunchedEffect
        vm.newSearch()
        vm.ask(q)
        onIncomingHandled()
    }
    LaunchedEffect(itemCount) { if (itemCount > 0) listState.animateScrollToItem(itemCount - 1) }

    Column(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (itemCount == 0) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Ask anything", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Gemini searches Google and answers with sources. Every search is saved on this phone " +
                            "and synced to JSONBin when sync is set up.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (settings.geminiKey.isBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            modifier = Modifier.padding(top = 24.dp).fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Gemini needs a free API key", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Create one in Google AI Studio and paste it in Settings. Or switch to AI Mode above, " +
                                        "which opens Google's AI Mode page and needs no key.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { runCatching { uri.openUri(AI_STUDIO_KEYS) } }) { Text("Get a key") }
                                    androidx.compose.material3.OutlinedButton(onClick = openSettings) { Text("Settings") }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (search != null) {
                        item(key = "header") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (search.turns.size > 1) "${search.turns.size} questions · saved" else "Saved",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { vm.toggleStar(search.id) }) {
                                    Icon(
                                        Icons.Default.Bookmark,
                                        "Star",
                                        tint = if (search.starred) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                                    )
                                }
                                IconButton(onClick = { vm.markdown(search.id)?.let { shareText(context, search.title, it) } }) {
                                    Icon(Icons.Default.Share, "Share")
                                }
                            }
                        }
                        items(search.turns, key = { it.at + it.question }) { TurnView(it) }
                    }
                    if (asking != null) {
                        item(key = "asking") {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                QuestionBubble(asking)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.size(12.dp))
                                    Text("Searching Google…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        QuestionInput(
            value = input,
            onValueChange = { input = it },
            placeholder = if (search != null) "Ask a follow-up" else "Ask Google AI",
            enabled = asking == null,
            onSend = {
                vm.ask(input)
                input = ""
            },
        )
    }
}

@Composable
private fun QuestionInput(value: String, onValueChange: (String) -> Unit, placeholder: String, enabled: Boolean, onSend: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            maxLines = 5,
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (enabled && value.isNotBlank()) onSend() }),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(8.dp))
        FilledIconButton(onClick = onSend, enabled = enabled && value.isNotBlank()) {
            Icon(Icons.AutoMirrored.Filled.Send, "Search")
        }
    }
}

/** Where the AI Mode page is, shown above it. */
private enum class PageStatus { Idle, Loading, Waiting, Saved, Consent, Captcha, SignIn }

@Composable
private fun AiModePane(
    vm: SearchViewModel,
    webView: WebView,
    privateMode: Boolean,
    incoming: Incoming?,
    onIncomingHandled: () -> Unit,
) {
    val uri = LocalUriHandler.current
    var input by rememberSaveable { mutableStateOf("") }
    var lastQuery by rememberSaveable { mutableStateOf("") }
    var pageLoads by rememberSaveable { mutableIntStateOf(0) }
    var lastSavedHash by rememberSaveable { mutableIntStateOf(0) }
    var savedAt by rememberSaveable { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf(if (lastQuery.isEmpty()) PageStatus.Idle else PageStatus.Waiting) }
    var loading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(webView.canGoBack()) }

    /** Reads the page; gives the parsed capture, the question it answers and the page address. */
    fun capture(onResult: (PageCapture, String, String) -> Unit) {
        webView.evaluateJavascript(PrivateWeb.CAPTURE_JS) { raw ->
            val pageUrl = webView.url.orEmpty()
            // Follow-ups asked inside the page can change the q= parameter.
            val q = runCatching { Uri.parse(pageUrl).getQueryParameter("q") }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: lastQuery
            val json = runCatching { JSONArray("[$raw]").getString(0) }.getOrDefault("")
            onResult(PageText.parse(json, q), q, pageUrl)
        }
    }

    fun save(c: PageCapture, q: String, manual: Boolean) {
        vm.saveAiMode(q, c.text, aiModeUrl(q), c.links, quiet = !manual)
        lastSavedHash = c.text.hashCode()
        savedAt = java.time.Instant.now().toString()
        status = PageStatus.Saved
    }

    fun run(q: String) {
        val query = q.trim()
        if (query.isEmpty()) return
        lastQuery = query
        lastSavedHash = 0
        savedAt = null
        status = PageStatus.Loading
        vm.newSearch()
        // The question is saved straight away; the answer is added once Google has finished writing it.
        vm.saveAiMode(query, "", aiModeUrl(query), emptyList(), quiet = true)
        val load = { webView.loadUrl(aiModeUrl(query)) }
        if (privateMode) PrivateWeb.wipe(webView, load) else load()
    }

    DisposableEffect(webView) {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host.orEmpty()
                if (PrivateWeb.isSignIn(host)) {
                    status = PageStatus.SignIn
                    return true
                }
                // Google pages stay here; result links open in the browser.
                if (host.contains("google.") || host.endsWith("gstatic.com") || request.url.scheme !in listOf("http", "https")) return false
                runCatching { uri.openUri(request.url.toString()) }
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { loading = true }

            override fun onPageFinished(view: WebView, url: String?) {
                loading = false
                canGoBack = view.canGoBack()
                pageLoads++
            }
        }
        onDispose { }
    }

    // Reads the page every 1.5 s. Once the answer text has stopped changing for about 3 s it is
    // saved; if it changes again (a follow-up asked in the page), the newer text is saved too.
    LaunchedEffect(pageLoads) {
        if (pageLoads == 0) return@LaunchedEffect
        var previous = ""
        var stable = 0
        val until = System.currentTimeMillis() + 10 * 60_000
        while (System.currentTimeMillis() < until) {
            delay(1500)
            val (c, q, pageUrl) = suspendCancellableCoroutine<Triple<PageCapture, String, String>> { cont ->
                capture { c, q, u -> if (cont.isActive) cont.resume(Triple(c, q, u)) }
            }
            when (c.state) {
                PageCapture.State.Consent -> { status = PageStatus.Consent; continue }
                PageCapture.State.Captcha -> { status = PageStatus.Captcha; continue }
                PageCapture.State.SignIn -> { status = PageStatus.SignIn; continue }
                PageCapture.State.Ok -> Unit
            }
            if (!pageUrl.contains("/search")) continue
            if (c.text.length < 40) {
                if (status != PageStatus.Saved) status = PageStatus.Waiting
                previous = c.text
                stable = 0
                continue
            }
            if (c.text == previous) stable++ else {
                previous = c.text
                stable = 0
                if (c.text.hashCode() != lastSavedHash) status = PageStatus.Waiting
            }
            if (stable >= 2 && c.text.hashCode() != lastSavedHash) save(c, q, manual = false)
        }
    }

    LaunchedEffect(incoming) {
        val q = incoming?.text ?: return@LaunchedEffect
        input = q
        run(q)
        onIncomingHandled()
    }

    BackHandler(enabled = canGoBack) {
        webView.goBack()
        canGoBack = webView.canGoBack()
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Search with AI Mode") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { run(input) }),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { webView.reload() }, enabled = lastQuery.isNotEmpty()) { Icon(Icons.Default.Refresh, "Reload") }
        }
        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (status != PageStatus.Idle) PageStatusRow(status, savedAt, privateMode)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { webView.also { (it.parent as? ViewGroup)?.removeView(it) } },
                modifier = Modifier.fillMaxSize(),
            )
            if (lastQuery.isEmpty() && webView.url == null) {
                Column(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                    Text("Google AI Mode", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
                    Text(
                        if (privateMode) {
                            "No account, no key. Google's cookies and site data are wiped before every search, " +
                                "so each one looks like a new visitor. The answer and its links are read from the page " +
                                "and saved automatically when Google finishes writing."
                        } else {
                            "No key needed. The answer and its links are read from the page and saved automatically " +
                                "when Google finishes writing. Private mode is off in Settings."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        if (lastQuery.isNotEmpty()) {
            OutlinedButton(
                onClick = {
                    capture { c, q, _ ->
                        when {
                            c.state != PageCapture.State.Ok || c.text.isBlank() ->
                                vm.saveAiMode(q, "", aiModeUrl(q), c.links)
                            else -> save(c, q, manual = true)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Default.Bookmark, null)
                Spacer(Modifier.size(8.dp))
                Text("Save now")
            }
        }
    }
}

@Composable
private fun PageStatusRow(status: PageStatus, savedAt: String?, privateMode: Boolean) {
    val (text, isProblem) = when (status) {
        PageStatus.Idle -> "" to false
        PageStatus.Loading -> (if (privateMode) "Private search: cookies wiped, loading…" else "Loading…") to false
        PageStatus.Waiting -> "Waiting for Google to finish the answer…" to false
        PageStatus.Saved -> "Answer saved ${formatIso(savedAt).substringAfterLast(", ")}" to false
        PageStatus.Consent -> "Google is asking about cookies. Choose Reject all in the page." to true
        PageStatus.Captcha -> "Google is showing a captcha (it limits anonymous searches). Solve it, or try again later." to true
        PageStatus.SignIn -> "Signing in is blocked here to keep searches anonymous." to true
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isProblem) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Icon(
            when (status) {
                PageStatus.Saved -> Icons.Default.CheckCircle
                PageStatus.Consent, PageStatus.Captcha, PageStatus.SignIn -> Icons.Default.Warning
                else -> Icons.Default.Shield
            },
            null,
            tint = if (isProblem) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isProblem) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

const val AI_STUDIO_KEYS = "https://aistudio.google.com/apikey"
