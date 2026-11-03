// iOS: FollowListView.swift (load, loadCounts), PublicGarageView.swift (load),
// PublicVehicleView.swift (load, loadVehicle, loadTrips), PublicJourneysView.swift (load, fetchPage)
package com.onezee.triptrack.feature.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onezee.triptrack.feature.stats.MeAggregates
import com.onezee.triptrack.logic.GeohashEncoder
import com.onezee.triptrack.logic.RegionAtlas
import com.onezee.triptrack.logic.build
import com.onezee.triptrack.logic.previewCoordinates
import com.onezee.triptrack.logic.invalidatePreviewCache
import com.onezee.triptrack.logic.visitedCityCount
import com.onezee.triptrack.model.*
import com.onezee.triptrack.network.ApiError
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What a loaded screen is: still asking, answered, or unable to ask. */
enum class LoadState { loading, loaded, failed }

data class FollowListState(
    val mode: FollowListMode,
    val users: List<SocialAuthor> = emptyList(),
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    /** Both counts for the segment labels, from the profile: real numbers or plain labels, never fake. */
    val counts: Pair<Int, Int>? = null,
    /** The viewed account's name: the screen's title, the mode lives in the segment. */
    val accountName: String? = null,
)

/**
 * iOS: FollowListView. The segment flips the list in place; a response for the mode no longer
 * shown is dropped rather than serialized away, so the fresh mode always fetches.
 */
class FollowListViewModel(
    private val accountId: UUID,
    mode: FollowListMode,
    private val api: SocialApi,
    private val signedIn: () -> Boolean,
    private val worker: CoroutineDispatcher,
) : ViewModel() {
    private val mutable = MutableStateFlow(FollowListState(mode))
    val state: StateFlow<FollowListState> = mutable.asStateFlow()
    private var started = false
    private var generation = 0

    fun start() {
        if (started) return
        started = true
        load()
        viewModelScope.launch {
            try {
                val profile = withContext(worker) { api.profile(accountId) }
                mutable.update { it.copy(counts = profile.followerCount to profile.followingCount, accountName = profile.displayName?.trim()) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* The chips fall back to labels without numbers. */ }
        }
    }

    fun setMode(mode: FollowListMode) {
        if (mode == state.value.mode) return
        mutable.update { it.copy(mode = mode, users = emptyList(), loadFailed = false) }
        load()
    }

    fun load(): Job {
        val current = ++generation
        val mode = state.value.mode
        mutable.update { it.copy(isLoading = true) }
        return viewModelScope.launch {
            try {
                val response = withContext(worker) { api.people(accountId, mode) }
                if (current == generation && mode == state.value.mode) mutable.update { it.copy(users = response.users, loadFailed = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (current == generation && mode == state.value.mode) mutable.update { it.copy(loadFailed = true) } }
            finally { if (current == generation && mode == state.value.mode) mutable.update { it.copy(isLoading = false) } }
        }
    }
}

data class PublicGarageState(val state: LoadState = LoadState.loading, val vehicles: List<PublicVehicle> = emptyList())

/** iOS: PublicGarageView. Unsold first, then sold, in the server's order. */
class PublicGarageViewModel(private val accountId: UUID, private val api: SocialApi, private val worker: CoroutineDispatcher) : ViewModel() {
    private val mutable = MutableStateFlow(PublicGarageState())
    val state: StateFlow<PublicGarageState> = mutable.asStateFlow()

    init { load() }

    fun load(): Job {
        mutable.value = PublicGarageState()
        return viewModelScope.launch {
            mutable.value = try {
                val vehicles = withContext(worker) { api.garage(accountId) }
                PublicGarageState(LoadState.loaded, vehicles.sortedBy { it.soldAt != null })
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { PublicGarageState(LoadState.failed) }
        }
    }
}

/** The road a car has driven, from its owner's public trips filtered to it (iOS `Biography`). */
data class VehicleBiography(
    val trips: List<Trip>,
    val aggregates: MeAggregates,
    val regions: Int,
    val cities: Int,
    val routes: List<List<GeoPoint>>,
    /** The regions themselves, for the chips under the mini map (iOS `regions`). */
    val regionStats: List<MapRegionStat> = emptyList(),
)

data class PublicVehicleState(
    val vehicle: PublicVehicle? = null,
    /** The car was hidden, removed, or we were blocked while the screen was open. */
    val gone: Boolean = false,
    val failed: Boolean = false,
    val biography: VehicleBiography? = null,
    val catalogVariant: com.onezee.triptrack.model.CarCatalog.Variant? = null,
)

/**
 * iOS: PublicVehicleView. Three answers that must not be confused: the car came, the car is no
 * longer there for us (a privacy decision beats our cached copy), or the question failed (the copy
 * we came with stays, with a line that refreshing did not work).
 */
class PublicVehicleViewModel(
    private val accountId: UUID,
    private val vehicleId: UUID,
    preloaded: PublicVehicle?,
    private val api: SocialApi,
    private val worker: CoroutineDispatcher,
    private val now: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {
    private val mutable = MutableStateFlow(PublicVehicleState(vehicle = preloaded))
    val state: StateFlow<PublicVehicleState> = mutable.asStateFlow()

    init { load() }

    fun load(): Job = viewModelScope.launch {
        mutable.update { it.copy(failed = false) }
        val vehicle = async {
            try { Result.success(withContext(worker) { api.vehicle(accountId, vehicleId) }) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { Result.failure(error) }
        }
        val biography = async { withContext(worker) { biography() } }
        val fetched = vehicle.await()
        val bio = biography.await()
        fetched.onSuccess { v ->
            val variant = withContext(worker) { com.onezee.triptrack.logic.CarCatalog().resolve(v.catalogModelId, v.catalogGenerationId, v.catalogVariantId) }
            mutable.update { it.copy(vehicle = v, gone = false, catalogVariant = variant) }
        }
            .onFailure { error ->
                if (error.serverCode == "USER_NOT_FOUND" || error is ApiError.VehicleNotFound) mutable.update { it.copy(vehicle = null, gone = true, failed = false) }
                else if (state.value.vehicle == null) mutable.update { it.copy(failed = true) }
            }
        if (bio != null && !state.value.gone) mutable.update { it.copy(biography = bio) }
        else if (fetched.getOrNull()?.mapVisible == true) mutable.update { it.copy(failed = true) }
    }

    private suspend fun biography(): VehicleBiography? {
        val result = RemoteTrips.load(api, accountId, vehicleId)
        if (result.failed) return null
        result.trips.forEach { Trip.invalidatePreviewCache(it.id) }
        val mine = result.trips.map { it.copy(vehicleId = vehicleId) }
        if (mine.isEmpty()) return VehicleBiography(emptyList(), MeAggregates(), 0, 0, emptyList())
        val hashes = mine.flatMap { it.previewCoordinates }.mapTo(HashSet()) { GeohashEncoder.encode(it.latitude, it.longitude, 6) }
        val exploration = MapExploration.build(mine, hashes, RegionAtlas.shared)
        return VehicleBiography(mine, MeAggregates.compute(mine, now = now(), zone = zone()), exploration.regions.size,
            exploration.regions.sumOf { it.visitedCityCount }, mine.map { it.previewCoordinates }.filter { it.size >= 2 }, exploration.regions)
    }
}

data class PublicJourneysState(
    val state: LoadState = LoadState.loading,
    val journeys: List<PublicJourneyDto> = emptyList(),
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
)

/** iOS: PublicJourneysView. Pages of 20, deduplicated by id; a repeated cursor ends the list. */
class PublicJourneysViewModel(private val accountId: UUID, private val api: SocialApi, private val worker: CoroutineDispatcher) : ViewModel() {
    private val mutable = MutableStateFlow(PublicJourneysState())
    val state: StateFlow<PublicJourneysState> = mutable.asStateFlow()
    private var cursor: String? = null
    private var seenIds = HashSet<UUID>()
    private var seenCursors = HashSet<String>()

    init { load() }

    fun load(): Job {
        cursor = null; seenIds = HashSet(); seenCursors = HashSet()
        mutable.value = PublicJourneysState()
        return fetchPage()
    }

    fun loadMoreIfNeeded(current: PublicJourneyDto): Job? {
        val s = state.value
        if (cursor == null || s.isLoadingMore || s.state == LoadState.failed) return null
        val index = s.journeys.indexOfFirst { it.id == current.id }
        if (index < 0 || index < s.journeys.size - 3) return null
        return fetchPage()
    }

    private fun fetchPage(): Job {
        val first = state.value.journeys.isEmpty()
        mutable.update { it.copy(isLoadingMore = true) }
        return viewModelScope.launch {
            try {
                val page = withContext(worker) { api.journeys(accountId, cursor, PAGE_SIZE) }
                val fresh = page.journeys.filter { seenIds.add(it.id) }
                cursor = page.nextCursor?.takeIf { it.isNotEmpty() && seenCursors.add(it) }
                mutable.update { it.copy(state = LoadState.loaded, journeys = it.journeys + fresh, hasMore = cursor != null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (first) mutable.update { it.copy(state = LoadState.failed) } else cursor = null }
            finally { mutable.update { it.copy(isLoadingMore = false) } }
        }
    }

    private companion object { const val PAGE_SIZE = 20 }
}
