package io.github.kkrampis.radiorecorder.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Same deep slate background as the other apps in this repo, with the desktop app's orange accent.
private val Orange = Color(0xFFFB923C)
private val OrangeDeep = Color(0xFFC2410C)

private val DarkColors = darkColorScheme(
    primary = Orange,
    onPrimary = Color(0xFF431407),
    primaryContainer = Color(0xFF5A2410),
    onPrimaryContainer = Color(0xFFFFDBC8),
    secondary = Color(0xFF81C995),
    onSecondary = Color(0xFF0B3A1D),
    tertiary = Color(0xFFFDD663),
    error = Color(0xFFF28B82),
    background = Color(0xFF0D0F12),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF0D0F12),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1C2130),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainer = Color(0xFF141720),
    surfaceContainerHigh = Color(0xFF1C2130),
    surfaceContainerHighest = Color(0xFF252D3D),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF252D3D),
)

private val LightColors = lightColorScheme(
    primary = OrangeDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC8),
    onPrimaryContainer = Color(0xFF3A0B00),
    secondary = Color(0xFF137333),
    tertiary = Color(0xFFB06000),
    background = Color(0xFFFAFAF7),
    surface = Color(0xFFFAFAF7),
)

object StatusColors {
    val recording = Color(0xFFEF4444)
    val waiting = Color(0xFFF59E0B)
    val ok = Color(0xFF22C55E)
    val muted = Color(0xFF94A3B8)
}

@Composable
fun RadioRecorderTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
