// iOS: PublicMapView.swift (startBounds, tripsPanelHeight, hidesRegions, openTrip),
// MyMapViewModel.loadRemote / remoteLayer, GeoBounds(coveringRoutes:)
package com.onezee.triptrack.feature.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onezee.triptrack.feature.atlas.AtlasCameraAction
import com.onezee.triptrack.feature.atlas.AtlasSelection
import com.onezee.triptrack.feature.atlas.AtlasState
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.map.MapFog
import com.onezee.triptrack.map.camera.MapCameraCommand
import com.onezee.triptrack.map.fog.FogLook
import com.onezee.triptrack.model.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** iOS PublicMapView's pure rules: where the camera opens, how tall the trips sheet is. */
object PublicMapStart {
    /** How many of the profile's latest trips frame the camera while the map loads. */
    const val TRIP_LIMIT = 10
    /** Rows visible without scrolling; with more, half of the next shows the list scrolls. */
    const val VISIBLE_TRIP_ROWS = 4
    const val TRIP_ROW_HEIGHT = 56f

    fun bounds(trips: List<Trip>): GeoBounds? = covering(trips.flatMap { it.previewCoordinates })

    /** Every point of every route, not their middles: two drives down France frame France, not its centre. */
    fun coveringRoutes(routes: List<List<GeoPoint>>): GeoBounds? = covering(routes.flatten())

    fun covering(points: List<GeoPoint>): GeoBounds? = if (points.isEmpty()) null else GeoBounds(
        points.minOf { it.latitude }, points.maxOf { it.latitude }, points.minOf { it.longitude }, points.maxOf { it.longitude })

    /** The trips sheet by number: its header 44 with 12 above, the list's 12 + 12, the rows. */
    fun tripsPanelHeight(count: Int): Float {
        val rows = if (count > VISIBLE_TRIP_ROWS) VISIBLE_TRIP_ROWS + .5f else maxOf(count, 1).toFloat()
        return 12 + 44 + 12 + rows * TRIP_ROW_HEIGHT + 12
    }

    /** «0 регионов» over routes is not a zero but a country the region atlas lacks: no cell then. */
    fun hidesRegions(regions: Int, trips: Int): Boolean = regions == 0 && trips > 0
}

/**
 * iOS: PublicMapView over its own MyMapViewModel(source: RemoteTripSource). The same atlas, one input
 * changed: someone's public trips from the server. Their fog is built on the fly from the previews
 * and never written: one's own opened world is the only store of one's own fog.
 */
class PublicMapViewModel(
    private val accountId: UUID,
    private val vehicleId: UUID?,
    private val api: SocialApi,
    private val worker: kotlinx.coroutines.CoroutineDispatcher,
    private val atlasProvider: () -> com.onezee.triptrack.logic.RegionAtlas = { com.onezee.triptrack.logic.RegionAtlas.shared },
) : ViewModel() {
    private val mutable = MutableStateFlow(AtlasState())
    val state: StateFlow<AtlasState> = mutable.asStateFlow()
    private val mutableFailed = MutableStateFlow(false)
    /** The load failed: an empty map then says so instead of claiming nothing was published. */
    val failed: StateFlow<Boolean> = mutableFailed.asStateFlow()
    private var generation = 0
    private val mutableOpening = MutableStateFlow<UUID?>(null)
    val openingTripId: StateFlow<UUID?> = mutableOpening.asStateFlow()

    init { load() }

    fun load(): Job {
        val current = ++generation
        if (state.value.exploration.isEmpty) mutable.update { it.copy(isLoading = true) }
        return viewModelScope.launch {
            val (result, exploration, layer) = withContext(worker) {
                val result = RemoteTrips.load(api, accountId, vehicleId)
                val atlas = atlasProvider()
                // The layer FIRST: its opened kilometres per region are what the region row prints.
                val layer = AtlasPreviewLayer.build(result.trips, atlas)
                val hashes = result.trips.flatMap { it.previewCoordinates }.mapTo(HashSet()) { GeohashEncoder.encode(it.latitude, it.longitude, 6) }
                Triple(result, MapExploration.build(result.trips, hashes, atlas, layer.regionKm), layer)
            }
            if (current != generation) return@launch
            mutableFailed.value = result.failed
            mutable.update { old ->
                val selection = old.selection?.takeIf { resolve(it, exploration) }
                selected(old.copy(isLoading = false, exploration = exploration, revealed = layer, selection = selection,
                    fog = MapFog(layer, FogLook.of(old.appearance.model()))))
            }
            // Then the flight to the whole routes (framesWholeRoutes): the opening frame was the profile's latest trips.
            PublicMapStart.coveringRoutes(exploration.trips.map { it.route })?.let { camera(MapCameraCommand.Fit(it, MapCameraCommand.Padding.overview)) }
        }
    }

    /** The region opens under the finger; the camera flies to it only from afar, as on the Atlas. */
    fun tapMap(point: GeoPoint, zoom: Boolean) {
        if (state.value.isLoading) return
        select(atlasProvider().region(containing = point)?.id?.takeIf { state.value.exploration.region(it) != null }?.let { AtlasSelection.Region(it) }, zoom)
    }

    fun selectRoad(ids: List<UUID>) {
        val sorted = ids.mapNotNull { state.value.exploration.trip(it) }.sortedByDescending { it.startDate }.map { it.id }
        if (sorted.isEmpty()) return
        select(if (sorted.size == 1) AtlasSelection.Trip(sorted.first()) else AtlasSelection.Road(sorted), zoom = false)
    }

    fun select(selection: AtlasSelection?, zoom: Boolean = true) {
        if (selection != null && !resolve(selection, state.value.exploration)) return
        mutable.update { selected(it.copy(selection = selection)) }
        if (!zoom || selection !is AtlasSelection.Region) return
        atlasProvider().region(selection.id)?.bounds?.let { camera(MapCameraCommand.Fit(it, MapCameraCommand.Padding.region)) }
    }

    fun closeSelection() = select(null)

    fun focusCity(point: GeoPoint) {
        val half = 6_000.0
        val dLat = half / 111_320.0
        val dLon = half / (111_320.0 * kotlin.math.max(0.01, kotlin.math.cos(Math.toRadians(point.latitude))))
        camera(MapCameraCommand.Fit(GeoBounds(point.latitude - dLat, point.latitude + dLat, point.longitude - dLon, point.longitude + dLon), MapCameraCommand.Padding.overview))
    }

    fun acknowledgeCamera(revision: Long) { mutable.update { if (it.cameraRevision == revision) it.copy(camera = null) else it } }

    /**
     * Someone's trip is asked from the server first: the map only has its preview, the card carries
     * the author and the reactions. A second tap while one is opening does nothing.
     */
    fun openTrip(id: UUID, onOpened: (SocialFeedTrip) -> Unit, onFailed: () -> Unit) {
        if (mutableOpening.value != null) return
        mutableOpening.value = id
        viewModelScope.launch {
            try { onOpened(withContext(worker) { api.trip(id, includeTrack = false).item }) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { onFailed() }
            finally { mutableOpening.value = null }
        }
    }

    private fun camera(command: MapCameraCommand) { mutable.update { it.copy(camera = AtlasCameraAction.Command(command), cameraRevision = it.cameraRevision + 1) } }

    private fun resolve(selection: AtlasSelection, exploration: MapExploration) = when (selection) {
        is AtlasSelection.Region -> exploration.region(selection.id) != null
        is AtlasSelection.Trip -> exploration.trip(selection.id) != null
        is AtlasSelection.Road -> selection.ids.any { exploration.trip(it) != null }
        is AtlasSelection.Discovery -> false
    }

    private fun selected(value: AtlasState): AtlasState = value.copy(
        selectedRoute = (value.selection as? AtlasSelection.Trip)?.let { value.exploration.trip(it.id)?.route }.orEmpty(),
        selectedRegionTrips = (value.selection as? AtlasSelection.Region)?.let { value.exploration.region(it.id)?.tripIds }
            .orEmpty().mapNotNull { value.exploration.trip(it) }.sortedByDescending { it.startDate },
    )
}
