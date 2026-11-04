// iOS: ProfileCardShotTests, GarageProfileShotTests, ProfileMonthlyJournalTests
package com.onezee.triptrack.feature.me

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.AppConfirmDialog
import com.onezee.triptrack.designsystem.components.AppDialogAction
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.garage.*
import com.onezee.triptrack.feature.settings.*
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.onezee.triptrack.ui.LocalAppVersion

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w411dp-h914dp-420dpi")
class MeScreenshotTests(private val dark: Boolean) {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = RuntimeEnvironment.getApplication()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }).around(compose)

    private fun shot(name: String, before: () -> Unit = {}, content: @Composable () -> Unit) {
        compose.setContent {
            TripTrackTheme(isDark = dark, language = AppLanguage.ru, reduceMotion = true) {
                // The version the baselines were recorded with: a release bump must not repaint them. The clock stands on
                // the last day of the seeded month, so «Эта неделя» and the relative dates of the cards are the same on every run.
                CompositionLocalProvider(LocalInspectionMode provides true, LocalAppVersion provides "0.9.2-dev",
                    com.onezee.triptrack.ui.LocalClock provides java.time.Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC)) {
                    Box(Modifier.fillMaxSize().background(AppTheme.colors.bg)) { content() }
                }
            }
        }
        compose.waitForIdle()
        before()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/me_${name}_${if (dark) "dark" else "light"}.png")
    }

    @Test fun heroDawn() = hero(RoadSceneTime.dawn)
    @Test fun heroDay() = hero(RoadSceneTime.day)
    @Test fun heroSunset() = hero(RoadSceneTime.sunset)
    @Test fun heroNight() = hero(RoadSceneTime.night)
    @Test fun journeyPrompts() = shot("journey_prompts") {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeQuestionCard("Краснодар", {}, {})
            JourneySuggestionBanner("Владикавказ · 6 поездок", {}, {})
        }
    }
    @Test fun bluetoothOff() = shot("bluetooth_off") {
        com.onezee.triptrack.feature.settings.StereoPickerContent(
            vehicles = null, selected = null, onSelect = {}, allowed = true, onAllow = {}, bluetoothEnabled = false,
            outputs = emptyList(), nearby = emptyList(), isLinked = { false }, onLink = {}, onRefresh = {},
            onOpenSettings = {}, onBack = {},
        )
    }
    @Test fun adminSettingsCard() = shot("admin_settings") {
        com.onezee.triptrack.feature.settings.SettingsContent(
            SettingsState(loaded = true), ThemeMode.dark, AppLanguage.ru, true,
            NotificationPreferencesState(isAdmin = true, loaded = true), {}, {}, {}, {}, {}, isAdmin = true,
            onAdmin = {}, adminNewAccounts = true, onAdminNewAccounts = {},
        )
    }
    @Test fun adminSettingsHint() = shot("admin_settings_hint", before = {
        compose.onNodeWithTag("settings_hint").performClick()
    }) {
        com.onezee.triptrack.feature.settings.SettingsContent(
            SettingsState(loaded = true), ThemeMode.dark, AppLanguage.ru, true,
            NotificationPreferencesState(isAdmin = true, loaded = true), {}, {}, {}, {}, {}, isAdmin = true,
            onAdmin = {}, adminNewAccounts = true, onAdminNewAccounts = {},
        )
    }
    @Test fun vehiclePhotoVisibilityQuestion() = shot("vehicle_photo_visibility_question") {
        AppConfirmDialog(appStrings.vehiclePhotoAskTitle(), appStrings.vehiclePhotoAskBody(),
            listOf(AppDialogAction(appStrings.vehiclePhotoAskShow()) {}), {}, appStrings.vehiclePhotoAskKeep())
    }
    @Test fun badgePinConfirmsAndCanBeUndone() {
        val pinned = mutableStateOf(false)
        compose.setContent {
            TripTrackTheme(isDark = dark, language = AppLanguage.ru, reduceMotion = true) {
                BadgeDetailScreen(
                    AchievementRow("first_trip", BadgeCategory.distance, BadgeRarity.common, unlocked = true, earnCount = 1),
                    onBack = {}, pinned = pinned.value, onPin = { pinned.value = true }, onUnpin = { pinned.value = false },
                )
            }
        }
        compose.onNodeWithTag("achievement_pin").performClick()
        compose.onNodeWithTag("achievement_pin_confirm").assertExists().performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertTrue(pinned.value)
        compose.onNodeWithTag("achievement_pin").performClick()
        assertFalse(pinned.value)
    }
    @Test fun trackingMapLoading() = shot("tracking_map_loading") {
        com.onezee.triptrack.recording.TrackingScreen(
            com.onezee.triptrack.recording.TrackingState(), null, false, com.onezee.triptrack.recording.TrackingActions(),
            Instant.parse("2026-10-31T12:00:00Z"), mapReady = false,
        )
    }
    @Test fun onboardingUsesLatestTrip() = shot("onboarding_own_trip") {
        val example = Trip(distance = 92_400.0, averageSpeed = 19.0,
            previewPolyline = Trip.encodePolyline(listOf(GeoPoint(55.7, 37.5), GeoPoint(55.9, 37.7), GeoPoint(56.0, 38.0))))
        com.onezee.triptrack.feature.onboarding.OnboardingContent(
            page = 1, distanceUnit = DistanceUnit.km, isRequesting = false, locationGranted = false,
            backgroundGranted = false, notificationsGranted = false, onContinue = {}, exampleTrip = example,
        )
    }
    private fun hero(time: RoadSceneTime) = shot("hero_${time.name}") {
        ProfileHeroCard(MeFixtures.hero.copy(roadTime = time), DistanceUnit.km, {}, {}, {})
    }
    @Test fun heroNoVehicle() = shot("hero_no_vehicle") {
        ProfileHeroCard(MeFixtures.hero.copy(cars = emptyList(), roadTime = RoadSceneTime.sunset), DistanceUnit.km, {}, {}, {})
    }
    @Test fun heroChosenBackground() = shot("hero_chosen") {
        ProfileHeroCard(MeFixtures.hero.copy(identity = MeFixtures.hero.identity.copy(effectiveBackground = ProfileBackground.ocean)), DistanceUnit.km, {}, {}, {})
    }
    @Test fun heroWithCoverPhotos() = shot("hero_cover") {
        // 47: the status bar of the iPhone the shots of the tour were taken on; the renders have no bar of their own.
        ProfileHeroCard(MeFixtures.hero, DistanceUnit.km, {}, {}, {}, coverPhotos = MeFixtures.cover, safeTop = 47.dp)
    }
    @Test fun heroAtTheLargestText() {
        val density = androidx.compose.ui.unit.Density(RuntimeEnvironment.getApplication().resources.displayMetrics.density, fontScale = 2f)
        shot("hero_large") { CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides density) { ProfileHeroCard(MeFixtures.hero, DistanceUnit.km, {}, {}, {}) } }
    }
    @Test fun page() = shot("page") { MePage(MeFixtures.page) }
    @Test fun pageWithTheSectionsOpen() = shot("page_sections", before = {
        compose.onNodeWithTag("screen_me").performScrollToNode(hasTestTag("profile_sections_toggle"))
        compose.onNodeWithTag("profile_sections_toggle").performClick()
        compose.waitForIdle()
    }) { MePage(MeFixtures.page) }
    @Test fun pageOnTheFirstRun() = shot("page_first_run") { MePage(MeFixtures.firstRun) }
    @Test fun pageWithCoverPhotos() = shot("page_cover") { MePage(MeFixtures.page, covers = MeFixtures.cover) }
    // The signed-in page's items: the hero, the sections, the drafts, then the history header (3), its calendar (4) and the cards (5).
    @Test fun pageInTheGrid() = shot("page_grid", before = { compose.onNodeWithTag("screen_me").performScrollToIndex(3) }) {
        MePage(MeFixtures.page.copy(mode = HistoryMode.grid), signedIn = true)
    }
    @Test fun pageHistoryHeader() = shot("page_history", before = { compose.onNodeWithTag("screen_me").performScrollToIndex(3) }) { MePage(MeFixtures.page, signedIn = true) }
    @Test fun pageHistoryCards() = shot("page_cards", before = { compose.onNodeWithTag("screen_me").performScrollToIndex(5) }) { MePage(MeFixtures.page, signedIn = true) }
    @Test fun journal() = shot("journal") {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ProfileHistoryCalendar(MeFixtures.calendar, null, null, MeFixtures.trips.size, { _, _ -> }, LocalDate.of(2026, 10, 31)) }
            profileJournal(MeFixtures.profile, DistanceUnit.km, {}, {}, {})
        }
    }
    @Test fun garage() = shot("garage") {
        GarageScreen(MeFixtures.garage, DistanceUnit.km, {}, {}, {}, {}, {})
    }
    @Test fun garageEmpty() = shot("garage_empty") {
        GarageScreen(GarageState(loaded = true), DistanceUnit.km, {}, {}, {}, {}, {})
    }
    @Test fun vehiclePhotosEmpty() = shot("vehicle_photos_empty") {
        Column { VehiclePhotosEmpty(busy = false) {} }
    }
    @Test fun clubs() = shot("clubs") {
        ClubsContent(com.onezee.triptrack.network.GroupsWaitlistState(total = 128, clubs = emptyMap(), joined = false, joinedClubs = emptyList()), busy = false, failed = false, onBack = {}, onToggle = {})
    }
    @Test fun passport() = shot("passport") {
        VehiclePassportScreen(MeFixtures.passport, DistanceUnit.km, {}, {}, {}, {})
    }
    @Test fun settings() = shot("settings") {
        SettingsContent(SettingsState(loaded = true), if (dark) ThemeMode.dark else ThemeMode.light, AppLanguage.ru, true,
            NotificationPreferencesState(loaded = true), {}, {}, {}, {}, {}, account = {
                SettingsAccountRow(cloudSyncEnabled = true, state = com.onezee.triptrack.feature.account.SyncState.Off, onClick = {})
            })
    }
    @Test fun achievements() = shot("achievements") {
        AchievementsScreen(MeFixtures.achievements, {}, {})
    }
    @Test fun levels() = shot("levels") {
        LevelsScreen(MeFixtures.hero.identity.copy(level = 21, xp = 26400), {})
    }
    @Test fun country() = shot("country") { CountryScreen("RU", {}, {}) }
    @Test fun nameEditor() = shot("name_editor") { ProfileTextEditor(ProfileTextField.displayName, "Путешественник", {}, {}) }
    @Test fun bioEditor() = shot("bio_editor") { ProfileTextEditor(ProfileTextField.bio, MeFixtures.hero.identity.bio, {}, {}) }
    /** The real Я content on a fixture, every callback a no-op. */
    @Composable private fun MePage(state: MeState, signedIn: Boolean = false, covers: List<ProfileCoverPhoto> = emptyList()) {
        MeContent(state, DistanceUnit.km, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {},
            account = { com.onezee.triptrack.feature.account.GuestSignInCard(expired = false, isAuthenticating = false, onSignIn = { _, _, _ -> }) },
            coverPhotos = covers, signedIn = signedIn, safeTop = if (covers.isEmpty()) 0.dp else 47.dp)
    }
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}

// iOS: -seed-garage-demo, -seed-profile-journal. Fixed ids and dates keep every render deterministic.
internal object MeFixtures {
    val car = Vehicle(id = UUID.fromString("10000000-0000-4000-8000-000000000001"), name = "Polo", avatarEmoji = "pixel_car_blue", make = "Volkswagen", model = "Polo", year = 2019,
        odometerKm = 2160.0, manualOdometerKm = 96400.0, level = 4, dashboardUnits = DashboardUnits.metric, createdAt = Instant.parse("2024-05-01T12:00:00Z"))
    val second = car.copy(id = UUID.fromString("10000000-0000-4000-8000-000000000002"), name = "Jolion", avatarEmoji = "pixel_car_white", avatarStyle = "crossover", make = "Haval", model = "Jolion", year = 2023)
    val sold = car.copy(id = UUID.fromString("10000000-0000-4000-8000-000000000003"), name = "Golf", model = "Golf", soldAt = Instant.parse("2026-03-01T12:00:00Z"))
    private val route = listOf(GeoPoint(45.0, 39.0), GeoPoint(45.01, 39.015), GeoPoint(45.017, 39.01), GeoPoint(45.03, 39.04))
    val trips = (10 downTo 1).map { day -> Trip(id = UUID.nameUUIDFromBytes("me_trip_$day".toByteArray()), startDate = Instant.parse("2026-10-${day.toString().padStart(2, '0')}T12:00:00Z"),
        endDate = Instant.parse("2026-10-${day.toString().padStart(2, '0')}T12:10:00Z"), title = if (day == 10) "К морю" else "Город", distance = if (day == 10) 214000.0 else 5000.0,
        previewPolyline = Trip.encodePolyline(route), tripDescription = if (day == 10) "Дорога" else null, isPrivate = false, vehicleId = car.id, region = "RU-KDA") }
    val calendar = HistoryCalendar(trips.associate { it.startDate.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant() to it.distance / 1000 }, 214.0)
    // The state of the iPhone tour (ProfileCardShotTests): «Вы», LVL 8, the Polo with its catalogue power, 58 trips.
    val heroCar = car.copy(name = "Полторашка", year = 2018)
    val hero = ProfileHeroState(ProfileIdentity(name = "Вы", avatarEmoji = "😎", level = 8, bio = "Езжу за новыми историями и красивыми дорогами"), RoadSceneTime.day, true,
        listOf(HeroCarStage(heroCar, null, VehicleStageState.build(heroCar, null, false), powerHp = 110.0)), 58, 2155000.0, 3)
    val profile = MeState(loaded = true, hero = hero, calendar = calendar,
        history = PreparedProfileHistory.build(ProfileHistoryLibrary(trips), emptyList(), "", null, null, AppLanguage.ru.locale, ZoneOffset.UTC, now = Instant.parse("2026-10-31T12:00:00Z")))
    val garage = GarageState(loaded = true, vehicles = listOf(car, second, sold).map { GarageVehicle(it, 12, null) }, selectedVehicleId = car.id,
        active = GarageVehicle(car, 12, null), others = listOf(GarageVehicle(second, 3, null)), sold = listOf(GarageVehicle(sold, 42, null)))
    val passport = VehiclePassportState(car, VehiclePassport(tripCount = 12, regionCount = 7, daysOnRoad = 9, recent = trips.take(3), years = listOf(VehicleYearDistance(2026, 2160.0))), dashboardUnit = DistanceUnit.km, loaded = true)
    val achievements = BadgeDefinitions.all.filterNot { it.isHidden }.mapIndexed { index, badge -> AchievementRow(badge.id, badge.category, badge.rarity, index < 8, if (index < 8) 1 else 0) }
    private val drafts = (0 until 4).map { Trip(id = UUID.nameUUIDFromBytes("me_draft_$it".toByteArray()), startDate = Instant.parse("2026-10-30T15:0$it:00Z"), confirmation = TripConfirmation.draft) }
    /** The whole page: the garage with its three cars, four drafts, the achievements of the fixture, the library of ten trips. */
    val page = profile.copy(vehicles = listOf(heroCar, second, sold), drafts = drafts, achievements = achievements,
        hero = hero.copy(identity = hero.identity.copy(selectedVehicleId = heroCar.id)))
    /** A library with nothing in it yet: the hero without numbers, the first-trip card, the garage and the clubs. */
    val firstRun = MeState(loaded = true, hero = hero.copy(tripCount = 0, distanceMetres = 0.0, regionCount = 0), vehicles = listOf(heroCar))
    /** Four cover photos that are not on the disk: the pager draws its grounds, the counter and the gear. */
    val cover = (1..4).map { ProfileCoverPhoto(UUID.nameUUIDFromBytes("me_cover_$it".toByteArray())) }
}
