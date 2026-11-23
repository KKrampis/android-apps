package io.github.kkrampis.spokemap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.kkrampis.spokemap.ui.MapScreen
import io.github.kkrampis.spokemap.ui.SpokeTheme
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MapLibre.getInstance(this)
        setContent {
            SpokeTheme { MapScreen() }
        }
    }
}
