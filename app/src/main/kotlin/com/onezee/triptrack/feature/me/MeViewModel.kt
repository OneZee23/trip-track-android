// iOS: ProfileView.swift, PreparedProfileHistory.swift, ProfileHeroCard.swift
package com.onezee.triptrack.feature.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onezee.triptrack.data.TripRepository
import com.onezee.triptrack.data.VehicleStore
import com.onezee.triptrack.feature.garage.VehicleStageState
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// iOS: ProfileHeroCard.swift. The identity/profile-sync owner supplies this snapshot.
data class ProfileIdentity(
    val isPlus: Boolean = false,
    val name: String = "",
    val avatarEmoji: String = "😎",
    val bio: String = "",
    val username: String = "",
    val country: String = "",
    val level: Int = 1,
    val xp: Int = 0,
    val effectiveBackground: ProfileBackground = ProfileBackground.none,
    val coverPhotoIds: List<UUID> = emptyList(),
    val mainVehiclePhotos: Map<UUID, String> = emptyMap(),
    val mainVehiclePhotoUrls: Map<UUID, String> = emptyMap(),
    val selectedVehicleId: UUID? = null,
    val unlockedBadgeIds: Set<String> = emptySet(),
    val badgeEarnCounts: Map<String, Int> = emptyMap(),
    val pinnedTripIds: List<UUID> = emptyList(),
)

// iOS: SettingsManager.togglePinnedTrip / syncProfileToServer. Public identity sync is independent of Cloud Sync.
interface ProfileIdentitySource {
    val identity: StateFlow<ProfileIdentity>
    suspend fun setPinnedTrips(ids: List<UUID>)
}

// iOS: VehicleStage.swift / ProfileGarageHeader.swift. [powerHp] is the catalogue's power of the chosen
// variant, which VehicleCatalogCaption appends to the caption («Volkswagen Polo · 2018 · 110 л.с.»).
data class HeroCarStage(val vehicle: Vehicle, val photoFilename: String?, val stage: VehicleStageState, val remoteURL: String? = null, val powerHp: Double? = null)

// iOS: ProfileHeroCard.swift.
data class ProfileHeroState(
    val identity: ProfileIdentity = ProfileIdentity(),
    val roadTime: RoadSceneTime = RoadSceneTime.day,
    val showsRoad: Boolean = true,
    val cars: List<HeroCarStage> = emptyList(),
    val tripCount: Int = 0,
    val distanceMetres: Double = 0.0,
    val regionCount: Int = 0,
    /** iOS: auth.isSignedIn. A signed-in account with no name shows the dimmed prompt «Добавьте имя». */
    val signedIn: Boolean = false,
)

// iOS: BadgesView.swift. Words and art resolve from generated resources by ID.
data class AchievementRow(val id: String, val category: BadgeCategory, val rarity: BadgeRarity, val unlocked: Boolean, val earnCount: Int)

// iOS: ProfileHistoryCalendar.swift.
data class HistoryCalendar(val kmByDay: Map<Instant, Double> = emptyMap(), val maxKmDay: Double = 0.0)

// iOS: ProfileView.swift.
enum class HistoryMode { grid, list }
// iOS: ProfileDest in ProfileView.swift.
enum class MeDestination { drafts, settings, achievements, garage }

// iOS: ProfileView.swift.
data class MeState(
    val loaded: Boolean = false,
    val allTrips: List<Trip> = emptyList(),
    val history: PreparedProfileHistory = PreparedProfileHistory.build(ProfileHistoryLibrary(emptyList()), emptyList(), "", null, null, Locale.ENGLISH),
    val hero: ProfileHeroState = ProfileHeroState(),
    val calendar: HistoryCalendar = HistoryCalendar(),
    val query: String = "",
    val from: Instant? = null,
    val to: Instant? = null,
    val showsCalendar: Boolean = false,
    val mode: HistoryMode = HistoryMode.list,
    val expandedRoutines: Set<UUID> = emptySet(),
    val achievements: List<AchievementRow> = emptyList(),
    val drafts: List<Trip> = emptyList(),
    /** iOS: settings.vehicles, sold ones included: a card names the car of its trip, and the garage section its main one. */
    val vehicles: List<Vehicle> = emptyList(),
    val showcaseElsewhere: List<UUID> = emptyList(),
    val destination: MeDestination? = null,
    val pinLimitReached: Boolean = false,
    val failed: Boolean = false,
)

// iOS: ProfileView.swift. Loading prepares the full library, including historical XP, off the UI thread.
class MeViewModel(
    private val repository: TripRepository,
    private val vehicleStore: VehicleStore,
    private val identitySource: ProfileIdentitySource,
    private val preferences: PresentationPreferences,
    dispatcher: CoroutineDispatcher,
    events: Flow<WorldChange> = emptyFlow(),
    private var locale: Locale = Locale.getDefault(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Instant = Instant::now,
    private val localizedRegion: (String?) -> String? = { it },
    private val catalog: com.onezee.triptrack.logic.CarCatalog? = null,
) : ViewModel(CoroutineScope(SupervisorJob() + dispatcher)) {
    private val mutableState = MutableStateFlow(MeState(mode = preferences.string("profileHistoryMode")?.let { raw -> HistoryMode.entries.firstOrNull { it.name == raw } } ?: HistoryMode.list))
    private val pinEdits = Mutex()
    val state = mutableState.asStateFlow()
    private var library = ProfileHistoryLibrary(emptyList())
    private var journeys = emptyList<Journey>()
    private var vehicles = emptyList<Vehicle>()
    private var loadTask: Job? = null
    private var prepareTask: Job? = null
    init {
        viewModelScope.launch { events.filter { it in setOf(WorldChange.trips, WorldChange.sync, WorldChange.garage) }.collect { reload() } }
        viewModelScope.launch { identitySource.identity.collect { prepareHistory(); refreshHero() } }
        reload()
    }
    fun reload() {
        loadTask?.cancel()
        loadTask = viewModelScope.launch {
            try {
                val trips = repository.fetchAllTrips().sortedByDescending { it.startDate }
                val loadedJourneys = repository.fetchJourneys()
                val loadedVehicles = vehicleStore.vehicles()
                val drafts = repository.fetchDraftTrips()
                val next = ProfileHistoryLibrary(trips, TripLevelHistory.levels(trips))
                val days = trips.groupBy { it.startDate.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant() }
                    .mapValues { (_, trips) -> trips.sumOf { it.distance / 1000 } }
                ensureActive()
                library = next
                journeys = loadedJourneys
                vehicles = loadedVehicles
                mutableState.update { it.copy(loaded = true, allTrips = trips, calendar = HistoryCalendar(days, days.values.maxOrNull() ?: 0.0), drafts = drafts, vehicles = loadedVehicles, failed = false) }
                // Missing local trips may be pins from a second phone. Only known invalid trips are removed.
                val byId = trips.associateBy { it.id }
                val pins = identitySource.identity.value.pinnedTripIds
                val valid = pins.filter { id -> byId[id]?.let { !it.isPrivate && it.endDate != null && it.confirmation == TripConfirmation.confirmed } ?: true }
                if (pins != valid) identitySource.setPinnedTrips(valid)
                prepareHistory()
                refreshHero()
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.update { it.copy(failed = true) } }
        }
    }
    fun search(query: String) { mutableState.update { it.copy(query = query) }; prepareHistory() }
    fun setCalendarRange(from: Instant?, to: Instant?) { mutableState.update { it.copy(from = from, to = to) }; prepareHistory() }
    fun showCalendar(show: Boolean) { mutableState.update { it.copy(showsCalendar = show) } }
    fun setLocale(value: Locale) { locale = value; prepareHistory() }
    fun clearFilters() { mutableState.update { it.copy(query = "", from = null, to = null) }; prepareHistory() }
    fun setMode(mode: HistoryMode) {
        preferences.set("profileHistoryMode", mode.name)
        mutableState.update { it.copy(mode = mode) }
    }
    fun expandRoutine(id: UUID) { mutableState.update { it.copy(expandedRoutines = it.expandedRoutines + id) } }
    fun visibleRoutineTrips(routine: ProfileRoutineTrips): List<Trip> = if (routine.id in state.value.expandedRoutines) routine.trips else routine.trips.take(7)
    fun togglePin(id: UUID) {
        viewModelScope.launch { pinEdits.withLock {
            try {
                val pins = identitySource.identity.value.pinnedTripIds
                if (id in pins) { identitySource.setPinnedTrips(pins - id); return@launch }
                val trip = library.trips.firstOrNull { it.id == id } ?: return@launch
                if (trip.isPrivate || trip.endDate == null || trip.confirmation != TripConfirmation.confirmed) return@launch
                if (pins.size >= 3) { mutableState.update { it.copy(pinLimitReached = true) }; return@launch }
                identitySource.setPinnedTrips(pins + id)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.update { it.copy(failed = true) } }
        } }
    }
    fun dismissPinLimit() { mutableState.update { it.copy(pinLimitReached = false) } }
    fun open(destination: MeDestination) { mutableState.update { it.copy(destination = destination) } }
    fun dismissDestination() { mutableState.update { it.copy(destination = null) } }
    // The scene updates on appearance/foreground only; it never requests location or starts a timer.
    fun appearedOrBecameActive() { refreshHero() }
    private fun prepareHistory() {
        prepareTask?.cancel()
        prepareTask = viewModelScope.launch {
            val snapshot = state.value
            val pins = identitySource.identity.value.pinnedTripIds
            val prepared = PreparedProfileHistory.build(library, journeys, snapshot.query, snapshot.from, snapshot.to,
                locale, zone, pins, localizedRegion = localizedRegion, now = now())
            val regionCounts = prepared.visibleTrips.groupBy { it.startDate.atZone(zone).toLocalDate().withDayOfMonth(1).atStartOfDay(zone).toInstant() }
                .mapValues { (_, trips) -> trips.mapNotNull { it.region?.takeIf(String::isNotEmpty)?.let(ProfileRegionDisplay::canonical) }.toSet().size }
            ensureActive()
            mutableState.update { it.copy(history = prepared.copy(chapters = prepared.chapters.map { chapter -> chapter.copy(regionCount = regionCounts[chapter.id] ?: 0) }), showcaseElsewhere = pins.filterNot { id -> library.trips.any { trip -> trip.id == id } }) }
        }
    }
    private fun refreshHero() {
        val identity = identitySource.identity.value
        val cars = vehicles.filter { it.soldAt == null }.sortedBy { if (it.id == identity.selectedVehicleId) 0 else 1 }
            .map { HeroCarStage(it, identity.mainVehiclePhotos[it.id], VehicleStageState.build(it, identity.mainVehiclePhotos[it.id], identity.isPlus), identity.mainVehiclePhotoUrls[it.id],
                catalog?.resolve(it.catalogModelId, it.catalogGenerationId, it.catalogVariantId)?.powerHp) }
        val badges = BadgeDefinitions.all.filter { !it.isHidden || it.id in identity.unlockedBadgeIds }.map {
            AchievementRow(it.id, it.category, it.rarity, it.id in identity.unlockedBadgeIds, identity.badgeEarnCounts[it.id] ?: 0)
        }
        mutableState.update { it.copy(hero = ProfileHeroState(identity, RoadSceneTime.slot(now(), zone),
            identity.effectiveBackground == ProfileBackground.none && identity.coverPhotoIds.isEmpty(), cars, library.trips.size,
            library.trips.sumOf { trip -> trip.distance }, library.trips.mapNotNull { trip -> trip.region?.takeIf(String::isNotEmpty)?.let(ProfileRegionDisplay::canonical) }.toSet().size), achievements = badges) }
    }
}
