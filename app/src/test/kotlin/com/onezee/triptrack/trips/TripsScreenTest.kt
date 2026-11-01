// iOS: TripTrackUITests' tours on the -seed-map-demo library, here composed under Robolectric.
package com.onezee.triptrack.trips

import com.onezee.triptrack.ui.LocalClock
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalInspectionMode
import com.onezee.triptrack.feature.me.*
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.feature.journey.ui.JourneyComposerSheet
import com.onezee.triptrack.ui.navigation.rememberLocalViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.room.Room
import com.onezee.triptrack.data.DataEnvironment
import com.onezee.triptrack.data.RoomPlaceStore
import com.onezee.triptrack.data.RoomTripRepository
import com.onezee.triptrack.data.RoomVehiclePhotoStore
import com.onezee.triptrack.data.RoomVehicleStore
import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.data.files.DirectoryTripPhotoFiles
import com.onezee.triptrack.data.files.DirectoryVehiclePhotoFiles
import com.onezee.triptrack.debug.TripsDemoSeed
import com.onezee.triptrack.designsystem.navigation.AppTab
import com.onezee.triptrack.designsystem.navigation.CustomTabBar
import com.onezee.triptrack.designsystem.navigation.LocalTabBarVisibility
import com.onezee.triptrack.designsystem.navigation.TabBarVisibility
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.LocalDistanceUnit
import com.onezee.triptrack.designsystem.theme.ThemeManager
import com.onezee.triptrack.designsystem.theme.TripTrackTheme
import com.onezee.triptrack.feature.FeedTab
import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.feature.TripsFeature
import com.onezee.triptrack.feature.asPreviewTrip
import com.onezee.triptrack.feature.drafts.DraftDecision
import com.onezee.triptrack.feature.drafts.DraftDecisionQueue
import com.onezee.triptrack.feature.trip.TestSocialApi
import com.onezee.triptrack.feature.trip.TestSyncSink
import com.onezee.triptrack.feature.trip.TripSummary
import com.onezee.triptrack.feature.tripChanges
import com.onezee.triptrack.location.recording.RecorderState
import com.onezee.triptrack.model.*
import com.onezee.triptrack.model.RoadSceneTime
import com.onezee.triptrack.model.DistanceUnit
import com.onezee.triptrack.model.Journey
import com.onezee.triptrack.model.ReactionTally
import com.onezee.triptrack.model.SocialAuthor
import com.onezee.triptrack.model.SocialFeedTrip
import com.onezee.triptrack.model.SocialFeedTripJourney
import com.onezee.triptrack.model.SocialFeedVehicle
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.model.TripConfirmation
import com.onezee.triptrack.shell.LanguageManager
import com.onezee.triptrack.ui.LocalLiveMaps
import com.onezee.triptrack.ui.LocalTripPhotoImages
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import java.util.TimeZone
import java.util.UUID
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
 * The trip screens on the debug seed's library, at a fixed «now» in a fixed zone: a Pixel 7 in
 * Russian, as the shell shots. Room, the screens' worker and photo decoding all run on the main
 * looper, so one `waitForIdle` settles a screen completely: a shot never catches a half-loaded list.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w411dp-h914dp-420dpi", application = Application::class)
abstract class TripsScreenTest {
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
    internal val meNavigator = MeNavigator()
    protected lateinit var feature: TripsFeature
    protected val owner: UUID = UUID.fromString("6f1c2d3e-4a5b-4c6d-8e7f-001122334455")
    protected val sink = TestSyncSink()
    protected val social = TestSocialApi()
    protected val recorder = MutableStateFlow(RecorderState())
    protected val visibility = TabBarVisibility()

    /** What the drafts screen handed to the recording side's queue, in order. */
    protected val decisions = mutableListOf<Pair<UUID, DraftDecision>>()

    private lateinit var root: File
    private lateinit var savedZone: TimeZone

    @Before fun openWorld() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE))
        val app = RuntimeEnvironment.getApplication()
        root = Files.createTempDirectory("trips-screens").toFile()
        db = Room.inMemoryDatabaseBuilder(app, TripTrackDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCoroutineContext(Dispatchers.Main)
            .build()
        val environment = DataEnvironment(localUserId = { owner }, clock = { NOW })
        val files = DirectoryTripPhotoFiles(root.resolve("trips"))
        repository = RoomTripRepository(db, environment, files, sink)
        val photos = RoomVehiclePhotoStore(db, DirectoryVehiclePhotoFiles(root.resolve("cars")), sink)
        val vehicles = RoomVehicleStore(db, repository, photos, environment, sink)
        val preferences = app.getSharedPreferences("trips-screens", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        val queue = object : DraftDecisionQueue {
            override suspend fun enqueue(id: UUID, decision: DraftDecision) { decisions += id to decision }
            override suspend fun decisionsQueued() {}
        }
        feature = TripsFeature(repository, RoomPlaceStore(db) { NOW }, vehicles, files, queue, recorder, tripChanges(db), preferences, sink,
            isOffline = { false }, worker = Dispatchers.Main, clock = { NOW })
        feature.socialApi = social
        onMain { TripsDemoSeed(db, repository, feature.journeys, NOW, ZoneId.of(ZONE)).run() }
    }

    @After fun closeWorld() {
        // The screens go first: nothing of theirs may run against a closed store.
        compose.activityRule.scenario.moveToState(Lifecycle.State.DESTROYED)
        compose.runOnUiThread {
            feature.feedNavigator.popToRoot()
            meNavigator.stack.popToRoot()
        }
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        feature.close()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
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
                check(++guard < 10_000) { "The main looper never finished the block" }
            }
        }
        return result!!.getOrThrow()
    }

    protected fun signIn() { feature.accountId = { owner } }

    /** The phone as the shell draws it: the theme, the locals the shell provides and, on a tab root, the bar. */
    protected fun show(dark: Boolean = false, tab: AppTab? = null, content: @Composable () -> Unit) {
        compose.setContent {
            TripTrackTheme(isDark = dark, language = AppLanguage.ru, reduceMotion = false) {
                CompositionLocalProvider(
                    LocalTabBarVisibility provides visibility,
                    LocalTripPhotoImages provides feature.photos,
                    LocalDistanceUnit provides DistanceUnit.km,
                    LocalLiveMaps provides false,
                    LocalClock provides java.time.Clock.fixed(NOW, ZoneId.of(ZONE)),
                ) {
                    Box(Modifier.fillMaxSize().background(AppTheme.colors.bg)) {
                        content()
                        if (tab != null && !visibility.isHiddenByScreen) {
                            val drafts by feature.drafts.state.collectAsState()
                            CustomTabBar(selectedTab = tab, onSelect = {}, modifier = Modifier.align(Alignment.BottomCenter), hasUnseenDrafts = drafts.hasUnseen)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    protected fun showFeed(dark: Boolean = false) = show(dark, AppTab.home) {
        FeedTab(feature, scrollToTop = 0, offline = false, onOpenRecord = {}, onOpenProfileTab = {})
    }

    /** The same journal and destination host as the real Me tab, on the deterministic Room library. */
    protected fun showMe(dark: Boolean = false, transform: (MeState) -> MeState = { it }) {
        val library = trips().sortedByDescending { it.startDate }
        val journeys = onMain { repository.fetchJourneys() }
        val drafts = onMain { repository.fetchDraftTrips() }
        val state = transform(MeState(loaded = true, drafts = drafts,
            hero = ProfileHeroState(ProfileIdentity(name = "Дорожный дневник"), RoadSceneTime.day, tripCount = library.size, distanceMetres = library.sumOf { it.distance }),
            history = PreparedProfileHistory.build(ProfileHistoryLibrary(library), journeys, "", null, null, AppLanguage.ru.locale, ZoneId.of(ZONE), now = NOW)))
        show(dark, AppTab.profile) {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                var composer by remember { mutableStateOf<Trip?>(null) }
                MeNavHost(feature, meNavigator, root = {
                    MeContent(state, DistanceUnit.km, {}, {}, {}, {}, {}, {},
                        onDrafts = { meNavigator.push(MeRoute.Drafts) }, onProfile = {}, onLevel = {}, onQuery = {}, onRange = { _, _ -> }, onMode = {}, onClear = {},
                        onTrip = { meNavigator.push(MeRoute.Trip(it.id, it)) }, onJourney = { meNavigator.push(MeRoute.Journey(it.id)) },
                        onPin = {}, onReload = {}, onComposeJourney = { composer = it })
                })
                composer?.let { anchor ->
                    val model = rememberLocalViewModel(anchor.id) { feature.journeyComposer(anchor) }
                    JourneyComposerSheet(model, onCreated = {}, onDismissRequest = { composer = null })
                }
            }
        }
    }

    protected fun push(destination: TripsDestination, navigator: com.onezee.triptrack.ui.navigation.DestinationNavigator<TripsDestination> = meNavigator) {
        compose.runOnUiThread { navigator.push(destination) }
        compose.waitForIdle()
    }

    protected fun openTrip(trip: Trip) = push(TripsDestination.TripDetail(trip.id, preview = TripSummary.from(trip).asPreviewTrip()))

    protected fun trips(): List<Trip> = onMain { repository.fetchAllTrips() }.filter { it.confirmation != TripConfirmation.draft }

    /** The most recent trip: the seed's photos are on it. */
    protected fun latest(): Trip = trips().maxBy { it.startDate }

    /** The road with the two checkpoints and the segment between them. */
    protected fun rostovRoad(): Trip = trips().filter { it.title == TripsDemoSeed.ROSTOV_ROAD }.maxBy { it.startDate }

    protected fun seededJourney(): Journey = onMain { repository.fetchJourneys() }.single()

    protected fun detail(id: UUID): Trip? = onMain { repository.fetchTripDetail(id) }

    /** Three cards of the «Все» feed: two people and one's own published trip, with reactions and a journey. */
    protected fun socialCards(): List<SocialFeedTrip> {
        val library = trips()
        fun preview(title: String) = library.first { it.title == title }.previewPolyline?.let { Base64.getEncoder().encodeToString(it) }
        val own = library.filter { !it.isPrivate }.maxBy { it.startDate }
        return listOf(
            SocialFeedTrip(
                id = UUID.fromString("0b6f6a52-1111-4c8e-9a3e-5d2b7c9e0a01"),
                author = SocialAuthor(UUID.fromString("0b6f6a52-2222-4c8e-9a3e-5d2b7c9e0a01"), displayName = "Аня Соколова", avatarEmoji = "🚙", profileLevel = 12),
                title = "Краснодар → Геленджик", startDate = NOW.minus(Duration.ofHours(3)), endDate = NOW.minus(Duration.ofMinutes(55)),
                distance = 142_300.0, duration = 7_500, maxSpeed = 31.0, drivingTime = 7_100, stoppedTime = 400, region = "Krasnodar Krai",
                isPrivate = false, previewPolyline = preview("Краснодар → Геленджик"), photoCount = 0,
                vehicle = SocialFeedVehicle(name = "Kia Rio", avatarEmoji = "🚗"), reactionCount = 6,
                reactionBreakdown = listOf(ReactionTally("🔥", 4), ReactionTally("🌅", 2)), myReaction = "🔥", badgeIds = emptyList(), commentCountRaw = 3,
            ),
            SocialFeedTrip(
                id = own.id, author = SocialAuthor(owner, displayName = "Саша", avatarEmoji = "🏎️", profileLevel = 7),
                title = own.title, startDate = own.startDate, endDate = own.endDate, distance = own.distance,
                duration = Duration.between(own.startDate, own.endDate ?: own.startDate).seconds.toInt(), region = own.region, isPrivate = false,
                previewPolyline = own.previewPolyline?.let { Base64.getEncoder().encodeToString(it) }, photoCount = 0,
                reactionCount = 1, reactionBreakdown = listOf(ReactionTally("👍", 1)), badgeIds = emptyList(), commentCountRaw = 0,
            ),
            SocialFeedTrip(
                id = UUID.fromString("0b6f6a52-3333-4c8e-9a3e-5d2b7c9e0a01"),
                author = SocialAuthor(UUID.fromString("0b6f6a52-4444-4c8e-9a3e-5d2b7c9e0a01"), displayName = "Михаил", avatarEmoji = "🚐", profileLevel = 21),
                title = "Батуми → Кобулети", startDate = NOW.minus(Duration.ofDays(5)), distance = 24_800.0, duration = 2_400, region = "Adjara",
                isPrivate = false, previewPolyline = preview("Батуми → Кобулети"), photoCount = 0, reactionCount = 0,
                reactionBreakdown = emptyList(), badgeIds = emptyList(), commentCountRaw = 1,
                journey = SocialFeedTripJourney(UUID.fromString("0b6f6a52-5555-4c8e-9a3e-5d2b7c9e0a01"), title = "Грузия",
                    startDate = NOW.minus(Duration.ofDays(8)), endDate = NOW.minus(Duration.ofDays(2))),
            ),
        )
    }

    companion object {
        const val ZONE = "Europe/Moscow"

        /** Wednesday 28 October 2026, 20:30 in Moscow: after every draft the seed writes for «today». */
        val NOW: Instant = Instant.parse("2026-10-28T17:30:00Z")
    }
}
