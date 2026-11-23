package io.github.kkrampis.bookmarkvault

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.ImportExport
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kkrampis.bookmarkvault.model.Bookmark
import io.github.kkrampis.bookmarkvault.model.mobileRoot
import io.github.kkrampis.bookmarkvault.ui.BookmarkDialog
import io.github.kkrampis.bookmarkvault.ui.BookmarkVaultTheme
import io.github.kkrampis.bookmarkvault.ui.ImportSheet
import io.github.kkrampis.bookmarkvault.ui.LibraryScreen
import io.github.kkrampis.bookmarkvault.ui.SettingsScreen
import io.github.kkrampis.bookmarkvault.ui.StatusKind
import io.github.kkrampis.bookmarkvault.ui.SyncScreen
import io.github.kkrampis.bookmarkvault.ui.TransferScreen
import io.github.kkrampis.bookmarkvault.ui.VaultViewModel

/** A page shared in from a browser, waiting to be saved. */
private data class SharedLink(val title: String, val url: String)

class MainActivity : ComponentActivity() {
    private val vm: VaultViewModel by viewModels()
    private val shared = mutableStateOf<SharedLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)

        setContent {
            BookmarkVaultTheme {
                val vault by vm.vault.collectAsStateWithLifecycle()
                val sync by vm.sync.collectAsStateWithLifecycle()
                val deviceName by vm.deviceName.collectAsStateWithLifecycle()
                val pending by vm.pending.collectAsStateWithLifecycle()
                val status by vm.status.collectAsStateWithLifecycle()
                var tab by rememberSaveable { mutableIntStateOf(0) }
                val snackbar = remember { SnackbarHostState() }

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
                    // Screens get the space between the system bars; insets are consumed so a
                    // screen's own top bar does not pad for the status bar a second time.
                    Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                        val none = PaddingValues(0.dp)
                        when (tab) {
                            0 -> LibraryScreen(vm, vault, none)
                            1 -> SyncScreen(vm, vault, sync, none)
                            2 -> TransferScreen(vm, none)
                            else -> SettingsScreen(vm, deviceName, none)
                        }
                    }
                }

                pending?.let { ImportSheet(vm, it) }

                shared.value?.let { link ->
                    BookmarkDialog(
                        heading = "Bookmark this page",
                        initialTitle = link.title,
                        initialUrl = link.url,
                        vault = vault,
                        initialFolderId = vault.mobileRoot()?.id,
                        onDismiss = { shared.value = null },
                    ) { title, url, folderId ->
                        folderId?.let { vm.add(it, Bookmark(title = title, url = url)) }
                        shared.value = null
                        tab = 0
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
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
                val url = Regex("https?://\\S+").find(text)?.value ?: return
                // Browsers put the page title in the subject, or before the link in the text.
                val title = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: text.replace(url, "").trim().trim('-', '—', ':', ' ').ifEmpty { url }
                shared.value = SharedLink(title, url)
            }
            Intent.ACTION_VIEW -> intent.data?.let { uri: Uri -> vm.loadFile(uri) }
        }
    }

    private companion object {
        val tabs = listOf(
            "Bookmarks" to Icons.Default.Bookmarks,
            "Sync" to Icons.Default.Sync,
            "Transfer" to Icons.Default.ImportExport,
            "Settings" to Icons.Default.Settings,
        )
    }
}
