// iOS: TripTrackUITests' Atlas and Places tours on -seed-map-demo -seed-places-rich, composed here
// under Robolectric.
package com.onezee.triptrack.world

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.room.Room
import com.onezee.triptrack.data.DataEnvironment
import com.onezee.triptrack.data.DataFlags
import com.onezee.triptrack.data.RoomPlaceStore
import com.onezee.triptrack.data.RoomRevealedTileStore
import com.onezee.triptrack.data.RoomTerritoryStore
import com.onezee.triptrack.data.RoomTripRepository
import com.onezee.triptrack.data.RoomVehiclePhotoStore
import com.onezee.triptrack.data.RoomVehicleStore
import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.data.files.DirectoryTripPhotoFiles
import com.onezee.triptrack.data.files.DirectoryVehiclePhotoFiles
import com.onezee.triptrack.debug.PlacesDemoSeed
import com.onezee.triptrack.debug.TripsDemoSeed
import com.onezee.triptrack.designsystem.navigation.AppTab
import com.onezee.triptrack.designsystem.navigation.CustomTabBar
import com.onezee.triptrack.designsystem.navigation.LocalTabBarVisibility
import com.onezee.triptrack.designsystem.navigation.TabBarVisibility
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.LocalDistanceUnit
import com.onezee.triptrack.designsystem.theme.TripTrackTheme
import com.onezee.triptrack.feature.TripsFeature
import com.onezee.triptrack.feature.atlas.AtlasLocationPermission
import com.onezee.triptrack.feature.atlas.AtlasLocationSource
import com.onezee.triptrack.feature.atlas.RevealedLayerStore
import com.onezee.triptrack.feature.atlas.WorldFeature
import com.onezee.triptrack.feature.atlas.ui.AtlasTab
import com.onezee.triptrack.feature.drafts.DraftDecision
import com.onezee.triptrack.feature.drafts.DraftDecisionQueue
import com.onezee.triptrack.feature.places.ui.PlacesTab
import com.onezee.triptrack.feature.settings.HomeState
import com.onezee.triptrack.feature.MemoryPreferences
import com.onezee.triptrack.feature.settings.RoomWorldChanges
import com.onezee.triptrack.feature.trip.TestSocialApi
import com.onezee.triptrack.feature.trip.TestSyncSink
import com.onezee.triptrack.feature.tripChanges
import com.onezee.triptrack.location.recording.RecorderState
import com.onezee.triptrack.logic.RegionAtlas
import com.onezee.triptrack.model.DistanceUnit
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.ui.LocalLiveMaps
import com.onezee.triptrack.ui.LocalTripPhotoImages
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Atlas and Places tabs on the debug seeds' library (trips, the rich road, ten places, the
 * opened world), at a fixed «now» in a fixed zone: a Pixel 7 in Russian, like the trip screens'
 * shots. Room, the screens' worker and the reveal build all run on the main looper, so one
 * `waitForIdle` settles a screen. The map itself is the stand-in ground: MapLibre's native library
 * does not load under Robolectric.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w411dp-h914dp-420dpi", application = Application::class)
abstract class WorldScreenTest {
    protected val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = RuntimeEnvironment.getApplication()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }).around(compose)

    protected lateinit var db: TripTrackDatabase
    protected lateinit var repository: RoomTripRepository
    protected lateinit var trips: TripsFeature
    protected lateinit var world: WorldFeature
    protected lateinit var revealed: RevealedLayerStore
    protected val owner: UUID = UUID.fromString("6f1c2d3e-4a5b-4c6d-8e7f-001122334455")
    protected val visibility = TabBarVisibility()
    protected val permission = MutableStateFlow(AtlasLocationPermission.allowed)
    protected val online = MutableStateFlow(true)
    protected val home = MutableStateFlow(HomeState())
    protected var selectedTab: AppTab? = null

    private lateinit var root: File
    private lateinit var savedZone: TimeZone

    /** Which seeds run: the trips always, the places and their rich road unless a test asks for fewer. */
    protected open val seedsPlaces: Boolean = true
    protected open val seedsTrips: Boolean = true

    /** What the world reads trips from; a loading test holds the map's read behind a gate. */
    protected open fun worldRepository(repository: RoomTripRepository): com.onezee.triptrack.data.TripRepository = repository

    private val placeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Before fun openWorld() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE))
        val app = RuntimeEnvironment.getApplication()
        root = Files.createTempDirectory("world-screens").toFile()
        db = Room.inMemoryDatabaseBuilder(app, TripTrackDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCoroutineContext(Dispatchers.Main)
            .build()
        val sink = TestSyncSink()
        val environment = DataEnvironment(localUserId = { owner }, clock = { NOW })
        val files = DirectoryTripPhotoFiles(root.resolve("trips"))
        repository = RoomTripRepository(db, environment, files, sink)
        val vehicles = RoomVehicleStore(db, repository, RoomVehiclePhotoStore(db, DirectoryVehiclePhotoFiles(root.resolve("cars")), sink), environment, sink)
        val preferences = app.getSharedPreferences("world-screens", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        val queue = object : DraftDecisionQueue {
            override suspend fun enqueue(id: UUID, decision: DraftDecision) {}
            override suspend fun decisionsQueued() {}
        }
        val places = RoomPlaceStore(db) { NOW }
        trips = TripsFeature(repository, places, vehicles, files, queue, MutableStateFlow(RecorderState()), tripChanges(db), preferences, sink,
            isOffline = { false }, worker = Dispatchers.Main, clock = { NOW })
        trips.socialApi = TestSocialApi()
        revealed = RevealedLayerStore(RoomRevealedTileStore(db), DataFlags.InMemory(), preferences, Dispatchers.Main)
        world = WorldFeature(
            repository = worldRepository(repository), places = places, territory = RoomTerritoryStore(db), tiles = RoomRevealedTileStore(db),
            revealed = revealed, presentation = MemoryPreferences(), location = object : AtlasLocationSource { override val permission = this@WorldScreenTest.permission },
            home = home, events = RoomWorldChanges(db).events, online = online, trips = trips, saveHome = { home.value = it },
            worker = Dispatchers.Main,
            placeManager = com.onezee.triptrack.data.PlaceManager(repository, places,
                com.onezee.triptrack.data.PreferencesPendingPlaceHistory(preferences), placeScope,
                io = Dispatchers.Main, geometry = Dispatchers.Main),
        ).also { feature -> feature.selectTab = { selectedTab = it } }
        // The bundled atlas once, before any clock: the regions are what the sheet lists.
        RegionAtlas.shared
        onMain {
            if (seedsTrips) TripsDemoSeed(db, repository, trips.journeys, NOW, ZoneId.of(ZONE)).run()
            if (seedsPlaces) PlacesDemoSeed(db, places, world.history, revealed, NOW).run()
            else revealed.rebuildIfNeeded()
        }
    }

    @After fun closeWorld() {
        compose.activityRule.scenario.moveToState(Lifecycle.State.DESTROYED)
        compose.runOnUiThread {
            world.placesNavigator.popToRoot()
            world.placesTrips.popToRoot()
            world.atlasTrips.popToRoot()
        }
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        world.close()
        trips.close()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        placeScope.cancel()
        db.close()
        root.deleteRecursively()
        TimeZone.setDefault(savedZone)
    }

    /** Runs [block] on the main looper and works the looper until it is done. */
    protected fun <T> onMain(block: suspend () -> T): T {
        var result: Result<T>? = null
        CoroutineScope(Dispatchers.Main).launch { result = runCatching { block() } }
        val looper = Shadows.shadowOf(Looper.getMainLooper())
        var guard = 0
        while (result == null) {
            looper.idle()
            if (result == null) {
                looper.idleFor(Duration.ofMillis(10))
                check(++guard < 20_000) { "The main looper never finished the block" }
            }
        }
        return result!!.getOrThrow()
    }

    /** Lets the debounces (places 300 ms, reload 250 ms) and the springs run out. */
    protected fun settle(millis: Long = 1_500) {
        compose.mainClock.advanceTimeBy(millis)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
        compose.waitForIdle()
    }

    protected fun show(dark: Boolean, tab: AppTab, settles: Boolean = true, content: @Composable () -> Unit) {
        compose.setContent {
            TripTrackTheme(isDark = dark, language = AppLanguage.ru, reduceMotion = true) {
                CompositionLocalProvider(
                    LocalTabBarVisibility provides visibility,
                    LocalTripPhotoImages provides trips.photos,
                    LocalDistanceUnit provides DistanceUnit.km,
                    LocalLiveMaps provides false,
                ) {
                    Box(Modifier.fillMaxSize().background(AppTheme.colors.bg)) {
                        content()
                        if (!visibility.isHiddenByScreen) CustomTabBar(selectedTab = tab, onSelect = {}, modifier = Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
        }
        compose.waitForIdle()
        if (settles) settle()
    }

    protected fun showAtlas(dark: Boolean = false, settles: Boolean = true) = show(dark, AppTab.maps, settles) { AtlasTab(world, resumeTick = 0) }

    protected fun showPlaces(dark: Boolean = false, settles: Boolean = true) = show(dark, AppTab.places, settles) { PlacesTab(world) }

    protected fun krasnodar() = GeoPoint(45.035, 38.975)

    companion object {
        const val ZONE = "Europe/Moscow"

        /** Wednesday 28 October 2026, 20:30 in Moscow, the trip screens' «now». */
        val NOW: Instant = Instant.parse("2026-10-28T17:30:00Z")
    }
}
