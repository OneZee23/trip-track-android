// iOS: MyMapViewModel.swift
package com.onezee.triptrack.feature.atlas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onezee.triptrack.data.*
import com.onezee.triptrack.feature.settings.*
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.logic.RegionAtlas
import com.onezee.triptrack.map.MapFog
import com.onezee.triptrack.map.camera.MapCameraCommand
import com.onezee.triptrack.map.fog.FogLook
import com.onezee.triptrack.model.*
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json

// iOS: LocationManager.authorizationStatus. Atlas observes recording's source and never starts GPS.
enum class AtlasLocationPermission { unknown, allowed, denied, restricted }
// iOS: LocationManager.swift (read-only authorization publisher).
interface AtlasLocationSource { val permission: StateFlow<AtlasLocationPermission> }

// iOS: MapCameraCommand.userLocation. The map supplies centring on its existing location source.
sealed interface AtlasCameraAction {
    data class Command(val command: MapCameraCommand) : AtlasCameraAction
    data object UserLocation : AtlasCameraAction
}
// iOS: Coordinator.bindViewModel. A map consumer handles this and acknowledges its revision.
fun interface AtlasMapConsumer { fun apply(action: AtlasCameraAction) }

// iOS: DiscoveryStore / RiddleHint.plan. Discovery rules stay with their owner.
data class AtlasFinds(val seals: List<Discovery> = emptyList(), val hints: List<AtlasRiddleHint> = emptyList())
// iOS: MyMapViewModel.reloadDiscoveries.
fun interface AtlasDiscoveries { suspend fun load(layer: RevealedLayer): AtlasFinds }

// iOS: MyMapViewModel.Selection.
sealed interface AtlasSelection {
    data class Region(val id: String) : AtlasSelection
    data class Trip(val id: UUID) : AtlasSelection
    data class Discovery(val id: UUID) : AtlasSelection
    data class Road(val ids: List<UUID>) : AtlasSelection
}

// iOS: AtlasMapAppearance.swift. Values are immutable while they are exposed to a UI.
data class AtlasAppearance(val style: AtlasMapAppearance.Style = AtlasMapAppearance.Style.fog, val showsPhotos: Boolean = true) {
    fun model() = AtlasMapAppearance(style, showsPhotos)
}

// iOS: MyMapViewModel.swift / MyMapView.swift.
data class AtlasState(
    val isLoading: Boolean = true,
    val isFiltering: Boolean = false,
    val failed: Boolean = false,
    val exploration: MapExploration = MapExploration(),
    val revealed: RevealedLayer = RevealedLayer.empty,
    val fog: MapFog = MapFog(RevealedLayer.empty),
    val appearance: AtlasAppearance = AtlasAppearance(),
    val period: AtlasPeriod = AtlasPeriod.AllTime,
    val overview: AtlasOverview = AtlasOverview(),
    val journal: AtlasJournal = AtlasJournal(),
    val selection: AtlasSelection? = null,
    val selectedRoute: List<GeoPoint> = emptyList(),
    val selectedRegionTrips: List<MapTripPin> = emptyList(),
    val placePins: List<AtlasPlacePin> = emptyList(),
    val selectedPlaceId: UUID? = null,
    val home: HomeState = HomeState(),
    val homeSelected: Boolean = false,
    val homeTripCount: Int = 0,
    val detent: AtlasSheetDetent = AtlasSheetDetent.collapsed,
    val detentBeforeSubstate: AtlasSheetDetent = AtlasSheetDetent.collapsed,
    val camera: AtlasCameraAction? = null,
    val cameraRevision: Long = 0,
    val locationPermission: AtlasLocationPermission = AtlasLocationPermission.unknown,
    val showsLocationDialog: Boolean = false,
    val online: Boolean = true,
    val shareOfflineNotice: Boolean = false,
    val filmingMode: Boolean = false,
) {
    val isEmpty get() = !isLoading && exploration.isEmpty
    val selectedPlace get() = placePins.firstOrNull { it.id == selectedPlaceId }
    val selectedRegion get() = (selection as? AtlasSelection.Region)?.let { exploration.region(it.id) }
    val selectedTrip get() = (selection as? AtlasSelection.Trip)?.let { exploration.trip(it.id) }
    val selectedRoad get() = (selection as? AtlasSelection.Road)?.ids?.mapNotNull { exploration.trip(it) }
}

// iOS: ReloadCoalescer.swift.
class ReloadCoalescer(private val scope: CoroutineScope, private val windowMillis: Long = 250, private val run: suspend () -> Unit) {
    private var task: Job? = null
    fun schedule() {
        task?.cancel()
        task = scope.launch { delay(windowMillis); task = null; run() }
    }
    fun cancel() { task?.cancel(); task = null }
}

// iOS: MyMapViewModel.swift. Keep one instance across tab switches in the owning graph.
class MyMapViewModel(
    private val repository: TripRepository,
    private val territoryStore: TerritoryStore,
    private val tileStore: RevealedTileStore,
    private val placeStore: PlaceStore,
    private val preferences: PresentationPreferences,
    location: AtlasLocationSource,
    private val home: StateFlow<HomeState>,
    dispatcher: CoroutineDispatcher,
    events: Flow<WorldChange> = emptyFlow(),
    online: Flow<Boolean> = flowOf(true),
    private val discoveries: AtlasDiscoveries = AtlasDiscoveries { AtlasFinds() },
    private val atlasProvider: () -> RegionAtlas = { RegionAtlas.shared },
    private val now: () -> Instant = Instant::now,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val filming: Flow<Boolean> = flowOf(false),
    private val defaultHiddenEdgeMetres: () -> Int? = { null },
) : ViewModel(CoroutineScope(SupervisorJob() + dispatcher)) {
    private val savedAppearance = preferences.string("atlas.mapAppearance")?.let { raw ->
        runCatching { Json.decodeFromString<AtlasMapAppearance>(raw) }.getOrNull()
    }?.let { AtlasAppearance(it.style, it.showsPhotos) } ?: AtlasAppearance()
    private val mutableState = MutableStateFlow(AtlasState(appearance = savedAppearance, fog = MapFog(RevealedLayer.empty, FogLook.of(savedAppearance.model()))))
    val state = mutableState.asStateFlow()
    private var loaded = false
    private var stale = false
    private var loadTask: Job? = null
    private var filterTask: Job? = null
    private var placesTask: Job? = null
    private var findsTask: Job? = null
    private var cachedTrips = emptyList<Trip>()
    private var allTime: Pair<MapExploration, RevealedLayer>? = null
    private var placeSeeds = emptyList<AtlasPlaces.Seed>()
    private var finds = AtlasFinds()
    private var pendingDiscovery: UUID? = null
    var placesReloadDebounceMillis = 300L
    var fogRebuilds = 0
        private set
    private val coalescer = ReloadCoalescer(viewModelScope) { reload() }

    init {
        viewModelScope.launch { location.permission.collect { value -> mutableState.update { it.copy(locationPermission = value) } } }
        viewModelScope.launch { online.collect { value -> mutableState.update { it.copy(online = value) } } }
        viewModelScope.launch { filming.collect { active -> mutableState.update { it.copy(filmingMode = active,
            home = if (active) HomeState() else home.value, homeSelected = if (active) false else it.homeSelected) }; rebuildPlacePins(); if (loaded) applyCurrentPeriod() } }
        viewModelScope.launch { home.collect { value -> mutableState.update { it.copy(home = if (it.filmingMode) HomeState() else value, homeSelected = it.homeSelected && value.mapPin != null,
            homeTripCount = value.coordinate?.let { point -> AtlasHome.tripsStarted(point, it.exploration.trips.mapNotNull { trip -> trip.route.firstOrNull() }) } ?: 0) } } }
        viewModelScope.launch { events.collect { event -> when (event) {
            WorldChange.places -> schedulePlacesReload()
            WorldChange.discoveries -> reloadDiscoveries()
            WorldChange.trips, WorldChange.territory, WorldChange.revealed, WorldChange.sync -> {
                stale = true
                if (loaded) coalescer.schedule()
            }
            else -> Unit
        } } }
    }
    fun loadIfNeeded() { if (!loaded || stale) { loaded = true; reload() } }
    fun reload() {
        coalescer.cancel()
        loadTask?.cancel()
        filterTask?.cancel()
        loaded = true
        stale = false
        mutableState.update { it.copy(isLoading = it.isLoading || it.exploration.isEmpty, failed = false) }
        loadTask = viewModelScope.launch {
            try {
                val atlas = atlasProvider()
                val trips = repository.fetchTripsForMap()
                val hashes = territoryStore.visitedHashes()
                val tiles = tileStore.tiles().mapNotNull {
                    val key = it.key ?: return@mapNotNull null
                    RevealedTile(key, it.cells ?: byteArrayOf(), it.geometry ?: byteArrayOf(), it.updatedAt ?: Instant.EPOCH)
                }
                trips.forEach { Trip.invalidatePreviewCache(it.id) }
                val layer = RevealedLayer.build(tiles, atlas)
                val exploration = MapExploration.build(trips, hashes, atlas, layer.regionKm)
                ensureActive()
                cachedTrips = trips
                allTime = exploration to layer
                applyCurrentPeriod()
                reloadPlaces()
                reloadDiscoveries()
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { stale = true; mutableState.update { it.copy(isLoading = false, isFiltering = false, failed = true) } }
        }
    }
    fun setAppearance(value: AtlasAppearance) {
        if (value == state.value.appearance) return
        preferences.set("atlas.mapAppearance", Json.encodeToString(value.model()))
        mutableState.update { it.copy(appearance = value, fog = MapFog(it.revealed, FogLook.of(value.model()))) }
    }
    fun setPeriod(period: AtlasPeriod) {
        if (period == state.value.period) return
        mutableState.update { it.copy(period = period, selection = null, selectedRoute = emptyList(), selectedRegionTrips = emptyList()) }
        rebuildPlacePins()
        filterTask?.cancel()
        filterTask = viewModelScope.launch { applyCurrentPeriod(); ensureActive(); fitAll() }
    }
    private suspend fun applyCurrentPeriod() {
        val cached = allTime ?: return
        val period = state.value.period
        val atlas = atlasProvider()
        if (state.value.filmingMode) {
            val safeTrips = filmingTrips(cachedTrips)
            val layer = AtlasPreviewLayer.build(safeTrips, atlas)
            val hashes = safeTrips.flatMap { it.previewCoordinates }.mapTo(mutableSetOf()) { GeohashEncoder.encode(it.latitude, it.longitude, 6) }
            val filtered = if (period == AtlasPeriod.AllTime) safeTrips else safeTrips.filter { period.contains(it.startDate, now(), zone) }
            val revealed = if (period == AtlasPeriod.AllTime) layer else AtlasPreviewLayer.build(filtered, atlas)
            val exploration = MapExploration.build(filtered, hashes, atlas, revealed.regionKm)
            apply(exploration, revealed, atlas)
            return
        }
        if (period == AtlasPeriod.AllTime) { apply(cached.first, cached.second, atlas); return }
        mutableState.update { it.copy(isFiltering = true) }
        val trips = cachedTrips.filter { it.previewPolyline?.isNotEmpty() == true && period.contains(it.startDate, now(), zone) }
        val layer = AtlasPreviewLayer.build(trips, atlas)
        val hashes = trips.flatMap { it.previewCoordinates }.mapTo(mutableSetOf()) { GeohashEncoder.encode(it.latitude, it.longitude, 6) }
        val exploration = MapExploration.build(trips, hashes, atlas, layer.regionKm)
        currentCoroutineContext().ensureActive()
        if (state.value.period == period) apply(exploration, layer, atlas)
    }
    private fun apply(exploration: MapExploration, layer: RevealedLayer, atlas: RegionAtlas) {
        fogRebuilds++
        mutableState.update { old ->
            val selection = old.selection?.takeIf { resolve(it, exploration) }
            selected(old.copy(exploration = exploration, revealed = layer, selection = selection,
                fog = MapFog(layer, FogLook.of(old.appearance.model())), overview = AtlasOverview.build(exploration, atlas, allTime?.first ?: exploration),
                journal = AtlasJournal.build(exploration, layer, finds.seals, finds.hints), isFiltering = false, isLoading = false,
                homeTripCount = old.home.coordinate?.let { AtlasHome.tripsStarted(it, exploration.trips.mapNotNull { trip -> trip.route.firstOrNull() }) } ?: 0))
        }
    }
    fun schedulePlacesReload() {
        if (!loaded) return
        placesTask?.cancel()
        placesTask = viewModelScope.launch { delay(placesReloadDebounceMillis); reloadPlaces() }
    }
    private suspend fun reloadPlaces() {
        val places = placeStore.fetchPlaces()
        val passes = placeStore.passes(places.map { it.id })
        val seeds = places.map { AtlasPlaces.Seed(it.id, GeoPoint(it.latitude, it.longitude), it.name,
            PlaceStats.build(passes[it.id].orEmpty()).medianElapsed, passes[it.id].orEmpty().map { pass -> pass.timestamp }) }
        currentCoroutineContext().ensureActive()
        placeSeeds = seeds
        rebuildPlacePins()
    }
    private fun rebuildPlacePins() {
        val allPins = AtlasPlaces.build(placeSeeds, state.value.period, now(), zone)
        val pins = if (state.value.filmingMode) allPins.filterNot { pin -> hiddenPlace(pin.coordinate) } else allPins
        mutableState.update { it.copy(placePins = pins, selectedPlaceId = it.selectedPlaceId?.takeIf { id -> pins.any { pin -> pin.id == id } }) }
    }

    private fun filmingTrips(trips: List<Trip>): List<Trip> {
        val homeZone = home.value.activeZone?.let { it.centre to it.radius }
        return trips.map { trip ->
            if (trip.trackPoints.isEmpty()) trip else {
                val result = TripHiddenPartsResolver.resolve(trip.trackPoints, trip.recordingBreaks,
                    trip.hiddenParts?.startMetres ?: defaultHiddenEdgeMetres() ?: 0,
                    trip.hiddenParts?.endMetres ?: defaultHiddenEdgeMetres() ?: 0,
                    trip.hiddenParts?.sections.orEmpty(), homeZone)
                trip.copy(previewPolyline = result.previewPoints.takeIf { it.size >= 2 }?.let(Trip::encodePolyline),
                    previewBreaks = result.previewBreaks, distance = if (trip.hiddenParts?.numbersVisibleOnly == true) result.visibleDistance else trip.distance,
                    photos = trip.photos.filterNot { photo -> TripHiddenPartsResolver.photoIsHidden(photo.capturedAt,
                        photo.exifLatitude?.let { lat -> photo.exifLongitude?.let { lon -> GeoPoint(lat, lon) } }, trip.trackPoints, result, homeZone) })
            }
        }
    }
    private fun hiddenPlace(point: GeoPoint): Boolean {
        val homeZone = home.value.activeZone?.let { it.centre to it.radius }
        if (homeZone != null && GeometryUtils.haversineDistance(point, homeZone.first) < homeZone.second) return true
        return cachedTrips.any { trip -> trip.trackPoints.isNotEmpty() && run {
            val resolution = TripHiddenPartsResolver.resolve(trip.trackPoints, trip.recordingBreaks,
                trip.hiddenParts?.startMetres ?: defaultHiddenEdgeMetres() ?: 0,
                trip.hiddenParts?.endMetres ?: defaultHiddenEdgeMetres() ?: 0,
                trip.hiddenParts?.sections.orEmpty(), homeZone)
            trip.trackPoints.indices.any { resolution.hidden[it] && GeometryUtils.haversineDistance(point, trip.trackPoints[it].coordinate) <= TripHiddenPartsResolver.photoNearMetres }
        } }
    }
    fun reloadDiscoveries() {
        if (!loaded) return
        findsTask?.cancel()
        findsTask = viewModelScope.launch {
            try {
                val found = discoveries.load(state.value.revealed)
                ensureActive()
                finds = found
                mutableState.update { old -> old.copy(journal = AtlasJournal.build(old.exploration, old.revealed, found.seals, found.hints),
                    selection = old.selection?.takeIf { resolve(it, old.exploration) }) }
                val pending = pendingDiscovery
                pendingDiscovery = null
                if (pending != null && found.seals.any { it.id == pending }) focusDiscovery(pending)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.update { it.copy(failed = true) } }
        }
    }
    // iOS: auto-zoom to the region only from the country view, where that IS the gesture; at street
    // level a tap that misses the road is a miss, and flinging the camera out loses the place.
    fun selectRegion(point: GeoPoint, zoom: Boolean = true) {
        select(atlasProvider().region(containing = point)?.id?.takeIf { state.value.exploration.region(it) != null }?.let { AtlasSelection.Region(it) }, zoom)
    }
    fun selectRoad(ids: List<UUID>) {
        val sorted = ids.mapNotNull { state.value.exploration.trip(it) }.sortedByDescending { it.startDate }.map { it.id }
        if (sorted.isEmpty()) return
        select(if (sorted.size == 1) AtlasSelection.Trip(sorted.first()) else AtlasSelection.Road(sorted), zoom = false)
    }
    fun select(selection: AtlasSelection?, zoom: Boolean = true) {
        if (selection == state.value.selection && state.value.selectedPlaceId == null && !state.value.homeSelected) return
        if (selection != null && !resolve(selection, state.value.exploration)) return
        mutableState.update { old -> selected(old.copy(selection = selection, selectedPlaceId = null, homeSelected = false,
            detentBeforeSubstate = if (old.selection == null) {
                if (old.detent == AtlasSheetDetent.peek) AtlasSheetDetent.collapsed else old.detent
            } else old.detentBeforeSubstate,
            detent = AtlasSheetDetent.collapsed)) }
        if (!zoom) return
        when (selection) {
            is AtlasSelection.Region -> atlasProvider().region(selection.id)?.bounds?.let { camera(MapCameraCommand.Fit(it, MapCameraCommand.Padding.region)) }
            is AtlasSelection.Trip -> state.value.exploration.trip(selection.id)?.route?.let { bounds(it) }?.let { camera(MapCameraCommand.Fit(it, MapCameraCommand.Padding.trip)) }
            else -> Unit
        }
    }
    fun selectPlace(id: UUID) {
        if (state.value.placePins.none { it.id == id }) return
        mutableState.update { selected(it.copy(selection = null, selectedPlaceId = id, homeSelected = false, detent = AtlasSheetDetent.peek)) }
    }
    fun selectHome() {
        if (state.value.home.mapPin == null) return
        mutableState.update { selected(it.copy(selection = null, selectedPlaceId = null, homeSelected = true, detent = AtlasSheetDetent.peek)) }
    }
    fun backFromSubstate() { val detent = state.value.detentBeforeSubstate; clearSelection(detent) }
    fun closeSelection() { clearSelection(AtlasSheetDetent.collapsed) }
    private fun clearSelection(detent: AtlasSheetDetent) { mutableState.update { selected(it.copy(selection = null, selectedPlaceId = null, homeSelected = false, detent = detent)) } }
    fun expand() { clearSelection(AtlasSheetDetent.expanded) }
    fun setDetent(detent: AtlasSheetDetent) { mutableState.update { it.copy(detent = detent) } }
    fun toggleSheet() {
        if (state.value.detent == AtlasSheetDetent.peek) expand()
        else setDetent(if (state.value.detent == AtlasSheetDetent.expanded) AtlasSheetDetent.collapsed else AtlasSheetDetent.expanded)
    }
    fun settleSheet(top: Double, velocity: Double, slot: AtlasSlot, variant: AtlasSummaryVariant) {
        if (state.value.selection is AtlasSelection.Region && AtlasSlot.substateDismisses(top, velocity, slot, variant)) {
            closeSelection()
        } else setDetent(AtlasSlot.settle(top, velocity, slot, variant))
    }
    fun tapMap(point: GeoPoint, zoom: Boolean = true) { if (state.value.detent == AtlasSheetDetent.expanded) setDetent(AtlasSheetDetent.collapsed) else selectRegion(point, zoom) }

    /** iOS: a city chip in a region asks to look closer; the region stays selected. */
    fun focusCity(point: GeoPoint) { camera(MapCameraCommand.Fit(around(point, focusSpanMetres), MapCameraCommand.Padding.overview)) }
    fun focusDiscovery(id: UUID) {
        val seal = finds.seals.firstOrNull { it.id == id }
        if (seal == null) { pendingDiscovery = id; return }
        pendingDiscovery = null
        select(AtlasSelection.Discovery(id), false)
        camera(MapCameraCommand.Fit(around(seal.coordinate, focusSpanMetres), MapCameraCommand.Padding.trip))
    }
    fun fitAll() { bounds(state.value.exploration.trips.flatMap { it.route })?.let { camera(MapCameraCommand.Fit(it, MapCameraCommand.Padding.overview)) } }
    fun locateUser() {
        if (state.value.locationPermission == AtlasLocationPermission.denied || state.value.locationPermission == AtlasLocationPermission.restricted)
            mutableState.update { it.copy(showsLocationDialog = true) }
        else mutableState.update { it.copy(camera = AtlasCameraAction.UserLocation, cameraRevision = it.cameraRevision + 1) }
    }
    fun dismissLocationDialog() { mutableState.update { it.copy(showsLocationDialog = false) }; fitAll() }
    fun acknowledgeCamera(revision: Long) { mutableState.update { if (it.cameraRevision == revision) it.copy(camera = null) else it } }
    fun requestShare(): Boolean {
        if (!state.value.online) { mutableState.update { it.copy(shareOfflineNotice = true) }; return false }
        return !state.value.revealed.isEmpty
    }
    fun dismissShareNotice() { mutableState.update { it.copy(shareOfflineNotice = false) } }
    private fun camera(command: MapCameraCommand) { mutableState.update { it.copy(camera = AtlasCameraAction.Command(command), cameraRevision = it.cameraRevision + 1) } }
    private fun resolve(selection: AtlasSelection, exploration: MapExploration) = when (selection) {
        is AtlasSelection.Region -> exploration.region(selection.id) != null
        is AtlasSelection.Trip -> exploration.trip(selection.id) != null
        is AtlasSelection.Road -> selection.ids.any { exploration.trip(it) != null }
        is AtlasSelection.Discovery -> finds.seals.any { it.id == selection.id }
    }
    private fun selected(value: AtlasState): AtlasState = value.copy(
        selectedRoute = (value.selection as? AtlasSelection.Trip)?.let { value.exploration.trip(it.id)?.route }.orEmpty(),
        selectedRegionTrips = (value.selection as? AtlasSelection.Region)?.let { value.exploration.region(it.id)?.tripIds }
            .orEmpty().mapNotNull { value.exploration.trip(it) }.sortedByDescending { it.startDate },
    )
    private fun around(point: GeoPoint, metres: Double): GeoBounds {
        val half = metres / 2
        val dLat = half / 111_320.0
        val dLon = half / (111_320.0 * kotlin.math.max(0.01, kotlin.math.cos(point.latitude * kotlin.math.PI / 180)))
        return GeoBounds(point.latitude - dLat, point.latitude + dLat, point.longitude - dLon, point.longitude + dLon)
    }
    private fun bounds(points: List<GeoPoint>): GeoBounds? = if (points.isEmpty()) null else GeoBounds(
        points.minOf { it.latitude }, points.maxOf { it.latitude }, points.minOf { it.longitude }, points.maxOf { it.longitude })
    companion object { const val focusSpanMetres = 12_000.0 }
}
