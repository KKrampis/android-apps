package io.github.kkrampis.stockvault.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Same deep slate background as the other apps in this repo, with a market-green accent.
private val Green = Color(0xFF81C995)
private val GreenDeep = Color(0xFF137333)

private val DarkColors = darkColorScheme(
    primary = Green,
    onPrimary = Color(0xFF0B3A1D),
    primaryContainer = Color(0xFF1B4D2E),
    onPrimaryContainer = Color(0xFFCEEAD6),
    secondary = Color(0xFF8AB4F8),
    onSecondary = Color(0xFF062E6F),
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
    primary = GreenDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCEEAD6),
    onPrimaryContainer = Color(0xFF0D3B1E),
    secondary = Color(0xFF1A62D6),
    tertiary = Color(0xFFB06000),
    background = Color(0xFFFAFAF7),
    surface = Color(0xFFFAFAF7),
)

/** Colours for gains, losses and chart series that read in both themes. */
object Palette {
    val up = Color(0xFF34A853)
    val down = Color(0xFFEA4335)
    val series = listOf(
        Color(0xFF4F9DF7), Color(0xFFF5A524), Color(0xFFB57BF2), Color(0xFF2EC4B6),
        Color(0xFFF26C8A), Color(0xFF9BC53D), Color(0xFFFF8A4C), Color(0xFF7D8CA3),
    )

    fun change(v: Double?): Color = when {
        v == null || v == 0.0 -> Color.Unspecified
        v > 0 -> up
        else -> down
    }
}

@Composable
fun StockVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
