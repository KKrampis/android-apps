package io.github.kkrampis.searchvault.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Same deep slate background as the other apps in this repo, with a search-blue accent.
private val Blue = Color(0xFF8AB4F8)
private val BlueDeep = Color(0xFF1A62D6)

private val DarkColors = darkColorScheme(
    primary = Blue,
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF1B3A6B),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFC58AF9),
    onSecondary = Color(0xFF2D0B4E),
    tertiary = Color(0xFF81C995),
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
    primary = BlueDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF8430CE),
    tertiary = Color(0xFF188038),
    background = Color(0xFFFAFAF7),
    surface = Color(0xFFFAFAF7),
)

@Composable
fun SearchVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
