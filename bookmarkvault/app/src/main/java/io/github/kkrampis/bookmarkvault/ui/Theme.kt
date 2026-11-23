package io.github.kkrampis.bookmarkvault.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The extension's palette: amber accent on a deep slate background.
private val Amber = Color(0xFFF59E0B)
private val AmberDeep = Color(0xFFB45309)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF2A1700),
    primaryContainer = Color(0xFF4A2E05),
    onPrimaryContainer = Color(0xFFFDE68A),
    secondary = Color(0xFFFB923C),
    onSecondary = Color(0xFF3A1500),
    tertiary = Color(0xFF34D399),
    error = Color(0xFFF87171),
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
    primary = AmberDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEF3C7),
    onPrimaryContainer = Color(0xFF451A03),
    secondary = Color(0xFFEA580C),
    tertiary = Color(0xFF059669),
    background = Color(0xFFFAFAF7),
    surface = Color(0xFFFAFAF7),
)

@Composable
fun BookmarkVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
