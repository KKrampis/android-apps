package io.github.kkrampis.appshelf.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import io.github.kkrampis.appshelf.data.AppRow
import io.github.kkrampis.appshelf.install.Installer
import java.time.Instant

/** The installed app's own icon, or a coloured tile with its first letter. */
@Composable
fun AppIcon(row: AppRow, vm: AppViewModel, size: Dp = 48.dp) {
    val pkg = row.installed?.packageName
    val bitmap: ImageBitmap? = remember(pkg) {
        pkg?.let { p -> vm.store.installed.icon(p)?.let { runCatching { it.toBitmap(144, 144).asImageBitmap() }.getOrNull() } }
    }
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(Palette.tile(row.app.key)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                row.app.name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.45f).sp,
            )
        }
    }
}

/** Install, Update, Open, or progress while downloading. */
@Composable
fun AppAction(row: AppRow, task: Task?, vm: AppViewModel, gate: InstallGate) {
    val context = LocalContext.current
    when {
        task is Task.Downloading -> {
            TextButton(onClick = { vm.cancel(row.app.id) }) {
                if (task.progress >= 0f) {
                    CircularProgressIndicator(progress = { task.progress }, modifier = Modifier.size(22.dp), strokeWidth = 3.dp)
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 3.dp)
                }
            }
        }
        task == Task.Installing -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 3.dp)
        row.installed == null -> Button(onClick = { gate.run { vm.install(row.app) } }) { Text("Install") }
        row.hasUpdate -> FilledTonalButton(onClick = { gate.run { vm.install(row.app) } }) { Text("Update") }
        else -> {
            val launch = remember(row.installed.packageName) { vm.store.installed.launchIntent(row.installed.packageName) }
            OutlinedButton(enabled = launch != null, onClick = { launch?.let { context.startActivity(it) } }) { Text("Open") }
        }
    }
}

/** Asks for "Install unknown apps" the first time something is installed. */
class InstallGate(private val context: Context, private val show: () -> Unit) {
    fun run(action: () -> Unit) {
        if (Installer.canInstall(context)) action() else show()
    }
}

@Composable
fun rememberInstallGate(): InstallGate {
    val context = LocalContext.current
    var asking by remember { mutableStateOf(false) }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("Allow installs") },
            text = {
                Text(
                    "Android needs your permission for AppShelf to install apps. On the next screen, " +
                        "turn on \"Allow from this source\", come back, and tap Install again.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    asking = false
                    context.startActivity(Installer.allowInstallsIntent(context))
                }) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("Not now") } },
        )
    }
    return remember(context) { InstallGate(context) { asking = true } }
}

fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

/** "3 days ago" from GitHub's ISO time. */
fun formatWhen(iso: String): String = try {
    DateUtils.getRelativeTimeSpanString(
        Instant.parse(iso).toEpochMilli(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
    ).toString()
} catch (e: Exception) {
    iso.take(10)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}
