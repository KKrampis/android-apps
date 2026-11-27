package io.github.kkrampis.stockvault.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One line on a chart. [xs] must be increasing; they are epoch days or epoch seconds. */
data class Series(
    val label: String,
    val xs: List<Long>,
    val ys: List<Double?>,
    val color: Color,
    val dashed: Boolean = false,
    val fill: Boolean = false,
    val width: Float = 2f,
)

/**
 * A line chart drawn on a Canvas, with a crosshair: touch and drag to read the values of every
 * series at that point. [formatX] and [formatY] label the crosshair readout and the axis.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LineChart(
    series: List<Series>,
    formatX: (Long) -> String,
    formatY: (Double) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    baseline: Double? = null,
    showLegend: Boolean = series.size > 1,
) {
    val visible = series.filter { s -> s.xs.isNotEmpty() && s.ys.any { it != null } }
    if (visible.isEmpty()) {
        Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Text("No data", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val minX = visible.minOf { it.xs.first() }
    val maxX = visible.maxOf { it.xs.last() }.let { if (it == minX) it + 1 else it }
    val allY = visible.flatMap { it.ys.filterNotNull() } + listOfNotNull(baseline)
    val lo = allY.min()
    val hi = allY.max()
    val pad = ((hi - lo).takeIf { it > 0 } ?: (kotlin.math.abs(hi).takeIf { it > 0 } ?: 1.0)) * 0.06
    val minY = lo - pad
    val maxY = hi + pad
    var touchX by remember(series) { mutableStateOf<Float?>(null) }
    val grid = MaterialTheme.colorScheme.outlineVariant
    val axisText = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier.fillMaxWidth()) {
        // Readout: the values under the finger, or the latest values.
        var widthPx by remember { mutableStateOf(1f) }
        val atX = touchX?.let { minX + ((it / widthPx).coerceIn(0f, 1f) * (maxX - minX)).toLong() }
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                atX?.let(formatX) ?: "${formatX(minX)} – ${formatX(visible.maxOf { it.xs.last() })}",
                style = MaterialTheme.typography.labelMedium,
                color = axisText,
            )
            if (atX != null && visible.size == 1) {
                val v = valueAt(visible[0], atX)
                Text(v?.let(formatY) ?: "", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
        Box {
            Canvas(
                Modifier.fillMaxWidth().height(height)
                    .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                    .pointerInput(series) {
                        detectTapGestures(onPress = {
                            touchX = it.x
                            tryAwaitRelease()
                            touchX = null
                        })
                    }
                    .pointerInput(series) {
                        detectHorizontalDragGestures(
                            onDragStart = { touchX = it.x },
                            onDragEnd = { touchX = null },
                            onDragCancel = { touchX = null },
                        ) { change, _ -> touchX = change.position.x }
                    },
            ) {
                val w = size.width
                val h = size.height
                fun px(x: Long) = ((x - minX).toDouble() / (maxX - minX) * w).toFloat()
                fun py(y: Double) = ((1 - (y - minY) / (maxY - minY)) * h).toFloat()

                // Three horizontal grid lines.
                for (i in 1..3) {
                    val y = h * i / 4f
                    drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                }
                baseline?.let {
                    drawLine(axisText.copy(alpha = 0.6f), Offset(0f, py(it)), Offset(w, py(it)), strokeWidth = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                }
                visible.forEach { s ->
                    val path = Path()
                    var started = false
                    var firstX = 0f
                    var lastX = 0f
                    // Thin out very long series to about two points per pixel.
                    val step = maxOf(1, s.xs.size / (w.toInt() * 2).coerceAtLeast(1))
                    for (i in s.xs.indices step step) {
                        val y = s.ys[i] ?: continue
                        val x = px(s.xs[i])
                        if (!started) {
                            path.moveTo(x, py(y)); started = true; firstX = x
                        } else {
                            path.lineTo(x, py(y))
                        }
                        lastX = x
                    }
                    s.ys.lastOrNull()?.let { y -> path.lineTo(px(s.xs.last()), py(y)); lastX = px(s.xs.last()) }
                    if (s.fill && started) {
                        val area = Path().apply {
                            addPath(path)
                            lineTo(lastX, h)
                            lineTo(firstX, h)
                            close()
                        }
                        drawPath(area, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.28f), Color.Transparent)))
                    }
                    drawPath(
                        path, s.color,
                        style = Stroke(
                            width = s.width.dp.toPx(),
                            pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null,
                        ),
                    )
                }
                if (atX != null) {
                    val x = px(atX)
                    drawLine(axisText, Offset(x, 0f), Offset(x, h), strokeWidth = 1.5f)
                    visible.forEach { s ->
                        valueAt(s, atX)?.let { drawCircle(s.color, radius = 4.dp.toPx(), center = Offset(x, py(it))) }
                    }
                }
            }
            Text(formatY(maxY - pad), style = MaterialTheme.typography.labelSmall, color = axisText,
                modifier = Modifier.align(Alignment.TopEnd).padding(2.dp))
            Text(formatY(minY + pad), style = MaterialTheme.typography.labelSmall, color = axisText,
                modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp))
        }
        if (showLegend || (atX != null && visible.size > 1)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                visible.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(s.color, CircleShape))
                        val v = atX?.let { valueAt(s, it) } ?: s.ys.lastOrNull { it != null }
                        Text(
                            " ${s.label}" + (v?.let { "  ${formatY(it)}" } ?: ""),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

/** The series value at the last point on or before [x]. */
private fun valueAt(s: Series, x: Long): Double? {
    if (s.xs.isEmpty() || x < s.xs.first()) return null
    var lo = 0
    var hi = s.xs.size - 1
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (s.xs[mid] <= x) lo = mid else hi = mid - 1
    }
    return s.ys[lo]
}

/** Horizontal bars for yearly returns: green up, red down. */
@Composable
fun ReturnBars(items: List<Pair<String, Double>>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    val maxAbs = items.maxOf { kotlin.math.abs(it.second) }.takeIf { it > 0 } ?: 1.0
    Column(modifier.fillMaxWidth()) {
        items.forEach { (label, v) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.size(width = 44.dp, height = 18.dp))
                Box(Modifier.weight(1f).height(14.dp)) {
                    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                        val mid = size.width / 2
                        val len = (kotlin.math.abs(v) / maxAbs * mid).toFloat()
                        val color = if (v >= 0) Palette.up else Palette.down
                        if (v >= 0) {
                            drawRect(color, topLeft = Offset(mid, 0f), size = androidx.compose.ui.geometry.Size(len, size.height))
                        } else {
                            drawRect(color, topLeft = Offset(mid - len, 0f), size = androidx.compose.ui.geometry.Size(len, size.height))
                        }
                    }
                }
                Text(
                    io.github.kkrampis.stockvault.model.Format.pct(v, digits = 1),
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.change(v),
                    modifier = Modifier.size(width = 64.dp, height = 18.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
    }
}
