package io.github.kkrampis.radiorecorder

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.core.content.ContextCompat
import io.github.kkrampis.radiorecorder.engine.RecState
import io.github.kkrampis.radiorecorder.ui.AppViewModel
import io.github.kkrampis.radiorecorder.ui.RadioRecorderTheme
import io.github.kkrampis.radiorecorder.ui.RecordingsScreen
import io.github.kkrampis.radiorecorder.ui.ScheduleScreen
import io.github.kkrampis.radiorecorder.ui.SettingsScreen
import io.github.kkrampis.radiorecorder.ui.StatusColors
import io.github.kkrampis.radiorecorder.ui.StationEditor
import io.github.kkrampis.radiorecorder.ui.StationsScreen

private enum class Tab(val label: String, val icon: ImageVector) {
    STATIONS("Stations", Icons.Filled.Radio),
    SCHEDULE("Schedule", Icons.Filled.Event),
    RECORDINGS("Recordings", Icons.AutoMirrored.Filled.LibraryBooks),
    SETTINGS("Settings", Icons.Filled.Settings),
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private var batteryOptimized = mutableStateOf(false)

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        handleShare(intent)
        setContent { RadioRecorderTheme { Root() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    override fun onResume() {
        super.onResume()
        batteryOptimized.value = !getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        vm.startIfNeeded()
    }

    /** A stream link shared from a browser opens the editor with it filled in. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val url = Regex("https?://\\S+").find(text)?.value ?: return
        vm.newStation(url)
    }

    @SuppressLint("BatteryLife")
    private fun fixBattery() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Root() {
        val editing by vm.editing.collectAsState()
        val toast by vm.toast.collectAsState()
        val status by vm.status.collectAsState()
        var tab by rememberSaveable { mutableIntStateOf(0) }
        val battery by batteryOptimized

        LaunchedEffect(toast) {
            toast?.let {
                Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                vm.toast.value = null
            }
        }

        editing?.let { st ->
            BackHandler { vm.editing.value = null }
            StationEditor(vm, st)
            return
        }

        val current = Tab.entries[tab]
        val recording = status.stations.values.count { it.state == RecState.RECORDING }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (current == Tab.STATIONS) "Radio Recorder" else current.label) },
                    actions = { if (recording > 0) Text("● $recording recording", color = StatusColors.recording, modifier = Modifier.padding(end = 16.dp)) },
                )
            },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEachIndexed { i, t ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(t.icon, null) }, label = { Text(t.label) })
                    }
                }
            },
            floatingActionButton = {
                if (current == Tab.STATIONS) {
                    ExtendedFloatingActionButton(onClick = { vm.newStation() }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add station") })
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad)) {
                when (current) {
                    Tab.STATIONS -> StationsScreen(vm, battery, ::fixBattery)
                    Tab.SCHEDULE -> ScheduleScreen(vm)
                    Tab.RECORDINGS -> RecordingsScreen(vm)
                    Tab.SETTINGS -> SettingsScreen(vm, battery, ::fixBattery)
                }
            }
        }
    }
}

