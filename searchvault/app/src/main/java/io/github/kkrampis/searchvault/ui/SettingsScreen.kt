package io.github.kkrampis.searchvault.ui

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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.kkrampis.searchvault.ai.Gemini

@Composable
fun SettingsScreen(vm: SearchViewModel, state: SettingsUiState) {
    val uri = LocalUriHandler.current
    var key by rememberSaveable(state.geminiKey) { mutableStateOf(state.geminiKey) }
    var model by rememberSaveable(state.model) { mutableStateOf(state.model) }
    var device by rememberSaveable(state.deviceName) { mutableStateOf(state.deviceName) }
    var showKey by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

        Section(
            "Gemini",
            "Optional. Gemini answers by running Google searches and lists the pages it used. It needs an API key " +
                "from Google AI Studio, which is tied to your Google account, so these searches are not anonymous.",
        ) {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("Gemini API key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Show key")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model") },
                supportingText = { Text("Default ${Gemini.DEFAULT_MODEL}. Any Gemini model with Google Search works, e.g. gemini-2.5-pro.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { runCatching { uri.openUri(AI_STUDIO_KEYS) } }) { Text("Get a key") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { vm.saveGemini(key, model.ifBlank { Gemini.DEFAULT_MODEL }) }) { Text("Save") }
            }
        }

        Section(
            "Private AI Mode",
            "AI Mode opens Google's own AI Mode page with no account and no key. Signing in is blocked, " +
                "third-party cookies, location, Safe Browsing lookups and WebView usage reports are off.",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Forget Google after every search", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Wipes Google's cookies, cache and site data before each search and when the app starts, " +
                            "so searches can't be linked by cookie. Google still sees your IP address.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.privateMode, onCheckedChange = { vm.setPrivateMode(it) })
            }
        }

        Section("Device name", "Shown in the JSONBin data and used to name the bin.") {
            OutlinedTextField(
                value = device,
                onValueChange = { device = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { vm.setDeviceName(device) }, modifier = Modifier.align(Alignment.End)) { Text("Save") }
        }
        Spacer(Modifier.height(24.dp))
    }
}
