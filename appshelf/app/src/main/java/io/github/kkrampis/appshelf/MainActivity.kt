package io.github.kkrampis.appshelf

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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kkrampis.appshelf.data.RepoUrl
import io.github.kkrampis.appshelf.ui.AppDetailScreen
import io.github.kkrampis.appshelf.ui.AppShelfTheme
import io.github.kkrampis.appshelf.ui.AppViewModel
import io.github.kkrampis.appshelf.ui.AppsScreen
import io.github.kkrampis.appshelf.ui.Filter
import io.github.kkrampis.appshelf.ui.SettingsScreen
import io.github.kkrampis.appshelf.ui.SourcesScreen
import io.github.kkrampis.appshelf.work.UpdateWorker
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    /** A repository link shared to the app, shown filled in on the Sources tab. */
    private val sharedRepo = MutableStateFlow<String?>(null)
    private val showUpdates = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)

        setContent {
            AppShelfTheme {
                val sources by vm.sources.collectAsStateWithLifecycle()
                val rows by vm.rows.collectAsStateWithLifecycle()
                val tasks by vm.tasks.collectAsStateWithLifecycle()
                val loading by vm.loading.collectAsStateWithLifecycle()
                val message by vm.message.collectAsStateWithLifecycle()
                val settings by vm.settings.collectAsStateWithLifecycle()
                val shared by sharedRepo.collectAsStateWithLifecycle()
                val showUpdatesTick by showUpdates.collectAsStateWithLifecycle()

                var tab by rememberSaveable { mutableIntStateOf(0) }
                var openApp by rememberSaveable { mutableStateOf<String?>(null) }
                var filter by rememberSaveable { mutableStateOf(Filter.All) }
                val snackbar = remember { SnackbarHostState() }

                LaunchedEffect(message) {
                    val m = message ?: return@LaunchedEffect
                    snackbar.currentSnackbarData?.dismiss()
                    vm.clearMessage()
                    snackbar.showSnackbar(m, withDismissAction = true)
                }
                LaunchedEffect(shared) { if (shared != null) { tab = 1; openApp = null } }
                LaunchedEffect(showUpdatesTick) {
                    if (showUpdatesTick > 0) { tab = 0; openApp = null; filter = Filter.Updates }
                }
                // Apps may have been installed or removed while AppShelf was in the background.
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshInstalled() }
                BackHandler(enabled = openApp != null) { openApp = null }

                val updates = rows.count { it.hasUpdate }
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        NavigationBar {
                            tabs.forEachIndexed { i, (label, icon) ->
                                NavigationBarItem(
                                    selected = tab == i && openApp == null,
                                    onClick = { tab = i; openApp = null },
                                    icon = {
                                        if (i == 0 && updates > 0) {
                                            BadgedBox(badge = { Badge { Text("$updates") } }) { Icon(icon, null) }
                                        } else {
                                            Icon(icon, null)
                                        }
                                    },
                                    label = { Text(label) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                        val detail = openApp?.let { id -> rows.firstOrNull { it.app.id == id } }
                        if (detail != null) {
                            AppDetailScreen(vm, detail, tasks[detail.app.id]) { openApp = null }
                        } else {
                            when (tab) {
                                0 -> AppsScreen(
                                    vm, sources, rows, tasks, loading, filter,
                                    onFilter = { filter = it },
                                    onOpen = { openApp = it },
                                    onSources = { tab = 1 },
                                )
                                1 -> SourcesScreen(vm, sources, shared.orEmpty())
                                else -> SettingsScreen(vm, settings)
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

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(UpdateWorker.EXTRA_SHOW_UPDATES, false) == true) {
            showUpdates.value += 1
            return
        }
        if (intent?.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            val repo = RepoUrl.findIn(text) ?: RepoUrl.parse(text)
            if (repo != null) sharedRepo.value = repo.url
            else vm.say("That link isn't a GitHub repository.")
        }
    }

    private companion object {
        val tabs = listOf(
            "Apps" to Icons.Default.Apps,
            "Sources" to Icons.Default.Source,
            "Settings" to Icons.Default.Settings,
        )
    }
}
