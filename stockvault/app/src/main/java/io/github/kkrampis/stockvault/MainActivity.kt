package io.github.kkrampis.stockvault

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kkrampis.stockvault.ui.AiScreen
import io.github.kkrampis.stockvault.ui.AppViewModel
import io.github.kkrampis.stockvault.ui.CompareScreen
import io.github.kkrampis.stockvault.ui.GoogleFinancePageScreen
import io.github.kkrampis.stockvault.ui.MarketsScreen
import io.github.kkrampis.stockvault.ui.PortfolioEditScreen
import io.github.kkrampis.stockvault.ui.PortfoliosScreen
import io.github.kkrampis.stockvault.ui.PrivateWeb
import io.github.kkrampis.stockvault.ui.Route
import io.github.kkrampis.stockvault.ui.SavedScreen
import io.github.kkrampis.stockvault.ui.SettingsScreen
import io.github.kkrampis.stockvault.ui.StatusKind
import io.github.kkrampis.stockvault.ui.StockScreen
import io.github.kkrampis.stockvault.ui.StockVaultTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)

        setContent {
            StockVaultTheme {
                val library by vm.library.collectAsStateWithLifecycle()
                val settings by vm.settingsState.collectAsStateWithLifecycle()
                val sync by vm.sync.collectAsStateWithLifecycle()
                val status by vm.status.collectAsStateWithLifecycle()
                val stack by vm.stack.collectAsStateWithLifecycle()
                val recent by vm.recent.collectAsStateWithLifecycle()
                val showAi by vm.showAi.collectAsStateWithLifecycle()
                var tab by rememberSaveable { mutableIntStateOf(0) }
                val snackbar = remember { SnackbarHostState() }
                val context = LocalContext.current
                // One WebView for AI Mode, kept while switching tabs so the page is not reloaded.
                val webView = remember {
                    PrivateWeb.create(context).also { if (settings.privateMode) PrivateWeb.wipe(it) }
                }

                LaunchedEffect(status) {
                    val s = status ?: return@LaunchedEffect
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(
                        message = (if (s.kind == StatusKind.Error) "✗ " else "") + s.text,
                        withDismissAction = s.kind == StatusKind.Error,
                        duration = if (s.kind == StatusKind.Error) SnackbarDuration.Long else SnackbarDuration.Short,
                    )
                    vm.clearStatus()
                }
                LaunchedEffect(showAi) { if (showAi > 0) tab = 3 }
                BackHandler(enabled = stack.isNotEmpty()) { vm.pop() }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        NavigationBar {
                            tabs.forEachIndexed { i, (label, icon) ->
                                NavigationBarItem(
                                    selected = tab == i && stack.isEmpty(),
                                    onClick = {
                                        tab = i
                                        while (vm.stack.value.isNotEmpty()) vm.pop()
                                    },
                                    icon = { Icon(icon, null) },
                                    label = { Text(label) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                        val top = stack.lastOrNull()
                        if (top != null) {
                            // Each screen keeps its own state while it is on the stack.
                            key(stack.size, top) {
                                when (top) {
                                    is Route.Stock -> StockScreen(vm, top.symbol, top.name, library, settings)
                                    is Route.PortfolioEdit -> PortfolioEditScreen(vm, top, library)
                                    is Route.Compare -> CompareScreen(vm, top, library)
                                    is Route.GoogleFinancePage -> GoogleFinancePageScreen(vm, top.url, top.title)
                                }
                            }
                        } else {
                            when (tab) {
                                0 -> MarketsScreen(vm, settings, recent)
                                1 -> SavedScreen(vm, library)
                                2 -> PortfoliosScreen(vm, library)
                                3 -> AiScreen(vm, library, webView, settings)
                                else -> SettingsScreen(vm, settings, sync, library)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Text shared from another app (an answer from the Claude app, an article) or picked from selected text. */
    private fun handle(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }?.trim()
        if (!text.isNullOrEmpty()) vm.receiveShared(text.take(20_000))
    }

    private companion object {
        val tabs = listOf(
            "Markets" to Icons.AutoMirrored.Filled.ShowChart,
            "Saved" to Icons.Default.Bookmarks,
            "Portfolios" to Icons.Default.PieChart,
            "AI" to Icons.Default.AutoAwesome,
            "Settings" to Icons.Default.Settings,
        )
    }
}
