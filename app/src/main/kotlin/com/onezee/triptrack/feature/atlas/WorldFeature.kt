// iOS: MyMapViewModel.shared, PlacesTabViewModel, PlacesDest, the `.openPlace → .navigateToPlace` and
// `.openDiscovery → .navigateToDiscovery` notifications of ContentView.swift.
package com.onezee.triptrack.feature.atlas

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import com.onezee.triptrack.data.PlaceStore
import com.onezee.triptrack.data.PlaceManager
import com.onezee.triptrack.data.RevealedTileStore
import com.onezee.triptrack.data.TerritoryStore
import com.onezee.triptrack.data.TripRepository
import com.onezee.triptrack.designsystem.navigation.AppTab
import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.feature.TripsFeature
import com.onezee.triptrack.feature.places.PlaceDetailViewModel
import com.onezee.triptrack.feature.places.PlaceHistory
import com.onezee.triptrack.feature.places.PlacesTabViewModel
import com.onezee.triptrack.feature.settings.HomeState
import com.onezee.triptrack.feature.settings.PresentationPreferences
import com.onezee.triptrack.feature.settings.WorldChange
import com.onezee.triptrack.ui.navigation.NavDestination
import com.onezee.triptrack.ui.navigation.TabNavigator
import java.util.UUID
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

/** iOS: PlacesDest. What the Places stack pushes; a trip from a place opens in the trips overlay. */
sealed interface PlacesDestination : NavDestination {
    data class PlaceDetail(val id: UUID) : PlacesDestination {
        override val key: Any get() = "place:$id"
    }
}

/**
 * The Atlas and Places tabs' singletons (iOS: `MyMapViewModel.shared`, the tabs' StateObjects, the
 * PlacesView path), built once by the app graph. The trip screens come from [trips]: a trip opened from
 * a region, a pass or a recent trip slides over the tab in its own stack, as iOS presents it over
 * the Atlas or pushes it onto the Places stack.
 */
class WorldFeature(
    val repository: TripRepository,
    val places: PlaceStore,
    territory: TerritoryStore,
    tiles: RevealedTileStore,
    val revealed: RevealedLayerStore,
    private val presentation: PresentationPreferences,
    val location: AtlasLocationSource,
    home: StateFlow<HomeState>,
    events: Flow<WorldChange>,
    online: StateFlow<Boolean>,
    val trips: TripsFeature,
    /** Sets the home on the settings owner, the one writer of `home.settings`. */
    private val saveHome: (HomeState) -> Unit,
    /** Where the view models and their builds run. Tests pass the main dispatcher. */
    val worker: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
    private val placeManager: PlaceManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + worker)
    private val appViewModels = ViewModelStore()
    private val sharedEvents = events.shareIn(scope, SharingStarted.WhileSubscribed())

    val home: StateFlow<HomeState> = home
    val online: StateFlow<Boolean> = online

    /** The shell's tab switch, set by the app graph: the place card leads into the Places tab. */
    @Volatile var selectTab: (AppTab) -> Unit = {}

    val history = PlaceHistory(placeManager)

    /** Kept for the app's life like iOS `MyMapViewModel.shared`: the Atlas survives tab switches. */
    val atlas: MyMapViewModel by lazy {
        appViewModel("atlas") {
            MyMapViewModel(repository, territory, tiles, places, presentation, location, home, worker, sharedEvents, online)
        }
    }

    /** iOS `@StateObject PlacesTabViewModel` of the tab: one, kept across tab switches. */
    val placesTab: PlacesTabViewModel by lazy {
        appViewModel("places") { PlacesTabViewModel(places, repository, presentation, history, worker, sharedEvents) }
    }

    fun placeDetail(id: UUID): PlaceDetailViewModel = PlaceDetailViewModel(id, places, repository, worker, sharedEvents, placeManager = placeManager)

    val placesNavigator = TabNavigator<PlacesDestination>()

    /** Where the Places panel stands and which pin is chosen; the tab's one panel. */
    val placesPanel = com.onezee.triptrack.feature.places.PlacesPanelModel()

    /** A trip opened from the Atlas: over the map, like iOS `fullScreenCover`. */
    val atlasTrips = TabNavigator<TripsDestination>()

    /** A trip opened from the Places tab or a place: over the stack, like the iOS push of `.trip`. */
    val placesTrips = TabNavigator<TripsDestination>()

    /** iOS ManualHomeSettings → HomeManager.shared: one writer. */
    fun setHome(value: HomeState) = saveHome(value)

    /**
     * iOS `.openPlace → .navigateToPlace`: switch to Places first, then, 300 ms later, put the place on
     * the tab's stack (`path = [.place(id)]`). The delay lets the tab arrive before the push plays.
     */
    fun openPlace(id: UUID) {
        selectTab(AppTab.places)
        scope.launch(Dispatchers.Main.immediate) {
            delay(TWO_PHASE_MILLIS)
            placesTrips.popToRoot()
            placesNavigator.popToRoot()
            placesNavigator.push(PlacesDestination.PlaceDetail(id))
        }
    }

    /**
     * iOS `.openDiscovery → .navigateToDiscovery`: the Atlas tab, then the camera to the seal; the
     * view model waits for the seal if the discoveries are not loaded yet. Discoveries are hidden by
     * their availability flag, so nothing calls this yet but the trip summary of a later wave.
     */
    fun openDiscovery(id: UUID) {
        selectTab(AppTab.maps)
        scope.launch(Dispatchers.Main.immediate) {
            delay(TWO_PHASE_MILLIS)
            atlasTrips.popToRoot()
            atlas.focusDiscovery(id)
        }
    }

    fun close() {
        appViewModels.clear()
        scope.cancel()
    }

    private inline fun <reified VM : ViewModel> appViewModel(key: String, crossinline create: () -> VM): VM {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T = create() as T
        }
        return ViewModelProvider.create(appViewModels, factory)[key, VM::class]
    }

    companion object {
        /** iOS: the 300 ms between the tab switch and the second notification. */
        const val TWO_PHASE_MILLIS = 300L
    }
}

/**
 * iOS: LocationManager.authorizationStatus as the Atlas reads it. The Atlas only ASKS whether location
 * is allowed and never starts GPS: the blue dot is the map's own. Read again when the app returns
 * ([refresh]): the answer changes in the system settings, not here.
 */
class AndroidAtlasLocation(private val context: Context) : AtlasLocationSource {
    private val mutable = MutableStateFlow(read())
    override val permission: StateFlow<AtlasLocationPermission> = mutable.asStateFlow()

    fun refresh() {
        mutable.value = read()
    }

    private fun read(): AtlasLocationPermission {
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        // Not granted reads as denied: onboarding asks first, and once asked Android does not ask
        // again from here; the dialog's way is the system settings either way.
        return if (granted) AtlasLocationPermission.allowed else AtlasLocationPermission.denied
    }
}

/**
 * iOS: CacheManager.networkMonitor, OPTIMISTIC from the start: the first answer arrives a moment
 * after start, and a pessimistic default would flash «offline» on every launch.
 */
fun networkOnline(context: Context, scope: CoroutineScope): StateFlow<Boolean> {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val flow = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(false) }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
            override fun onUnavailable() { trySend(false) }
        }
        try {
            manager.registerDefaultNetworkCallback(callback)
        } catch (_: RuntimeException) {
            trySend(true)
        }
        awaitClose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()
    val state = MutableStateFlow(true)
    scope.launch { flow.collect { state.value = it } }
    return state.asStateFlow()
}
