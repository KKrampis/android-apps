package io.github.kkrampis.clipharbor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.kkrampis.clipharbor.ui.screens.ChannelScreen
import io.github.kkrampis.clipharbor.ui.screens.DownloadsScreen
import io.github.kkrampis.clipharbor.ui.screens.HomeScreen
import io.github.kkrampis.clipharbor.ui.screens.LibraryScreen
import io.github.kkrampis.clipharbor.ui.screens.LocalPlayerScreen
import io.github.kkrampis.clipharbor.ui.screens.PlaylistScreen
import io.github.kkrampis.clipharbor.ui.screens.SearchScreen
import io.github.kkrampis.clipharbor.ui.screens.SettingsScreen
import io.github.kkrampis.clipharbor.ui.screens.TranscriptScreen
import io.github.kkrampis.clipharbor.ui.screens.WatchScreen
import io.github.kkrampis.clipharbor.ui.theme.ClipHarborTheme

class MainActivity : ComponentActivity() {
    /** A YouTube link shared into the app, waiting to be opened. */
    private val sharedUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedUrl.value = extractUrl(intent)
        setContent {
            ClipHarborTheme {
                AppNav(sharedUrl.value) { sharedUrl.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractUrl(intent)?.let { sharedUrl.value = it }
    }

    private fun extractUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return Regex("https?://\\S+").find(text)?.value
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Default.Home),
    Tab("search", "Search", Icons.Default.Search),
    Tab("library", "Library", Icons.Default.VideoLibrary),
    Tab("downloads", "Downloads", Icons.Default.Download),
)

fun NavHostController.openVideo(url: String) = navigate("watch/${Uri.encode(url)}")
fun NavHostController.openChannel(url: String) = navigate("channel/${Uri.encode(url)}")
fun NavHostController.openTranscript(url: String, title: String = "") =
    navigate("transcript/${Uri.encode(url)}?title=${Uri.encode(title)}")

@Composable
private fun AppNav(sharedUrl: String?, onSharedConsumed: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = tabs.any { it.route == route }

    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            nav.openVideo(sharedUrl)
            onSharedConsumed()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier
                .padding(pad)
                .consumeWindowInsets(pad),
        ) {
            composable("home") {
                HomeScreen(onOpen = { nav.openVideo(it.url) }, onSettings = { nav.navigate("settings") })
            }
            composable("search") {
                SearchScreen(onOpen = { nav.openVideo(it.url) })
            }
            composable("library") {
                LibraryScreen(
                    onOpen = { nav.openVideo(it.url) },
                    onPlaylist = { nav.navigate("playlist/$it") },
                    onSettings = { nav.navigate("settings") },
                )
            }
            composable("downloads") {
                DownloadsScreen(
                    onPlay = { nav.navigate("local/$it") },
                    onOpenPage = { nav.openVideo(it) },
                    onTranscript = { u, t -> nav.openTranscript(u, t) },
                    onSettings = { nav.navigate("settings") },
                )
            }
            composable("settings") {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
            composable(
                "watch/{url}",
                arguments = listOf(navArgument("url") { type = NavType.StringType }),
            ) { e ->
                val url = Uri.decode(e.arguments?.getString("url").orEmpty())
                WatchScreen(
                    url = url,
                    onOpen = { nav.openVideo(it.url) },
                    onChannel = { nav.openChannel(it) },
                    onTranscript = { u, t -> nav.openTranscript(u, t) },
                )
            }
            composable(
                "channel/{url}",
                arguments = listOf(navArgument("url") { type = NavType.StringType }),
            ) { e ->
                ChannelScreen(
                    url = Uri.decode(e.arguments?.getString("url").orEmpty()),
                    onOpen = { nav.openVideo(it.url) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                "transcript/{url}?title={title}",
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType },
                    navArgument("title") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { e ->
                TranscriptScreen(
                    url = Uri.decode(e.arguments?.getString("url").orEmpty()),
                    title = e.arguments?.getString("title").orEmpty(),
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                "playlist/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { e ->
                PlaylistScreen(
                    id = e.arguments?.getLong("id") ?: 0L,
                    onOpen = { nav.openVideo(it.url) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                "local/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { e ->
                LocalPlayerScreen(id = e.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
            }
        }
    }
}
