package io.github.kkrampis.stockvault.ui

import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import io.github.kkrampis.stockvault.ai.ClaudeApp
import io.github.kkrampis.stockvault.ai.Prompts
import io.github.kkrampis.stockvault.ai.Tickers
import io.github.kkrampis.stockvault.model.Library
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.coroutines.resume

fun aiModeUrl(q: String) = "https://www.google.com/search?udm=50&q=" + URLEncoder.encode(q, "UTF-8")

private val starterPrompts = listOf(
    "Stocks similar to NVIDIA but smaller and cheaper",
    "Dividend stocks with 10+ years of dividend growth and low debt",
    "Which sectors did best this month and why?",
    "ETFs that track AI infrastructure companies",
)

@Composable
fun AiScreen(vm: AppViewModel, library: Library, webView: WebView, settings: SettingsUiState) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val incoming by vm.incoming.collectAsStateWithLifecycle()
    val aiQuery by vm.aiModeQuery.collectAsStateWithLifecycle()
    val turns by vm.claude.collectAsStateWithLifecycle()
    val busy by vm.claudeBusy.collectAsStateWithLifecycle()
    LaunchedEffect(aiQuery) { if (aiQuery != null) mode = 0 }
    LaunchedEffect(busy, turns.size) { if (busy != null) mode = 1 }

    Column(Modifier.fillMaxSize().imePadding()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            listOf("Google AI Mode", "Claude").forEachIndexed { i, label ->
                SegmentedButton(selected = mode == i, onClick = { mode = i }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
            }
        }
        incoming?.let { text -> IncomingCard(vm, library, text) }
        if (mode == 0) AiModePane(vm, library, webView, settings.privateMode, aiQuery) else ClaudePane(vm, library, turns, busy, settings)
    }
}

/** Text shared to the app, usually an answer from the Claude app, with the stocks found in it. */
@Composable
private fun IncomingCard(vm: AppViewModel, library: Library, text: String) {
    val tickers = remember(text) { Tickers.extract(text) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Shared to StockVault", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = vm::incomingHandled) { Icon(Icons.Default.Close, "Dismiss") }
            }
            Text(text, maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            if (tickers.isEmpty()) {
                SmallLabel("No stock symbols found in it.", Modifier.padding(top = 4.dp))
            } else {
                TickerChips(vm, library, tickers, "Stocks in it")
            }
            TextButton(onClick = { vm.runAiMode(text.take(500)) }) { Text("Search this with Google AI Mode") }
        }
    }
}

/** Buttons for the symbols an answer mentions: tap to open, or save them all to a folder. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TickerChips(vm: AppViewModel, library: Library, tickers: List<String>, title: String) {
    var saveAll by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallLabel("$title (${tickers.size})", Modifier.weight(1f))
            TextButton(onClick = { saveAll = true }) { Icon(Icons.Default.BookmarkAdd, null, Modifier.size(18.dp)); Text(" Save all") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tickers.forEach { t -> SuggestionChip(onClick = { vm.openStock(t) }, label = { Text(t) }) }
        }
    }
    if (saveAll) {
        FolderPickerDialog(library, "Save ${tickers.size} stocks to…", onDismiss = { saveAll = false },
            onNewFolder = { n, p -> vm.addFolder(n, p) }) { folder ->
            tickers.forEach { vm.saveStock(it, "", folder, null, "") }
            vm.say(StatusKind.Ok, "Saved ${tickers.size} stocks in ${library.pathOf(folder)}")
        }
    }
}

private enum class PageState { Idle, Loading, Ready, Consent, Captcha }

@Composable
private fun AiModePane(vm: AppViewModel, library: Library, webView: WebView, privateMode: Boolean, request: String?) {
    val uri = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    var lastQuery by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf(PageState.Idle) }
    var pageLoads by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(webView.canGoBack()) }
    var tickers by remember { mutableStateOf<List<String>>(emptyList()) }

    fun run(q: String) {
        val query = q.trim()
        if (query.isEmpty()) return
        input = query
        lastQuery = query
        tickers = emptyList()
        state = PageState.Loading
        val load = { webView.loadUrl(aiModeUrl(query)) }
        if (privateMode) PrivateWeb.wipe(webView, load) else load()
    }

    DisposableEffect(webView) {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host.orEmpty()
                if (PrivateWeb.isSignIn(host)) return true
                if (host.contains("google.") || host.endsWith("gstatic.com") || request.url.scheme !in listOf("http", "https")) return false
                openUrl(uri, request.url.toString())
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

    // Reads the answer while Google writes it and picks out the stock symbols.
    LaunchedEffect(pageLoads) {
        if (pageLoads == 0) return@LaunchedEffect
        val until = System.currentTimeMillis() + 3 * 60_000
        var previous = ""
        var stable = 0
        while (System.currentTimeMillis() < until && stable < 4) {
            delay(1500)
            val raw = suspendCancellableCoroutine { cont ->
                webView.evaluateJavascript(PrivateWeb.CAPTURE_JS) { if (cont.isActive) cont.resume(it) }
            }
            val json = runCatching { JSONObject(JSONArray("[$raw]").getString(0)) }.getOrNull() ?: continue
            when (json.optString("state")) {
                "consent" -> { state = PageState.Consent; continue }
                "captcha" -> { state = PageState.Captcha; continue }
            }
            val text = json.optString("text")
            if (text.length > 40) state = PageState.Ready
            if (text == previous) stable++ else { previous = text; stable = 0 }
            val found = Tickers.extract(text)
            if (found.isNotEmpty()) tickers = found
        }
    }

    LaunchedEffect(request) {
        val q = request ?: return@LaunchedEffect
        run(q)
        vm.aiModeHandled()
    }

    BackHandler(enabled = canGoBack) {
        webView.goBack()
        canGoBack = webView.canGoBack()
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Ask Google AI Mode about stocks") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { run(input) }),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { webView.reload() }, enabled = lastQuery.isNotEmpty()) { Icon(Icons.Default.Refresh, "Reload") }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
        when (state) {
            PageState.Consent -> SmallLabel("Google is asking about cookies: choose \"Reject all\" in the page.", Modifier.padding(horizontal = 16.dp))
            PageState.Captcha -> SmallLabel("Google wants to check you're not a robot. Solve it in the page, or try again later.", Modifier.padding(horizontal = 16.dp))
            else -> Unit
        }
        if (tickers.isNotEmpty()) {
            Box(Modifier.padding(horizontal = 16.dp)) { TickerChips(vm, library, tickers, "Stocks in this answer") }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp)) {
            AndroidView(factory = { webView.also { (it.parent as? ViewGroup)?.removeView(it) } }, modifier = Modifier.fillMaxSize())
            if (lastQuery.isEmpty() && webView.url == null) {
                Column(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(16.dp),
                ) {
                    Text("Search with Google's AI Mode", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Runs in a private view: no Google account, cookies wiped before every search" +
                            (if (privateMode) "" else " (turned off in Settings)") + ". Stock symbols in the answer " +
                            "appear as buttons you can open or save.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    starterPrompts.forEach { p ->
                        AssistChip(onClick = { run(p) }, label = { Text(p) }, modifier = Modifier.padding(vertical = 2.dp))
                    }
                    SmallLabel("Tip: on any stock, open the Similar tab to search for stocks like it.", Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClaudePane(vm: AppViewModel, library: Library, turns: List<ClaudeTurn>, busy: String?, settings: SettingsUiState) {
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    val hasKey = settings.claudeKey.isNotBlank()
    val installed = remember { ClaudeApp.isInstalled(context) }

    fun send(followUp: Boolean) {
        if (input.isBlank()) return
        if (hasKey) vm.askClaude(input, followUp) else ClaudeApp.ask(context, Prompts.withTickerLine(input))
        input = ""
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (!hasKey) {
                Section("Two ways to use Claude", note = null) {
                    Text(
                        "Android doesn't let one app read another app's answers, and the Claude app has no way to send " +
                            "results back on its own. So StockVault offers the closest things:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "1. Claude app: questions open in Claude" + (if (installed) "" else " (not installed on this phone)") +
                            ". When Claude has answered, tap Share on the answer and pick StockVault, or copy it and tap " +
                            "Paste below. StockVault reads the stock symbols out of it so you can open or save them.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "2. Built in: add your own Anthropic API key in Settings and answers come straight back into this " +
                            "screen, with web search, sources and stock buttons. API use is billed to your Anthropic account.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            turns.forEach { t ->
                Section(t.question.lineSequence().first().take(140), note = "Claude · ${t.answer.model}") {
                    SelectionContainer { Text(plain(t.answer.text), style = MaterialTheme.typography.bodyMedium) }
                    if (t.answer.tickers.isNotEmpty()) TickerChips(vm, library, t.answer.tickers, "Stocks mentioned")
                    if (t.answer.sources.isNotEmpty()) {
                        SmallLabel("Sources", Modifier.padding(top = 8.dp))
                        t.answer.sources.take(8).forEach { s ->
                            Text(
                                "${s.title} · ${hostOf(s.url)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().clickable { openUrl(context, s.url) }.padding(vertical = 3.dp),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { copyText(context, t.answer.text) }) { Text("Copy") }
                        TextButton(onClick = { shareText(context, t.question.take(80), t.answer.text) }) { Text("Share") }
                    }
                }
            }
            if (busy != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("  Claude is researching: ${busy.lineSequence().first().take(80)}…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (turns.isNotEmpty() && busy == null) {
                TextButton(onClick = vm::clearClaude, modifier = Modifier.padding(horizontal = 8.dp)) { Text("New conversation") }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AssistChip(
                onClick = {
                    val text = readClipboard(context)
                    if (text.isNullOrBlank()) vm.say(StatusKind.Info, "The clipboard is empty. Copy Claude's answer first.")
                    else vm.receiveShared(text)
                },
                label = { Text("Paste Claude's answer") },
                leadingIcon = { Icon(Icons.Default.ContentPaste, null, Modifier.size(18.dp)) },
            )
            AssistChip(
                onClick = { ClaudeApp.ask(context, Prompts.withTickerLine(input.ifBlank { "Find interesting stocks to research this week" })) },
                label = { Text("Open in Claude app") },
                leadingIcon = { Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp)) },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(if (hasKey) (if (turns.isEmpty()) "Ask Claude about stocks" else "Ask a follow-up") else "Question for the Claude app") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { send(followUp = turns.isNotEmpty()) }, enabled = input.isNotBlank() && busy == null) {
                Icon(Icons.AutoMirrored.Filled.Send, "Ask")
            }
        }
    }
}

/** Claude writes Markdown; show it readably as plain text. */
private fun plain(md: String): String = md
    .replace(Regex("""\*\*(.+?)\*\*"""), "$1")
    .replace(Regex("""(?m)^#{1,6}\s*"""), "")
    .replace(Regex("""(?m)^\s*[-*]\s+"""), "• ")
    .replace(Regex("""\[(.+?)]\((https?://[^)]+)\)"""), "$1")
