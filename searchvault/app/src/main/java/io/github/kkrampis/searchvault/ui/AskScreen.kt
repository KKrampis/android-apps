package io.github.kkrampis.searchvault.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
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
import androidx.compose.runtime.mutableFloatStateOf
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
import io.github.kkrampis.searchvault.model.Source
import org.json.JSONArray
import org.json.JSONObject

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
                    ) { Text(if (e == Engine.Gemini) "Gemini" else "AI Mode") }
                }
            }
            IconButton(onClick = { vm.newSearch() }) { Icon(Icons.Default.AddComment, "New search") }
        }
        when (settings.engine) {
            Engine.Gemini -> GeminiPane(vm, archive, settings, currentId, asking, incoming, onIncomingHandled, openSettings)
            Engine.AiMode -> AiModePane(vm, archive, currentId, webView, incoming, onIncomingHandled)
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

/** Reads the visible answer and the outside links from Google's page. */
private const val CAPTURE_JS = """
(function () {
  var root = document.querySelector('[role="main"]') || document.querySelector('main') || document.getElementById('rso') || document.body;
  var text = (root.innerText || '').replace(/\n{3,}/g, '\n\n').trim();
  var seen = {}, links = [];
  document.querySelectorAll('a[href^="http"]').forEach(function (a) {
    var u = a.href, h = a.hostname || '';
    if (/(^|\.)google\.|gstatic\.com|youtube\.com\/redirect/.test(h) || seen[u]) return;
    seen[u] = 1;
    var t = (a.innerText || a.getAttribute('aria-label') || h).trim().split('\n')[0].slice(0, 140);
    if (links.length < 25) links.push({ t: t, u: u });
  });
  return JSON.stringify({ text: text.slice(0, 15000), links: links });
})()
"""

@SuppressLint("SetJavaScriptEnabled")
fun createSearchWebView(context: android.content.Context): WebView = WebView(context).apply {
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    // Google serves a reduced page to the WebView user agent; drop the "wv" marker so AI Mode loads.
    settings.userAgentString = settings.userAgentString.replace("; wv)", ")").replace(Regex("Version/\\S+ "), "")
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
}

@Composable
private fun AiModePane(
    vm: SearchViewModel,
    archive: Archive,
    currentId: String?,
    webView: WebView,
    incoming: Incoming?,
    onIncomingHandled: () -> Unit,
) {
    val uri = LocalUriHandler.current
    var input by rememberSaveable { mutableStateOf("") }
    var lastQuery by rememberSaveable { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(1f) }
    var canGoBack by remember { mutableStateOf(webView.canGoBack()) }
    var saving by remember { mutableStateOf(false) }

    fun run(q: String) {
        val query = q.trim()
        if (query.isEmpty()) return
        lastQuery = query
        val url = aiModeUrl(query)
        webView.loadUrl(url)
        vm.newSearch()
        // The question is saved straight away; Save adds the answer once Google has written it.
        vm.saveAiMode(query, "", url, emptyList(), quiet = true)
    }

    DisposableEffect(webView) {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host.orEmpty()
                // Google pages stay here; result links open in the browser.
                if (host.contains("google.") || host.endsWith("gstatic.com") || request.url.scheme !in listOf("http", "https")) return false
                runCatching { uri.openUri(request.url.toString()) }
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { progress = 0.1f }

            override fun onPageFinished(view: WebView, url: String?) {
                progress = 1f
                canGoBack = view.canGoBack()
            }
        }
        onDispose { }
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
            IconButton(onClick = { webView.reload() }) { Icon(Icons.Default.Refresh, "Reload") }
        }
        if (progress < 1f) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { webView.also { (it.parent as? ViewGroup)?.removeView(it) } },
                modifier = Modifier.fillMaxSize(),
            )
            if (webView.url == null && lastQuery.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                    Text("Google AI Mode", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
                    Text(
                        "Google's own AI Mode, no key needed. Each search is saved; tap Save answer when " +
                            "the answer has finished to keep its text and links too.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        if (webView.url != null || lastQuery.isNotEmpty()) {
            Button(
                enabled = !saving,
                onClick = {
                    saving = true
                    webView.evaluateJavascript(CAPTURE_JS) { raw ->
                        saving = false
                        val pageUrl = webView.url.orEmpty()
                        // Follow-ups asked inside the page change the q= parameter.
                        val q = runCatching { Uri.parse(pageUrl).getQueryParameter("q") }.getOrNull()
                            ?.takeIf { it.isNotBlank() } ?: lastQuery
                        val captured = runCatching { JSONObject(JSONArray("[$raw]").getString(0)) }.getOrNull()
                        val text = captured?.optString("text").orEmpty()
                        val links = captured?.optJSONArray("links")?.let { a ->
                            (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Source(it.optString("t"), it.optString("u")) } }
                        }.orEmpty()
                        vm.saveAiMode(q, text, aiModeUrl(q), links)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Default.Bookmark, null)
                Spacer(Modifier.size(8.dp))
                Text("Save answer")
            }
        }
    }
}

const val AI_STUDIO_KEYS = "https://aistudio.google.com/apikey"
