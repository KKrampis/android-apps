package io.github.kkrampis.searchvault

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kkrampis.searchvault.model.Engine
import io.github.kkrampis.searchvault.ui.AskScreen
import io.github.kkrampis.searchvault.ui.HistoryScreen
import io.github.kkrampis.searchvault.ui.Incoming
import io.github.kkrampis.searchvault.ui.SearchVaultTheme
import io.github.kkrampis.searchvault.ui.SearchViewModel
import io.github.kkrampis.searchvault.ui.SettingsScreen
import io.github.kkrampis.searchvault.ui.StatusKind
import io.github.kkrampis.searchvault.ui.SyncScreen
import io.github.kkrampis.searchvault.ui.PrivateWeb

class MainActivity : ComponentActivity() {
    private val vm: SearchViewModel by viewModels()
    private val incoming = mutableStateOf<Incoming?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)

        setContent {
            SearchVaultTheme {
                val archive by vm.archive.collectAsStateWithLifecycle()
                val settings by vm.settingsState.collectAsStateWithLifecycle()
                val sync by vm.sync.collectAsStateWithLifecycle()
                val currentId by vm.currentId.collectAsStateWithLifecycle()
                val asking by vm.asking.collectAsStateWithLifecycle()
                val status by vm.status.collectAsStateWithLifecycle()
                var tab by rememberSaveable { mutableIntStateOf(0) }
                val snackbar = remember { SnackbarHostState() }
                // One WebView for AI Mode, kept while switching tabs so the page is not reloaded.
                val context = LocalContext.current
                val webView = remember {
                    // Start every app session as a new, unknown visitor to Google.
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
                LaunchedEffect(incoming.value) { if (incoming.value != null) tab = 0 }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        NavigationBar {
                            tabs.forEachIndexed { i, (label, icon) ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    icon = { Icon(icon, null) },
                                    label = { Text(label) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                        when (tab) {
                            0 -> AskScreen(
                                vm = vm,
                                archive = archive,
                                settings = settings,
                                currentId = currentId,
                                asking = asking,
                                webView = webView,
                                incoming = incoming.value,
                                onIncomingHandled = { incoming.value = null },
                                openSettings = { tab = 3 },
                            )
                            1 -> HistoryScreen(vm, archive) { search ->
                                vm.setEngine(Engine.Gemini)
                                vm.open(search.id)
                                tab = 0
                            }
                            2 -> SyncScreen(vm, archive, sync)
                            else -> SettingsScreen(vm, settings)
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

    /** Questions shared from other apps, picked from selected text, or sent as a web search. */
    private fun handle(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            Intent.ACTION_WEB_SEARCH, Intent.ACTION_SEARCH -> intent.getStringExtra(SearchManager.QUERY)
            else -> null
        }?.trim()
        if (!text.isNullOrEmpty()) incoming.value = Incoming(text.take(2000))
    }

    private companion object {
        val tabs = listOf(
            "Ask" to Icons.Default.AutoAwesome,
            "Saved" to Icons.Default.History,
            "Sync" to Icons.Default.Sync,
            "Settings" to Icons.Default.Settings,
        )
    }
}
