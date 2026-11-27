package io.github.kkrampis.stockvault.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kkrampis.stockvault.model.Format
import io.github.kkrampis.stockvault.model.Stat
import java.text.DateFormat
import java.time.Instant
import java.util.Date

@Composable
fun Section(
    title: String,
    note: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            } else {
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
            }
            content()
        }
    }
}

/** A label on the left and a value on the right, for stats tables. */
@Composable
fun StatRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun StatTable(stats: List<Stat>) {
    stats.forEach { StatRow(it.label, it.value) }
}

/** Big number with a caption, for result summaries. */
@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Column(modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun MetricRow(vararg items: Triple<String, String, Color>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { (l, v, c) -> Metric(l, v, Modifier.weight(1f), c) }
    }
}

/** "+1.23 (+0.54%)" coloured green or red. */
@Composable
fun ChangeText(change: Double?, pct: Double?, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium) {
    Text(
        if (change == null) Format.pct(pct) else "${Format.signed(change)} (${Format.pct(pct)})",
        style = style,
        color = Palette.change(pct ?: change),
        fontWeight = FontWeight.Medium,
    )
}

/** A tiny line of recent prices. */
@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier, width: Dp = 64.dp, height: Dp = 28.dp, reference: Double? = null) {
    if (values.size < 2) return
    val up = values.last() >= (reference ?: values.first())
    val color = if (up) Palette.up else Palette.down
    Canvas(modifier.size(width, height)) {
        val min = minOf(values.min(), reference ?: values.min())
        val max = maxOf(values.max(), reference ?: values.max())
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        fun y(v: Double) = (size.height * (1 - (v - min) / span)).toFloat()
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
        }
        reference?.let {
            drawLine(color.copy(alpha = 0.35f), Offset(0f, y(it)), Offset(size.width, y(it)), strokeWidth = 1f)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
    }
}

fun formatIso(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    return runCatching {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date.from(Instant.parse(iso)))
    }.getOrDefault(iso)
}

fun formatEpoch(seconds: Long?): String {
    if (seconds == null || seconds <= 0) return ""
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(seconds * 1000))
}

fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("StockVault", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

fun readClipboard(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

fun shareText(context: Context, subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Share"))
}

fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show() }
}

fun hostOf(url: String): String = runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull().orEmpty()
