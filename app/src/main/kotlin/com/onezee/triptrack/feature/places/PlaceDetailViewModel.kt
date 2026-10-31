// iOS: PlaceDetailViewModel.swift
package com.onezee.triptrack.feature.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onezee.triptrack.data.PlaceStore
import com.onezee.triptrack.data.TripRepository
import com.onezee.triptrack.feature.settings.WorldChange
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// iOS: TripFocus.swift. Navigation adapters translate this to the trip screen's focus.
sealed interface PlaceTripFocus {
    data object Top : PlaceTripFocus
    data class Checkpoint(val id: UUID) : PlaceTripFocus
}

// iOS: PlaceDetailViewModel.swift.
data class PlaceDetailState(
    val place: Place? = null,
    val stats: PlaceStats = PlaceStats.build(emptyList()),
    val passes: List<PlacePass> = emptyList(),
    val routes: List<List<GeoPoint>> = emptyList(),
    val directionLabels: Map<UUID, String> = emptyMap(),
    val routeNames: Map<UUID, String> = emptyMap(),
    val geocodedName: String? = null,
    val address: String? = null,
    val tiles: List<PlaceScreen.DateTile> = emptyList(),
    val showsAllPasses: Boolean = false,
    val loaded: Boolean = false,
    val failed: Boolean = false,
) {
    val visiblePasses get() = if (showsAllPasses) passes else passes.take(PlaceScreen.visiblePasses)
}

// iOS: PlaceDetailViewModel.swift.
class PlaceDetailViewModel(
    val placeId: UUID,
    private val placeStore: PlaceStore,
    private val repository: TripRepository,
    dispatcher: CoroutineDispatcher,
    events: Flow<WorldChange> = emptyFlow(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val localityLookup: suspend (GeoPoint) -> String? = {
        repository.cachedLocality(GeohashEncoder.encode(it.latitude, it.longitude, 5))
    },
) : ViewModel(CoroutineScope(SupervisorJob() + dispatcher)) {
    private val mutableState = MutableStateFlow(PlaceDetailState())
    val state = mutableState.asStateFlow()
    private var loadTask: Job? = null
    init {
        viewModelScope.launch { events.filter { it == WorldChange.places }.collect { load() } }
    }
    fun load() {
        loadTask?.cancel()
        loadTask = viewModelScope.launch {
            try {
                val place = placeStore.fetchPlace(placeId)
                val all = placeStore.passes(placeId)
                val passes = all.sortedByDescending { it.timestamp }
                val stats = PlaceStats.build(all)
                // Keep the first row if a broken historical store contains duplicate IDs.
                val previews = repository.tripPreviews(false).map { TripPreviewRef(it.id, it.startDate, it.previewPolyline) }
                    .groupBy { it.id }.mapValues { it.value.first() }
                val routes = passes.map { it.tripId }.distinct().take(maxRoutes)
                    .mapNotNull { previews[it]?.previewCoordinates?.takeIf { points -> points.size > 1 } }
                val labels = buildMap {
                    for (direction in stats.directions) {
                        val end = previews[direction.latestTripId]?.previewCoordinates?.lastOrNull() ?: continue
                        localityLookup(end)?.let { put(direction.latestTripId, it) }
                    }
                }
                val cache = mutableMapOf<String, String?>()
                suspend fun name(point: GeoPoint): String? {
                    val cell = GeohashEncoder.encode(point.latitude, point.longitude, 5)
                    if (cache.containsKey(cell)) return cache[cell]
                    return localityLookup(point).also { cache[cell] = it }
                }
                val names = buildMap {
                    for (id in passes.map { it.tripId }.distinct()) {
                        val ends = PlaceScreen.endpoints(previews[id]?.previewCoordinates.orEmpty()) ?: continue
                        PlaceScreen.route(name(ends.first), name(ends.second))?.let { put(id, it) }
                    }
                }
                val geocoded = place?.let { localityLookup(GeoPoint(it.latitude, it.longitude)) }?.takeIf(String::isNotEmpty)
                ensureActive()
                mutableState.update { it.copy(place = place, passes = passes, stats = stats, routes = routes,
                    directionLabels = labels, routeNames = names, geocodedName = geocoded,
                    address = geocoded?.takeUnless { value -> value.equals(place.name.orEmpty(), true) },
                    tiles = PlaceScreen.tiles(all, zone), loaded = true, failed = false) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.update { it.copy(failed = true) } }
        }
    }
    fun showAllPasses() { mutableState.update { it.copy(showsAllPasses = true) } }
    fun rename(name: String?) = edit { placeStore.rename(placeId, name) }
    fun delete() {
        loadTask?.cancel()
        mutableState.update { it.copy(place = null) }
        edit { placeStore.deletePlace(placeId) }
    }
    private fun edit(block: suspend () -> Unit) { viewModelScope.launch {
        try { block(); load() }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { mutableState.update { it.copy(failed = true) } }
    } }
    suspend fun focus(tripId: UUID): PlaceTripFocus = repository.fetchTripDetail(tripId)?.checkpoints
        ?.firstOrNull { it.placeId == placeId }?.let { PlaceTripFocus.Checkpoint(it.id) } ?: PlaceTripFocus.Top
    companion object { const val maxRoutes = 20 }
}
