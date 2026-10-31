// iOS: TripTrackApp.swift / ProfileView.swift dependency ownership
package com.onezee.triptrack.feature.me

import com.onezee.triptrack.AppGraph
import com.onezee.triptrack.data.VehiclePhotoStore
import com.onezee.triptrack.feature.garage.*
import com.onezee.triptrack.feature.settings.*
import com.onezee.triptrack.logic.CarCatalog
import com.onezee.triptrack.model.DistanceUnit
import com.onezee.triptrack.model.Vehicle
import java.util.UUID
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// iOS: SettingsManager and ProfileView StateObjects. AppGraph only supplies shared stores.
class MeFeatures(graph: AppGraph, private val preferences: PresentationPreferences, photos: VehiclePhotoStore,
                 badgeLedger: com.onezee.triptrack.location.world.BadgeLedgerStore, files: java.io.File) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val changes = RoomWorldChanges(graph.database).events
        .onEach { if (it == WorldChange.garage) photos.invalidateMainCache() }
        // Recording writes TripEntity for each fix; the journal and badges describe finished trips.
        .filter { it != WorldChange.trips || !graph.location.recorder.state.value.isRecording }
    val catalog = CarCatalog()
    val analytics = ProductAnalytics(preferences, ApiUsageService(graph.apiClient), scope, !com.onezee.triptrack.BuildConfig.DEBUG)
    var roadTimeOverride by mutableStateOf<com.onezee.triptrack.model.RoadSceneTime?>(null)
    var coverPromptHidden by mutableStateOf(preferences.string("profileCoverPromptHidden") == "true")
        private set
    fun hideCoverPrompt() { preferences.set("profileCoverPromptHidden", "true"); coverPromptHidden = true }
    val profilePhotos = ProfilePhotoStore(java.io.File(files, "ProfilePhotos"), ApiProfilePhotoService(graph.apiClient), com.onezee.triptrack.sync.RasterPhotoEncoder(),
        { graph.auth.state.value.signedInAccountId }, scope)
    val identity = ProfileIdentityStore(graph.settings, graph.trips, photos, preferences, graph.auth, graph.apiClient, graph.settingsManager, scope, changes, badgeLedger, ProfileProgressStore(graph.database), profilePhotos)
    init { graph.auth.addMeListener { response ->
        val account = graph.auth.state.value.signedInAccountId
        scope.launch { if (account != null && graph.auth.state.value.signedInAccountId == account) identity.applyRemote(response) }
    } }
    init { scope.launch { graph.auth.state.map { it.signedInAccountId }.distinctUntilChanged().collect { if (it != null) profilePhotos.requestSync() } } }
    @OptIn(FlowPreview::class)
    private val profileUpdates = scope.launch { changes.filter { it == WorldChange.settings || it == WorldChange.trips }.debounce(250).collect { identity.syncJournal() } }
    private val baseProfileUpdates = scope.launch { graph.settingsManager.state.filter { it.loaded }
        .map { Triple(it.selectedVehicleId, it.language, it.showOnPublicMap) }.distinctUntilChanged().drop(1).collect { identity.baseProfileChanged() } }
    private val unit = graph.settingsManager.state.map { it.distanceUnit }.stateIn(scope, SharingStarted.Eagerly, DistanceUnit.km)
    private val selected = graph.settingsManager.state.map { it.selectedVehicleId }.stateIn(scope, SharingStarted.Eagerly, null)
    val profile by lazy { MeViewModel(graph.trips, graph.vehicles, identity, preferences, Dispatchers.Default, changes,
        locale = graph.strings.language.locale, localizedRegion = { ProfileRegionDisplay.localized(it, graph.strings.language.code) }) }
    val garage by lazy { GarageViewModel(graph.vehicles, photos, graph.trips, Dispatchers.Default, selected, changes) }
    val vehiclePhotos = photos
    private val vehicleStore = graph.vehicles
    private val tripRepository = graph.trips
    private val apiClient = graph.apiClient
    private val presentationPreferences = preferences
    fun passport(id: UUID) = VehiclePassportViewModel(id, vehicleStore, vehiclePhotos, tripRepository, catalog, Dispatchers.Default, unit, changes)
    fun edit(vehicle: Vehicle?) = VehicleEditViewModel(vehicle, vehicleStore, unit.value, Dispatchers.Main.immediate)
    fun privacy() = PrivacySettingsViewModel(ApiProfilePrivacyService(apiClient), Dispatchers.Main.immediate)
    fun notificationPreferences() = NotificationPreferencesViewModel(ApiNotificationPreferencesService(apiClient), presentationPreferences, Dispatchers.Main.immediate)
}
