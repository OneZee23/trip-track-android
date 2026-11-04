// iOS: PlacesTabViewModel.swift, PlacesView.swift
package com.onezee.triptrack.feature.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onezee.triptrack.data.PlaceStore
import com.onezee.triptrack.data.TripRepository
import com.onezee.triptrack.feature.settings.PresentationPreferences
import com.onezee.triptrack.feature.settings.WorldChange
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// iOS: PlacesView.swift (search is a modal, with its own query).
data class PlacesSearch(val query: String = "", val results: List<PlaceListItem> = emptyList())

// iOS: PlacesTabViewModel.swift.
data class PlacesTabState(
    val items: List<PlaceListItem> = emptyList(),
    val groups: List<PlacesPresentation.Group> = emptyList(),
    val suggestions: List<PlaceSuggestion>? = null,
    val recentTrips: List<Trip> = emptyList(),
    val lastPass: PlaceLastPass.Reading? = null,
    val lastTripId: UUID? = null,
    val sort: PlacesSort = PlacesSort.recent,
    val search: PlacesSearch? = null,
    val loaded: Boolean = false,
    val failed: Boolean = false,
) {
    val knownSuggestions get() = suggestions.orEmpty()
    val isComputingSuggestions get() = suggestions == null
    val showsOrganization get() = items.size >= PlacesPresentation.manyPlaces
}

// iOS: PlaceManager.createPlace. The recording owner supplies reconciliation/backfill.
fun interface PlaceCreator { suspend fun create(suggestion: PlaceSuggestion) }

// iOS: PlaceSuggestions.buildDetached. The algorithm is the shared core:logic implementation.
private suspend fun buildDetached(previews: List<TripPreviewRef>, taken: Set<UUID>, dispatcher: CoroutineDispatcher) =
    withContext(dispatcher) { PlaceSuggestions.build(previews, taken) }

// iOS: PlacesTabViewModel.swift.
class PlacesTabViewModel(
    private val placeStore: PlaceStore,
    private val repository: TripRepository,
    private val preferences: PresentationPreferences,
    private val placeCreator: PlaceCreator,
    dispatcher: CoroutineDispatcher,
    events: Flow<WorldChange> = emptyFlow(),
    private val locale: Locale = Locale.getDefault(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Instant = Instant::now,
) : ViewModel(CoroutineScope(SupervisorJob() + dispatcher)) {
    private val worker = dispatcher
    private val mutableState = MutableStateFlow(PlacesTabState(sort = PlacesSort.load(preferences.string(PlacesSort.defaultsKey))))
    val state = mutableState.asStateFlow()
    var suggestionDebounceMillis = 300L
    private var suggestionTask: Job? = null
    private var reloadTask: Job? = null

    init {
        viewModelScope.launch { events.collect { event -> when (event) {
            WorldChange.places -> reload()
            WorldChange.trips, WorldChange.sync -> refreshSuggestions()
            else -> Unit
        } } }
        reload()
    }

    fun reload() {
        reloadTask?.cancel()
        reloadTask = viewModelScope.launch {
            try {
                val places = placeStore.fetchPlaces()
                val passes = placeStore.passes(places.map { it.id })
                val items = PlaceListItem.sorted(places.map { PlaceListItem.build(it, passes[it.id].orEmpty(), now()) })
                val freshest = passes.mapNotNull { (id, all) -> PlaceLastPass.reading(id, all) }.maxByOrNull { it.at }
                    ?.takeUnless { seenKey(it) == preferences.string(SEEN_KEY) }
                val recent = if (items.isEmpty()) repository.fetchTrips(recentTripsShown, 0) else emptyList()
                ensureActive()
                mutableState.update { present(it.copy(items = items, lastPass = freshest, recentTrips = recent, loaded = true, failed = false)) }
                refreshSuggestions()
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.update { it.copy(failed = true) } }
        }
    }

    fun refreshSuggestions() {
        suggestionTask?.cancel()
        suggestionTask = viewModelScope.launch {
            delay(suggestionDebounceMillis)
            try {
                val previews = repository.tripPreviews(false).map { TripPreviewRef(it.id, it.startDate, it.previewPolyline) }
                val taken = placeStore.fetchPlaces().mapTo(mutableSetOf()) { it.id } + repository.checkpointPlaceIds()
                val found = buildDetached(previews, taken, worker).map { suggestion ->
                    val cached = repository.cachedGeocode(GeohashEncoder.encode(suggestion.latitude, suggestion.longitude, 5))
                    suggestion.copy(name = listOfNotNull(cached?.locality, cached?.region).firstOrNull(String::isNotEmpty))
                }
                ensureActive()
                mutableState.update { it.copy(suggestions = found, lastTripId = previews.firstOrNull()?.id, failed = false) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.update { it.copy(failed = true) } }
        }
    }

    fun save(suggestion: PlaceSuggestion) {
        suggestionTask?.cancel()
        mutableState.update { it.copy(suggestions = it.knownSuggestions.filterNot { item -> item.id == suggestion.id }) }
        // Persist through the manager boundary so history reconciliation is not bypassed.
        viewModelScope.launch {
            try { placeCreator.create(suggestion); reload() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.update { it.copy(failed = true) }; refreshSuggestions() }
        }
    }

    fun markLastPassSeen() {
        state.value.lastPass?.let { preferences.set(SEEN_KEY, seenKey(it)) }
        mutableState.update { it.copy(lastPass = null) }
    }
    fun setSort(sort: PlacesSort) {
        preferences.set(PlacesSort.defaultsKey, sort.rawValue)
        mutableState.update { present(it.copy(sort = sort)) }
    }
    fun openSearch() { mutableState.update { it.copy(search = PlacesSearch(results = matches(it, ""))) } }
    fun search(query: String) { mutableState.update { it.copy(search = PlacesSearch(query, matches(it, query))) } }
    // iOS searchMatches: the matches WITHOUT groups (a heading over the results would answer a question
    // nobody asked), in the chosen order: the sort chips stay where they were.
    private fun matches(state: PlacesTabState, query: String) =
        PlacesPresentation.sort(PlacesPresentation.filter(state.items, query, locale), state.sort, locale)
    fun dismissSearch() { mutableState.update { it.copy(search = null) } }
    private fun present(value: PlacesTabState) = value.copy(
        groups = PlacesPresentation.build(value.items, "", value.sort, locale, now(), zone),
        search = value.search?.let { it.copy(results = matches(value, it.query)) },
    )
    private fun seenKey(reading: PlaceLastPass.Reading) = "${reading.placeId.toString().uppercase(Locale.ROOT)}|${reading.at.epochSecond}"
    companion object {
        const val recentTripsShown = 3
        private const val SEEN_KEY = "places.lastPassSeen"
    }
}
