package io.github.kkrampis.clipharbor.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Lagoon = Color(0xFF0F766E)
private val LagoonLight = Color(0xFF5EEAD4)
private val Ember = Color(0xFFF97316)
private val EmberLight = Color(0xFFFDBA74)

private val DarkColors = darkColorScheme(
    primary = LagoonLight,
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF115E59),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = EmberLight,
    onSecondary = Color(0xFF4A1A00),
    background = Color(0xFF0B1416),
    surface = Color(0xFF0B1416),
    surfaceVariant = Color(0xFF1E2A2C),
    surfaceContainer = Color(0xFF142022),
)

private val LightColors = lightColorScheme(
    primary = Lagoon,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF042F2E),
    secondary = Ember,
    onSecondary = Color.White,
    background = Color(0xFFF6FAF9),
    surface = Color(0xFFF6FAF9),
)

@Composable
fun ClipHarborTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
