package io.github.kkrampis.radiorecorder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kkrampis.radiorecorder.data.RecStatus
import io.github.kkrampis.radiorecorder.engine.RecState
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object Fmt {
    fun bytes(b: Long): String {
        if (b <= 0) return "0 B"
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var v = b.toDouble()
        var i = 0
        while (v >= 1024 && i < units.lastIndex) {
            v /= 1024; i++
        }
        return if (i == 0) "$b B" else String.format(Locale.getDefault(), "%.1f %s", v, units[i])
    }

    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m ${s % 60}s"
    }

    private val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    private val dayTime = DateTimeFormatter.ofPattern("EEE d MMM, ", Locale.getDefault())
    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    fun epoch(ms: Long?): String =
        if (ms == null || ms <= 0) "—" else ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()).format(dateTime)

    /** "Fri 27 Nov, 10:00 PM" in the phone's time zone. */
    fun local(z: ZonedDateTime): String {
        val l = z.withZoneSameInstant(ZoneId.systemDefault())
        return l.format(dayTime) + l.format(time)
    }

    /** "22:00" -> "10:00 PM" (or stays 24-hour, following the phone's locale). */
    fun hm(s: String): String = try {
        java.time.LocalTime.parse(if (s.length == 4) "0$s" else s).format(time)
    } catch (e: Exception) {
        s
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
fun Chip(text: String, color: Color) {
    Text(
        text.uppercase(),
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun StateChip(state: RecState) {
    val c = when (state) {
        RecState.RECORDING -> StatusColors.recording
        RecState.WAITING, RecState.CONNECTING, RecState.STARTING -> StatusColors.waiting
        RecState.RETRYING -> MaterialTheme.colorScheme.error
        else -> StatusColors.muted
    }
    Chip(state.name.lowercase(), c)
}

@Composable
fun RecStatusChip(s: RecStatus) {
    val c = when (s) {
        RecStatus.RECORDING -> StatusColors.recording
        RecStatus.UPLOADED -> StatusColors.ok
        RecStatus.DONE, RecStatus.UPLOADING -> StatusColors.waiting
        RecStatus.FAILED -> MaterialTheme.colorScheme.error
        RecStatus.DELETED -> StatusColors.muted
    }
    val label = when (s) {
        RecStatus.DONE -> "to upload"
        RecStatus.FAILED -> "upload failed"
        RecStatus.DELETED -> "archived only"
        else -> s.name.lowercase()
    }
    Chip(label, c)
}

@Composable
fun RecDot() {
    Spacer(
        Modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(StatusColors.recording),
    )
}

@Composable
fun KeyValue(key: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(key, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
