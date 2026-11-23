package io.github.kkrampis.spokemap.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4ADE80),
    onPrimary = Color(0xFF00391C),
    primaryContainer = Color(0xFF14532D),
    onPrimaryContainer = Color(0xFFBBF7D0),
    secondary = Color(0xFF38BDF8),
    background = Color(0xFF0F1411),
    surface = Color(0xFF151B17),
    onSurface = Color(0xFFE2E8E4),
    surfaceVariant = Color(0xFF1F2823),
    onSurfaceVariant = Color(0xFF9DB0A5),
    surfaceContainer = Color(0xFF19201C),
    surfaceContainerHigh = Color(0xFF202924),
    surfaceContainerHighest = Color(0xFF29332D),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF15803D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCFCE7),
    onPrimaryContainer = Color(0xFF052E16),
    secondary = Color(0xFF0284C7),
    background = Color(0xFFF8FAF8),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun SpokeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
