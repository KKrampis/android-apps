package io.github.kkrampis.bookmarkvault.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun TransferScreen(vm: VaultViewModel, contentPadding: PaddingValues) {
    val context = LocalContext.current
    var pasted by rememberSaveable { mutableStateOf("") }

    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.writeTo(it, vm.exportJson(), "JSON") }
    }
    val saveHtml = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        uri?.let { vm.writeTo(it, vm.exportHtml(), "Netscape HTML (importable in any browser)") }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.loadFile(it) }
    }
    val today = LocalDate.now().toString()

    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        Text(
            "Import & export",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Section("Export", "Files are the same as the Chrome extension's, so either one can open them.") {
            Button(onClick = { saveJson.launch("bookmarkvault-$today.json") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.DataObject, null)
                Spacer(Modifier.width(8.dp))
                Text("Save as JSON")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { saveHtml.launch("bookmarks-$today.html") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Code, null)
                Spacer(Modifier.width(8.dp))
                Text("Save as HTML (any browser)")
            }
            Spacer(Modifier.height(8.dp))
            Row {
                TextButton(onClick = { shareText(context, vm.exportJson(), "BookmarkVault export") }) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Share JSON as text")
                }
            }
        }

        Section("Import", "Open a BookmarkVault .json export, or paste its contents. You then pick which folders to bring in.") {
            Button(
                onClick = { openFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.FileOpen, null)
                Spacer(Modifier.width(8.dp))
                Text("Open a .json file")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = pasted,
                onValueChange = { pasted = it },
                label = { Text("Or paste JSON") },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 240.dp),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.loadText(pasted, "pasted JSON") }, modifier = Modifier.fillMaxWidth()) {
                Text("Load pasted JSON")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
