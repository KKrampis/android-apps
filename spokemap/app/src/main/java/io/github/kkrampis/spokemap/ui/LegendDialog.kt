package io.github.kkrampis.spokemap.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.kkrampis.spokemap.data.Kind
import io.github.kkrampis.spokemap.data.NjStress

@Composable
fun LegendDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Legend") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Heading("Bike lanes (OSM solid, NYC DOT dashed)")
                for (k in listOf(Kind.PATH, Kind.PROTECTED, Kind.LANE, Kind.SHARED)) {
                    Entry(Color(Palette.kind(k)), Kind.label(k))
                }

                Heading("NJ road traffic stress")
                for (i in 1..5) Entry(Color(Palette.STRESS[i - 1]), NjStress.label(i))

                Heading("Citi Bike stations")
                Entry(Color(Palette.STATION_OK), "4 or more bikes")
                Entry(Color(Palette.STATION_FEW), "1 to 3 bikes")
                Entry(Color(Palette.STATION_EMPTY), "No bikes")
                Entry(Color(Palette.STATION_CLOSED), "Not renting")
                Small("Live counts refresh every minute while the app is open. Zoom in to see the number on each dot.")

                Heading("Data")
                Small(
                    "Map tiles: OpenFreeMap, © OpenStreetMap contributors.\n" +
                        "Bike lanes: OpenStreetMap via the Overpass API (ODbL).\n" +
                        "NYC DOT network: \"New York City Bike Routes\", NYC Open Data.\n" +
                        "NJ traffic stress: NJTPA Bicycle Level of Traffic Stress.\n" +
                        "Citi Bike: public GBFS feed from Lyft.",
                )

                Heading("Privacy")
                Small(
                    "No account, no analytics, no ads. Your location stays on the phone. The app asks each " +
                        "data server above directly for the map area you are looking at, and nothing else.",
                )
            }
        },
    )
}

@Composable
private fun Heading(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Entry(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Swatch(color)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Small(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
