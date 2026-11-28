package io.github.kkrampis.radiorecorder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.kkrampis.radiorecorder.core.CleanupSettings
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: AppViewModel, batteryOptimized: Boolean, onFixBattery: () -> Unit) {
    val config by vm.config.collectAsState()
    val status by vm.status.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val a = config.archive
    val c = config.cleanup

    var enabled by remember(a) { mutableStateOf(a.enabled) }
    var access by remember(a) { mutableStateOf(a.accessKey) }
    var secret by remember { mutableStateOf("") }
    var prefix by remember(a) { mutableStateOf(a.identifierPrefix) }
    var coll by remember(a) { mutableStateOf(a.defaultCollection) }
    var onFinish by remember(a) { mutableStateOf(a.uploadOnFinish) }
    var wifiOnly by remember(a) { mutableStateOf(a.wifiOnly) }
    var derive by remember(a) { mutableStateOf(a.derive) }
    var testMode by remember(a) { mutableStateOf(a.testMode) }
    var interval by remember(c) { mutableStateOf(n(c.intervalHours)) }
    var retention by remember(c) { mutableStateOf(n(c.retentionHours)) }
    var minFree by remember(c) { mutableStateOf(n(c.minFreeGb)) }
    var unup by remember(c) { mutableStateOf(n(c.deleteUnuploadedAfterHours)) }
    var keyMsg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var checking by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard {
            Text("Internet Archive upload", style = MaterialTheme.typography.titleSmall)
            Hint("Get your keys: log in at archive.org, open archive.org/account/s3.php, tick the box and tap Generate New Keys.")
            TextButton(onClick = { vm.openUrl("https://archive.org/account/s3.php")?.let { runCatching { ctx.startActivity(it) } } }) {
                Text("Open archive.org/account/s3.php")
            }
            SwitchRow("Upload recordings to archive.org", enabled) { enabled = it }
            OutlinedTextField(access, { access = it.trim() }, label = { Text("Access key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                secret, { secret = it.trim() }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text("Secret key") },
                placeholder = { if (a.hasSecret) Text("Saved (type to replace)") },
                visualTransformation = PasswordVisualTransformation(),
                supportingText = { Text("Stored encrypted with the Android Keystore, on this phone only.") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(enabled = !checking, onClick = {
                    checking = true
                    scope.launch {
                        keyMsg = vm.testKeys(access, secret)
                        checking = false
                    }
                }) { Text(if (checking) "Checking…" else "Test keys") }
                if (a.hasSecret) TextButton(onClick = { vm.clearSecret() }) { Text("Forget secret key") }
            }
            keyMsg?.let { (ok, msg) -> Text((if (ok) "✓ " else "✗ ") + msg, color = if (ok) StatusColors.ok else MaterialTheme.colorScheme.error) }
            OutlinedTextField(prefix, { prefix = it }, label = { Text("Identifier prefix") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(coll, { coll = it.trim() }, label = { Text("Default collection") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Each station can use its own collection (station editor). opensource_audio = Community Audio.") })
            SwitchRow("Upload each recording when it finishes", onFinish) { onFinish = it }
            SwitchRow("Upload on Wi-Fi only", wifiOnly) { wifiOnly = it }
            SwitchRow("Let archive.org create other formats", derive) { derive = it }
            SwitchRow("Test mode (test_collection, removed after ~30 days)", testMode) { testMode = it }
            status.uploadError?.let { Text("Last upload error: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }

        SectionCard {
            Text("Storage cleanup", style = MaterialTheme.typography.titleSmall)
            Hint("Each run first uploads everything pending, then deletes local recordings older than the retention time that are already on archive.org (or all old ones if uploading is off).")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField("Run every (hours)", interval, Modifier.weight(1f)) { interval = it }
                NumberField("Keep local (hours)", retention, Modifier.weight(1f)) { retention = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField("Keep free (GB)", minFree, Modifier.weight(1f)) { minFree = it }
                NumberField("Delete un-uploaded after (h, 0 = never)", unup, Modifier.weight(1f)) { unup = it }
            }
            KeyValue("Last run", Fmt.epoch(status.lastCleanupMs))
            KeyValue("Next run", Fmt.epoch(status.nextCleanupMs))
            status.lastCleanup?.let {
                KeyValue("Last result", "${it.uploaded} uploaded, ${it.deleted} deleted (${Fmt.bytes(it.freedBytes)}), ${it.keptUnuploaded} kept (not uploaded yet)")
            }
            OutlinedButton(onClick = vm::runCleanup) { Text("Run cleanup now") }
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                vm.saveSettings(
                    a.copy(
                        enabled = enabled, accessKey = access, identifierPrefix = prefix.ifBlank { "radiorec" },
                        defaultCollection = coll.ifBlank { "opensource_audio" }, uploadOnFinish = onFinish, wifiOnly = wifiOnly,
                        derive = derive, testMode = testMode,
                    ),
                    secret.ifBlank { null },
                    CleanupSettings(
                        intervalHours = (interval.toDoubleOrNull() ?: 24.0).coerceAtLeast(0.25),
                        retentionHours = (retention.toDoubleOrNull() ?: 24.0).coerceAtLeast(0.0),
                        minFreeGb = (minFree.toDoubleOrNull() ?: 1.0).coerceAtLeast(0.0),
                        deleteUnuploadedAfterHours = (unup.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
                    ),
                )
                secret = ""
            },
        ) { Text("Save settings") }

        SectionCard {
            Text("Background recording", style = MaterialTheme.typography.titleSmall)
            KeyValue("Battery", if (batteryOptimized) "optimised: Android may pause recording" else "unrestricted ✓")
            if (batteryOptimized) OutlinedButton(onClick = onFixBattery) { Text("Allow unrestricted battery use") }
            KeyValue("Recordings", vm.engine.recordingsDir.absolutePath)
            KeyValue("Free space", Fmt.bytes(status.freeBytes))
            Hint("Recordings live in the app's own folder (Android/data/…/files/Music). Use Share on a recording to save or send it elsewhere.")
        }
        Spacer(Modifier.height(24.dp))
    }

    LaunchedEffect(Unit) { vm.engine.refreshStatus() }
}

private fun n(d: Double): String = if (d == Math.floor(d)) d.toLong().toString() else d.toString()
