package io.github.kkrampis.spokemap.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.kkrampis.spokemap.data.Bbox
import io.github.kkrampis.spokemap.data.Cell
import io.github.kkrampis.spokemap.data.CitiBike
import io.github.kkrampis.spokemap.data.GeoJson
import io.github.kkrampis.spokemap.data.Grid
import io.github.kkrampis.spokemap.data.Http
import io.github.kkrampis.spokemap.data.NjStress
import io.github.kkrampis.spokemap.data.NycDot
import io.github.kkrampis.spokemap.data.Overpass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.sqrt

data class Layers(
    val osm: Boolean = true,
    val nyc: Boolean = false,
    val nj: Boolean = false,
    val bikes: Boolean = true,
)

enum class Layer { OSM, NYC, NJ, BIKES }

data class Notice(val text: String, val loading: Boolean = false, val error: Boolean = false)

data class CameraSpot(val lat: Double, val lon: Double, val zoom: Double)

class MapViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        /** Lanes are fetched per area only when zoomed in this far, to keep requests small. */
        const val MIN_LANE_ZOOM = 12.0
        private const val MAX_CELLS_PER_REQUEST = 40
        private const val BIKES_REFRESH_MS = 60_000L
        private const val NYC_CACHE_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
        private const val MAX_KEPT_FEATURES = 60_000
    }

    private val prefs = app.getSharedPreferences("spokemap", Context.MODE_PRIVATE)

    private val _layers = MutableStateFlow(
        Layers(
            osm = prefs.getBoolean("osm", true),
            nyc = prefs.getBoolean("nyc", false),
            nj = prefs.getBoolean("nj", false),
            bikes = prefs.getBoolean("bikes", true),
        ),
    )
    val layers: StateFlow<Layers> = _layers.asStateFlow()

    private val _notices = MutableStateFlow<Map<Layer, Notice>>(emptyMap())
    val notices: StateFlow<Map<Layer, Notice>> = _notices.asStateFlow()

    private val _selection = MutableStateFlow<Selection?>(null)
    val selection: StateFlow<Selection?> = _selection.asStateFlow()

    val osmJson = MutableStateFlow(GeoJson.EMPTY)
    val nycJson = MutableStateFlow(GeoJson.EMPTY)
    val njJson = MutableStateFlow(GeoJson.EMPTY)
    val bikesJson = MutableStateFlow(GeoJson.EMPTY)

    private val _stations = MutableStateFlow<List<CitiBike.Station>>(emptyList())
    val stations: StateFlow<List<CitiBike.Station>> = _stations.asStateFlow()

    val startCamera = CameraSpot(
        lat = prefs.getFloat("lat", 40.7200f).toDouble(),
        lon = prefs.getFloat("lon", -74.0200f).toDouble(),
        zoom = prefs.getFloat("zoom", 12.5f).toDouble(),
    )

    private val osmFeatures = LinkedHashMap<Long, String>()
    private val osmCells = HashSet<Cell>()
    private val njFeatures = LinkedHashMap<Long, String>()
    private val njCells = HashSet<Cell>()

    private var viewport: Pair<Bbox, Double>? = null
    private var debounceJob: Job? = null
    private val laneMutex = Mutex()
    private var nycLoaded = false
    private var nycJob: Job? = null
    private var bikesJob: Job? = null
    private var foreground = false
    private var bikeUrls: Pair<String, String>? = null

    init {
        if (_layers.value.nyc) loadNyc()
    }

    fun toggle(layer: Layer) {
        _layers.update {
            when (layer) {
                Layer.OSM -> it.copy(osm = !it.osm)
                Layer.NYC -> it.copy(nyc = !it.nyc)
                Layer.NJ -> it.copy(nj = !it.nj)
                Layer.BIKES -> it.copy(bikes = !it.bikes)
            }
        }
        val l = _layers.value
        prefs.edit()
            .putBoolean("osm", l.osm).putBoolean("nyc", l.nyc)
            .putBoolean("nj", l.nj).putBoolean("bikes", l.bikes)
            .apply()
        when (layer) {
            Layer.NYC -> if (l.nyc) loadNyc() else clearNotice(Layer.NYC)
            Layer.BIKES -> restartBikes()
            Layer.OSM, Layer.NJ -> {
                if (!l.osm) clearNotice(Layer.OSM)
                if (!l.nj) clearNotice(Layer.NJ)
                viewport?.let { (b, z) -> scheduleLanes(b, z, 0) }
            }
        }
    }

    fun select(selection: Selection?) {
        _selection.value = selection
    }

    fun saveCamera(spot: CameraSpot) {
        prefs.edit()
            .putFloat("lat", spot.lat.toFloat())
            .putFloat("lon", spot.lon.toFloat())
            .putFloat("zoom", spot.zoom.toFloat())
            .apply()
    }

    fun setForeground(value: Boolean) {
        foreground = value
        restartBikes()
    }

    /** Called when the map stops moving. */
    fun onViewport(box: Bbox, zoom: Double) {
        viewport = box to zoom
        scheduleLanes(box, zoom, 400)
    }

    fun retry() {
        val l = _layers.value
        if (l.nyc && !nycLoaded) loadNyc()
        if (l.bikes) restartBikes()
        viewport?.let { (b, z) -> scheduleLanes(b, z, 0) }
    }

    /** The closest station with a bike to rent. */
    fun nearestStationWithBike(lat: Double, lon: Double): CitiBike.Station? {
        val k = cos(Math.toRadians(lat))
        return _stations.value
            .filter { it.renting && it.bikes > 0 }
            .minByOrNull {
                val dx = (it.lon - lon) * k
                val dy = it.lat - lat
                sqrt(dx * dx + dy * dy)
            }
    }

    // ---- Lanes (OpenStreetMap and NJTPA), fetched per grid cell ----

    private fun scheduleLanes(box: Bbox, zoom: Double, delayMs: Long) {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(delayMs)
            // Fetches run outside the debounce job so moving the map never throws away a download.
            viewModelScope.launch { laneMutex.withLock { loadLanes(box, zoom) } }
        }
    }

    private suspend fun loadLanes(box: Bbox, zoom: Double) {
        val l = _layers.value
        if (zoom < MIN_LANE_ZOOM) {
            val hint = Notice("Zoom in to load bike lanes for this area")
            if (l.osm) setNotice(Layer.OSM, hint) else clearNotice(Layer.OSM)
            if (l.nj && !l.osm) setNotice(Layer.NJ, hint) else clearNotice(Layer.NJ)
            return
        }
        val cells = Grid.cells(box)
        if (l.osm) loadOsm(cells) else clearNotice(Layer.OSM)
        if (l.nj) loadNj(cells) else clearNotice(Layer.NJ)
    }

    private suspend fun loadOsm(cells: List<Cell>) {
        val missing = cells.filter { it !in osmCells }.take(MAX_CELLS_PER_REQUEST)
        if (missing.isEmpty()) {
            clearNotice(Layer.OSM)
            return
        }
        setNotice(Layer.OSM, Notice("Loading bike lanes from OpenStreetMap…", loading = true))
        val query = Overpass.query(Grid.cover(missing))
        var lastError: Exception? = null
        for (endpoint in Overpass.ENDPOINTS) {
            try {
                val json = Http.postForm(endpoint, mapOf("data" to query))
                val features = withContext(Dispatchers.Default) { Overpass.parse(json) }
                withContext(Dispatchers.Default) {
                    if (osmFeatures.size > MAX_KEPT_FEATURES) {
                        osmFeatures.clear()
                        osmCells.clear()
                    }
                    features.forEach { (id, f) -> osmFeatures[id] = f }
                    osmCells.addAll(missing)
                    osmJson.value = GeoJson.collection(osmFeatures.values)
                }
                clearNotice(Layer.OSM)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        setNotice(Layer.OSM, Notice("Couldn't load OpenStreetMap lanes: ${lastError.short()}", error = true))
    }

    private suspend fun loadNj(cells: List<Cell>) {
        val missing = cells.filter { it !in njCells }.take(MAX_CELLS_PER_REQUEST)
        if (missing.isEmpty()) {
            clearNotice(Layer.NJ)
            return
        }
        // NJTPA covers northern New Jersey only; skip areas clearly east of the Hudson.
        val box = Grid.cover(missing)
        if (box.west > -73.95) {
            njCells.addAll(missing)
            clearNotice(Layer.NJ)
            return
        }
        setNotice(Layer.NJ, Notice("Loading NJ traffic stress…", loading = true))
        try {
            var offset = 0
            var pages = 0
            while (true) {
                val page = withContext(Dispatchers.Default) { NjStress.parse(Http.get(NjStress.url(box, offset))) }
                withContext(Dispatchers.Default) {
                    if (njFeatures.size > MAX_KEPT_FEATURES) {
                        njFeatures.clear()
                        njCells.clear()
                    }
                    page.features.forEach { (id, f) -> njFeatures[id] = f }
                    njJson.value = GeoJson.collection(njFeatures.values)
                }
                pages++
                if (!page.more || page.features.isEmpty() || pages >= 12) break
                offset += page.features.size
            }
            njCells.addAll(missing)
            clearNotice(Layer.NJ)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setNotice(Layer.NJ, Notice("Couldn't load NJ traffic stress: ${e.short()}", error = true))
        }
    }

    // ---- NYC DOT bike network, downloaded whole and cached ----

    private fun loadNyc() {
        if (nycLoaded || nycJob?.isActive == true) return
        nycJob = viewModelScope.launch {
            val cache = File(getApplication<Application>().cacheDir, "nyc-dot-bike-routes.geojson")
            val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < NYC_CACHE_MAX_AGE_MS
            if (fresh) {
                nycJson.value = withContext(Dispatchers.IO) { cache.readText() }
                nycLoaded = true
                return@launch
            }
            setNotice(Layer.NYC, Notice("Downloading the NYC DOT bike network…", loading = true))
            var lastError: Exception? = null
            val json = try {
                downloadNyc()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                null
            }
            if (json != null) {
                nycJson.value = json
                nycLoaded = true
                withContext(Dispatchers.IO) { runCatching { cache.writeText(json) } }
                clearNotice(Layer.NYC)
            } else if (cache.exists()) {
                // Offline or the servers are down: an old copy beats nothing.
                nycJson.value = withContext(Dispatchers.IO) { cache.readText() }
                nycLoaded = true
                setNotice(Layer.NYC, Notice("Showing a saved copy of the NYC DOT network", error = true))
            } else {
                setNotice(Layer.NYC, Notice("Couldn't load NYC DOT routes: ${lastError.short()}", error = true))
            }
        }
    }

    private suspend fun downloadNyc(): String {
        var lastError: Exception? = null
        for (url in NycDot.DOWNLOADS) {
            try {
                val page = withContext(Dispatchers.Default) { NycDot.parse(Http.get(url)) }
                if (page.features.isNotEmpty()) return GeoJson.collection(page.features)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        try {
            val all = ArrayList<String>()
            var offset = 0
            while (offset < 100_000) {
                val page = withContext(Dispatchers.Default) { NycDot.parse(Http.get(NycDot.mirrorPage(offset))) }
                all += page.features
                if (page.features.isEmpty() || (!page.more && page.features.size < NycDot.MIRROR_PAGE_SIZE)) break
                offset += page.features.size
            }
            if (all.isNotEmpty()) return GeoJson.collection(all)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError = e
        }
        throw lastError ?: IllegalStateException("No data")
    }

    // ---- Citi Bike, refreshed every minute while the app is open ----

    private fun restartBikes() {
        bikesJob?.cancel()
        if (!_layers.value.bikes) {
            clearNotice(Layer.BIKES)
            return
        }
        if (!foreground) return
        bikesJob = viewModelScope.launch {
            while (isActive) {
                refreshBikes()
                delay(BIKES_REFRESH_MS)
            }
        }
    }

    private suspend fun refreshBikes() {
        if (_stations.value.isEmpty()) setNotice(Layer.BIKES, Notice("Loading Citi Bike stations…", loading = true))
        try {
            val urls = bikeUrls ?: runCatching { CitiBike.feedUrls(Http.get(CitiBike.DISCOVERY)) }.getOrNull()
                ?: (CitiBike.FALLBACK_INFO to CitiBike.FALLBACK_STATUS)
            bikeUrls = urls
            val info = Http.get(urls.first)
            val status = Http.get(urls.second)
            val list = withContext(Dispatchers.Default) { CitiBike.merge(info, status) }
            _stations.value = list
            bikesJson.value = withContext(Dispatchers.Default) { CitiBike.toGeoJson(list) }
            refreshSelectedStation(list)
            clearNotice(Layer.BIKES)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            bikeUrls = null
            setNotice(Layer.BIKES, Notice("Couldn't refresh Citi Bike: ${e.short()}", error = true))
        }
    }

    private fun refreshSelectedStation(list: List<CitiBike.Station>) {
        val current = _selection.value as? Selection.Station ?: return
        list.firstOrNull { it.id == current.station.id }?.let { _selection.value = Selection.Station(it) }
    }

    // ---- Notices ----

    private fun setNotice(layer: Layer, notice: Notice) = _notices.update { it + (layer to notice) }

    private fun clearNotice(layer: Layer) = _notices.update { it - layer }

    private fun Exception?.short(): String {
        val msg = this?.message?.takeIf { it.isNotBlank() } ?: this?.javaClass?.simpleName ?: "unknown error"
        return if (msg.length > 80) msg.take(80) + "…" else msg
    }
}

sealed interface Selection {
    data class Station(val station: CitiBike.Station) : Selection
    data class Lane(
        val source: String,
        val title: String,
        val kind: String?,
        val detail: String?,
        val stress: Int?,
    ) : Selection
}
