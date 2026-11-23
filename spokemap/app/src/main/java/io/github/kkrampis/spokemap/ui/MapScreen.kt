package io.github.kkrampis.spokemap.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kkrampis.spokemap.data.Bbox
import io.github.kkrampis.spokemap.data.CitiBike
import io.github.kkrampis.spokemap.data.Kind
import io.github.kkrampis.spokemap.data.NjStress
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Feature

/** No-key vector tiles built from OpenStreetMap, served by OpenFreeMap (no accounts, no tracking). */
private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/positron"
private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"
private const val STYLE_FALLBACK = "https://tiles.openfreemap.org/styles/liberty"

@Composable
fun MapScreen(vm: MapViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val dark = isSystemInDarkTheme()

    val layers by vm.layers.collectAsStateWithLifecycle()
    val notices by vm.notices.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var showLegend by remember { mutableStateOf(false) }

    val mapView = remember {
        MapView(context).apply { onCreate(null) }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    mapView.onStart()
                    vm.setForeground(true)
                }
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> {
                    mapView.onStop()
                    vm.setForeground(false)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val m = map
        val s = style
        if (result.values.any { it } && m != null && s != null) enableLocation(context, m, s, follow = true)
    }

    // Set up the map once.
    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isCompassEnabled = false
            m.cameraPosition = CameraPosition.Builder()
                .target(LatLng(vm.startCamera.lat, vm.startCamera.lon))
                .zoom(vm.startCamera.zoom)
                .build()

            val onStyle = Style.OnStyleLoaded { s ->
                MapLayers.install(s)
                style = s
                if (hasLocationPermission(context)) enableLocation(context, m, s, follow = false)
            }
            var fellBack = false
            mapView.addOnDidFailLoadingMapListener {
                if (!fellBack) {
                    fellBack = true
                    m.setStyle(Style.Builder().fromUri(STYLE_FALLBACK), onStyle)
                }
            }
            m.setStyle(Style.Builder().fromUri(if (dark) STYLE_DARK else STYLE_LIGHT), onStyle)

            m.addOnCameraIdleListener {
                val target = m.cameraPosition.target ?: return@addOnCameraIdleListener
                val zoom = m.cameraPosition.zoom
                vm.saveCamera(CameraSpot(target.latitude, target.longitude, zoom))
                val region = m.projection.visibleRegion
                val corners = listOfNotNull(region.farLeft, region.farRight, region.nearLeft, region.nearRight)
                if (corners.isNotEmpty()) {
                    vm.onViewport(
                        Bbox(
                            south = corners.minOf { it.latitude },
                            west = corners.minOf { it.longitude },
                            north = corners.maxOf { it.latitude },
                            east = corners.maxOf { it.longitude },
                        ),
                        zoom,
                    )
                }
            }

            m.addOnMapClickListener { point ->
                val screen = m.projection.toScreenLocation(point)
                fun around(r: Float) = RectF(screen.x - r, screen.y - r, screen.x + r, screen.y + r)
                val station = m.queryRenderedFeatures(around(28f), MapLayers.L_BIKES).firstOrNull()
                val lane = if (station == null) {
                    m.queryRenderedFeatures(around(18f), *MapLayers.LANE_LAYERS).firstOrNull()
                } else {
                    null
                }
                val picked = station?.let { stationFrom(it, vm.stations.value) } ?: lane?.let { laneFrom(it) }
                vm.select(picked)
                true
            }
        }
    }

    // Push data and layer visibility into the map whenever either changes.
    LaunchedEffect(style) {
        val s = style ?: return@LaunchedEffect
        launch { vm.osmJson.collect { MapLayers.setData(s, MapLayers.SRC_OSM, it) } }
        launch { vm.nycJson.collect { MapLayers.setData(s, MapLayers.SRC_NYC, it) } }
        launch { vm.njJson.collect { MapLayers.setData(s, MapLayers.SRC_NJ, it) } }
        launch { vm.bikesJson.collect { MapLayers.setData(s, MapLayers.SRC_BIKES, it) } }
    }
    LaunchedEffect(style, layers) {
        style?.let { MapLayers.setVisible(it, layers) }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        Column(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.dp),
        ) {
            LayerChips(layers, onToggle = vm::toggle)
            notices.values
                .sortedByDescending { if (it.error) 2 else if (it.loading) 1 else 0 }
                .take(3)
                .forEach { NoticePill(it, onRetry = vm::retry) }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp),
        ) {
            Column(
                Modifier.align(Alignment.End),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SmallFloatingActionButton(onClick = { showLegend = true }) {
                    Icon(Icons.Filled.Info, contentDescription = "Legend and data sources")
                }
                if (layers.bikes) {
                    SmallFloatingActionButton(onClick = {
                        val m = map ?: return@SmallFloatingActionButton
                        val here = currentSpot(m)
                        val nearest = vm.nearestStationWithBike(here.latitude, here.longitude)
                        if (nearest != null) {
                            stopFollowing(m)
                            m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(nearest.lat, nearest.lon), 16.0))
                            vm.select(Selection.Station(nearest))
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.DirectionsBike, contentDescription = "Nearest Citi Bike")
                    }
                }
                FloatingActionButton(onClick = {
                    val m = map
                    val s = style
                    if (m == null || s == null) return@FloatingActionButton
                    if (hasLocationPermission(context)) {
                        enableLocation(context, m, s, follow = true)
                    } else {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        )
                    }
                }) {
                    Icon(Icons.Filled.MyLocation, contentDescription = "My location")
                }
            }
            selection?.let { sel ->
                Spacer(Modifier.height(12.dp))
                SelectionCard(sel, onClose = { vm.select(null) })
            }
        }
    }

    if (showLegend) LegendDialog(onDismiss = { showLegend = false })
}

@Composable
private fun LayerChips(layers: Layers, onToggle: (Layer) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chip("Bike lanes (OSM)", layers.osm, Color(Palette.KIND_PROTECTED)) { onToggle(Layer.OSM) }
        Chip("Citi Bike", layers.bikes, Color(Palette.STATION_OK)) { onToggle(Layer.BIKES) }
        Chip("NYC DOT network", layers.nyc, Color(Palette.KIND_LANE)) { onToggle(Layer.NYC) }
        Chip("NJ road stress", layers.nj, Color(Palette.STRESS[3])) { onToggle(Layer.NJ) }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, dot: Color, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(if (selected) dot else dot.copy(alpha = 0.35f))) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ),
    )
}

@Composable
private fun NoticePill(notice: Notice, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp),
        shape = RoundedCornerShape(50),
        color = if (notice.error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shadowElevation = 2.dp,
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = if (notice.error) 2.dp else 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                notice.loading -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                notice.error -> Icon(Icons.Filled.ErrorOutline, null, Modifier.size(16.dp))
            }
            if (notice.loading || notice.error) Spacer(Modifier.width(8.dp))
            Text(
                notice.text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (notice.error) {
                IconButton(onClick = onRetry, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Retry", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun SelectionCard(selection: Selection, onClose: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 14.dp, end = 4.dp)) {
            Column(Modifier.weight(1f)) {
                when (selection) {
                    is Selection.Station -> StationDetails(selection.station)
                    is Selection.Lane -> LaneDetails(selection)
                }
            }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
        }
    }
}

@Composable
private fun StationDetails(s: CitiBike.Station) {
    Text(s.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Stat("${(s.bikes - s.ebikes).coerceAtLeast(0)}", "classic")
        Stat("${s.ebikes}", "e-bikes")
        Stat("${s.docks}", "open docks")
    }
    val status = when {
        !s.renting && !s.returning -> "Station closed"
        !s.renting -> "Not renting bikes right now"
        !s.returning -> "Not accepting returns right now"
        else -> null
    }
    status?.let {
        Spacer(Modifier.height(4.dp))
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
    if (s.lastReported > 0) {
        val mins = ((System.currentTimeMillis() / 1000 - s.lastReported) / 60).coerceAtLeast(0)
        Spacer(Modifier.height(4.dp))
        Text(
            if (mins < 1) "Updated just now" else "Updated $mins min ago",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LaneDetails(lane: Selection.Lane) {
    Text(
        lane.title.ifBlank { "Unnamed segment" },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(4.dp))
    if (lane.stress != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Swatch(Color(lane.stress.let { if (it in 1..5) Palette.STRESS[it - 1] else Palette.STRESS_UNKNOWN }))
            Spacer(Modifier.width(8.dp))
            Text("Traffic stress " + NjStress.label(lane.stress), style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Swatch(Color(Palette.kind(lane.kind)))
            Spacer(Modifier.width(8.dp))
            Text(Kind.label(lane.kind), style = MaterialTheme.typography.bodyMedium)
        }
    }
    lane.detail?.takeIf { it.isNotBlank() }?.let {
        Spacer(Modifier.height(2.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(2.dp))
    Text(lane.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun Swatch(color: Color) {
    Box(Modifier.size(width = 18.dp, height = 6.dp).clip(RoundedCornerShape(3.dp)).background(color))
}

// ---- Map helpers ----

private fun Feature.str(key: String): String? =
    if (hasNonNullValueForProperty(key)) getStringProperty(key) else null

private fun Feature.int(key: String): Int? =
    if (hasNonNullValueForProperty(key)) getNumberProperty(key)?.toInt() else null

private fun stationFrom(f: Feature, stations: List<CitiBike.Station>): Selection? {
    val id = f.str("id") ?: return null
    return stations.firstOrNull { it.id == id }?.let { Selection.Station(it) }
}

private fun laneFrom(f: Feature): Selection? = when (f.str("src")) {
    "osm" -> Selection.Lane(
        source = "OpenStreetMap",
        title = f.str("name").orEmpty(),
        kind = f.str("kind"),
        detail = null,
        stress = null,
    )
    "nyc" -> Selection.Lane(
        source = "NYC DOT bike network",
        title = f.str("name").orEmpty(),
        kind = f.str("kind"),
        detail = listOfNotNull(f.str("detail"), f.str("span")).filter { it.isNotBlank() }.joinToString(" · "),
        stress = null,
    )
    "nj" -> Selection.Lane(
        source = "NJTPA bike level of traffic stress",
        title = f.str("name").orEmpty(),
        kind = null,
        detail = buildList {
            f.int("speed")?.takeIf { it > 0 }?.let { add("$it mph limit") }
            f.str("bikelane")?.takeIf { it.isNotBlank() && it != "0" && !it.equals("no", true) }?.let { add("Bike lane: $it") }
            f.str("bikepath")?.takeIf { it.isNotBlank() && it != "0" && !it.equals("no", true) }?.let { add("Bike path: $it") }
        }.joinToString(" · "),
        stress = f.int("los") ?: 0,
    )
    else -> null
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission")
private fun enableLocation(context: Context, map: MapLibreMap, style: Style, follow: Boolean) {
    if (!hasLocationPermission(context)) return
    val lc = map.locationComponent
    if (!lc.isLocationComponentActivated) {
        lc.activateLocationComponent(LocationComponentActivationOptions.builder(context, style).build())
    }
    lc.isLocationComponentEnabled = true
    lc.renderMode = RenderMode.NORMAL
    if (follow) {
        lc.cameraMode = CameraMode.TRACKING
        if (map.cameraPosition.zoom < 14.5) map.animateCamera(CameraUpdateFactory.zoomTo(15.0))
    }
}

private fun stopFollowing(map: MapLibreMap) {
    val lc = map.locationComponent
    if (lc.isLocationComponentActivated) lc.cameraMode = CameraMode.NONE
}

/** The user's position when known, otherwise the middle of the map. */
@SuppressLint("MissingPermission")
private fun currentSpot(map: MapLibreMap): LatLng {
    val lc = map.locationComponent
    val loc = if (lc.isLocationComponentActivated && lc.isLocationComponentEnabled) lc.lastKnownLocation else null
    return loc?.let { LatLng(it.latitude, it.longitude) } ?: map.cameraPosition.target ?: LatLng(40.72, -74.02)
}
