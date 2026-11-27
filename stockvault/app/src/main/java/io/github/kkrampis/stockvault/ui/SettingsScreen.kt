package io.github.kkrampis.stockvault.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.kkrampis.stockvault.ai.ClaudeApi
import io.github.kkrampis.stockvault.ai.ClaudeApp
import io.github.kkrampis.stockvault.model.Library

@Composable
fun SettingsScreen(vm: AppViewModel, settings: SettingsUiState, sync: SyncUiState, library: Library) {
    val context = LocalContext.current
    var claudeKey by remember(settings.claudeKey) { mutableStateOf(settings.claudeKey) }
    var claudeModel by remember(settings.claudeModel) { mutableStateOf(settings.claudeModel) }
    var showKey by remember { mutableStateOf(false) }
    var markets by remember(settings.markets) { mutableStateOf(settings.markets.joinToString(", ")) }
    var benchmark by remember(settings.benchmark) { mutableStateOf(settings.benchmark) }
    var master by remember(sync.masterKey) { mutableStateOf(sync.masterKey) }
    var access by remember(sync.accessKey) { mutableStateOf(sync.accessKey) }
    var binId by remember(sync.binId) { mutableStateOf(sync.binId.orEmpty()) }
    var device by remember(settings.deviceName) { mutableStateOf(settings.deviceName) }
    var cacheBytes by remember { mutableLongStateOf(vm.cache.sizeBytes()) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.writeTo(uri, vm.exportJson())
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFrom(uri)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        Section(
            "Claude",
            note = "Optional. With your own Anthropic API key, Claude answers inside the app, searching the web " +
                "for current figures. Without it, questions open in the Claude app" +
                (if (ClaudeApp.isInstalled(context)) "." else " (not installed on this phone).") +
                " The key stays on this phone, encrypted with the Android Keystore.",
        ) {
            OutlinedTextField(
                claudeKey, { claudeKey = it }, label = { Text("Anthropic API key") }, singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Show key")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                claudeModel, { claudeModel = it }, label = { Text("Model") }, singleLine = true,
                supportingText = { Text("Default: ${ClaudeApi.DEFAULT_MODEL}") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { vm.saveClaude(claudeKey, claudeModel.ifBlank { ClaudeApi.DEFAULT_MODEL }) }) { Text("Save") }
                TextButton(onClick = { openUrl(context, "https://console.anthropic.com/settings/keys") }) {
                    Text("Get a key"); Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                }
            }
        }

        Section("Google AI Mode") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Forget Google after every search")
                    SmallLabel("Wipes Google's cookies and site data before each AI Mode search, so searches can't be linked. Sign-in is always blocked.")
                }
                Switch(settings.privateMode, vm::setPrivateMode)
            }
        }

        Section("Markets and benchmark") {
            OutlinedTextField(
                markets, { markets = it }, label = { Text("Markets tab symbols") },
                supportingText = { Text("Yahoo symbols, comma separated, e.g. ^GSPC, ^FTSE, GC=F, BTC-USD") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                benchmark, { benchmark = it.uppercase() }, label = { Text("Default benchmark") }, singleLine = true,
                supportingText = { Text("Compared with stocks and backtests, e.g. SPY, VT, QQQ") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Button(onClick = {
                vm.setMarkets(markets.split(','))
                vm.setBenchmark(benchmark)
                vm.say(StatusKind.Ok, "Saved")
            }, modifier = Modifier.padding(top = 8.dp)) { Text("Save") }
        }

        Section(
            "Sync with JSONBin.io",
            note = "Keeps folders, saved stocks and portfolios the same on several phones. Uses the same JSONBin " +
                "keys as BookmarkVault and SearchVault, in a bin of its own. " +
                "${library.saved.size} saved stocks, ${library.folders.size} folders, ${library.portfolios.size} portfolios.",
        ) {
            OutlinedTextField(master, { master = it }, label = { Text("Master key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(access, { access = it }, label = { Text("Access key (optional)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Button(onClick = { vm.saveKeys(master, access) }, modifier = Modifier.padding(top = 8.dp)) { Text("Save keys") }
            OutlinedTextField(binId, { binId = it }, label = { Text("Sync ID (Bin ID)") }, singleLine = true,
                supportingText = { Text("Leave empty to create a new bin on the first sync. Paste another phone's ID to share its library.") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { if (binId.isNotBlank() && binId != sync.binId) vm.linkBin(binId) else vm.syncNow() }, enabled = !sync.busy) {
                    Text("Sync now")
                }
                if (sync.busy) CircularProgressIndicator(Modifier.padding(4.dp))
                TextButton(onClick = vm::clearSync) { Text("Forget sync") }
            }
            SmallLabel("Last synced: ${formatIso(sync.lastSyncedAt)}")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("Sync after every change", Modifier.weight(1f))
                Switch(sync.autoSync, vm::setAutoSync)
            }
            OutlinedTextField(device, { device = it }, label = { Text("This phone's name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            TextButton(onClick = { vm.setDeviceName(device) }) { Text("Save name") }
        }

        Section("Backup") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { export.launch("StockVault-backup.json") }) { Text("Export JSON") }
                OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Import JSON") }
            }
            SmallLabel("Importing merges with what's here; nothing is overwritten unless the file has a newer edit.", Modifier.padding(top = 6.dp))
        }

        Section("Price cache", note = "Daily price histories are kept on the phone for 6 hours, and used offline.") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${"%.1f".format(cacheBytes / 1_048_576.0)} MB", Modifier.weight(1f))
                TextButton(onClick = { vm.clearCache(); cacheBytes = vm.cache.sizeBytes() }) { Text("Clear") }
            }
        }

        Section("Where the data comes from") {
            Text(
                "Google Finance has no API, so StockVault uses Yahoo Finance's public endpoints for prices, history " +
                    "(adjusted for splits and dividends), dividends, key stats, company profiles, news and \"people " +
                    "also watch\" lists, and reads the stats table and About box from the Google Finance quote page. " +
                    "Both are unofficial and can change or rate-limit without notice; the app falls back to whatever " +
                    "still works and can always open the Google Finance page itself. Not investment advice.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** The real Google Finance quote page in a browser view, for everything the app doesn't show natively. */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GoogleFinancePageScreen(vm: AppViewModel, url: String, title: String) {
    val context = LocalContext.current
    var progress by remember { mutableIntStateOf(0) }
    val webView = remember {
        PrivateWeb.create(context).apply {
            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
            }
            webViewClient = object : WebViewClient() {}
            android.webkit.CookieManager.getInstance().setCookie("https://www.google.com", "SOCS=CAI; Domain=.google.com; Path=/; Secure")
            loadUrl(url)
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Google Finance · $title") },
            navigationIcon = { IconButton(onClick = vm::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { IconButton(onClick = { openUrl(context, url) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open in browser") } },
        )
        if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        AndroidView(factory = { webView.also { (it.parent as? ViewGroup)?.removeView(it) } }, modifier = Modifier.fillMaxSize())
    }
}
