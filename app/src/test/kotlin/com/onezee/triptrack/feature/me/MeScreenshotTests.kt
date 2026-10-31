// iOS: ProfileCardShotTests, GarageProfileShotTests, ProfileMonthlyJournalTests
package com.onezee.triptrack.feature.me

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.AppGlyph
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
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            TripTrackTheme(isDark = dark, language = AppLanguage.ru, reduceMotion = true) {
                CompositionLocalProvider(LocalInspectionMode provides true) { Box(Modifier.fillMaxSize().background(AppTheme.colors.bg)) { content() } }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/me_${name}_${if (dark) "dark" else "light"}.png")
    }

    @Test fun heroDawn() = hero(RoadSceneTime.dawn)
    @Test fun heroDay() = hero(RoadSceneTime.day)
    @Test fun heroSunset() = hero(RoadSceneTime.sunset)
    @Test fun heroNight() = hero(RoadSceneTime.night)
    private fun hero(time: RoadSceneTime) = shot("hero_${time.name}") {
        Column(Modifier.padding(16.dp)) { ProfileHeroCard(MeFixtures.hero.copy(roadTime = time), DistanceUnit.km, {}, {}, {}) }
    }
    @Test fun journal() = shot("journal") {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ProfileHistoryCalendar(MeFixtures.calendar, null, null, MeFixtures.trips.size, { _, _ -> }, LocalDate.of(2026, 10, 31)) }
            profileJournal(MeFixtures.profile, DistanceUnit.km, {}, {}, {})
        }
    }
    @Test fun garage() = shot("garage") {
        GarageScreen(MeFixtures.garage, DistanceUnit.km, {}, {}, {}, {}, {})
    }
    @Test fun passport() = shot("passport") {
        VehiclePassportScreen(MeFixtures.passport, DistanceUnit.km, {}, {}, {}, {})
    }
    @Test fun settings() = shot("settings") {
        SettingsContent(SettingsState(loaded = true), if (dark) ThemeMode.dark else ThemeMode.light, AppLanguage.ru, true,
            NotificationPreferencesState(loaded = true), {}, {}, {}, {}, {}, account = {
                com.onezee.triptrack.feature.account.AccountSettingsRow(icon = com.onezee.triptrack.feature.account.AccountGlyph.cloudFill, iconColor = AppTheme.accent, title = appStrings.accountSyncTitle(), onClick = {}) {
                    Glyph(AppGlyph.chevronRight, null, tint = AppTheme.colors.textTertiary, size = 18.dp)
                }
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
    val hero = ProfileHeroState(ProfileIdentity(name = "Дорожный дневник", avatarEmoji = "😎", level = 12, bio = "За новыми историями и красивыми дорогами"), RoadSceneTime.day, true,
        listOf(HeroCarStage(car, null, VehicleStageState.build(car, null, false))), trips.size, 1697000.0, 7)
    val profile = MeState(loaded = true, hero = hero, calendar = calendar,
        history = PreparedProfileHistory.build(ProfileHistoryLibrary(trips), emptyList(), "", null, null, AppLanguage.ru.locale, ZoneOffset.UTC, now = Instant.parse("2026-10-31T12:00:00Z")))
    val garage = GarageState(loaded = true, vehicles = listOf(car, second, sold).map { GarageVehicle(it, 12, null) }, selectedVehicleId = car.id,
        active = GarageVehicle(car, 12, null), others = listOf(GarageVehicle(second, 3, null)), sold = listOf(GarageVehicle(sold, 42, null)))
    val passport = VehiclePassportState(car, VehiclePassport(tripCount = 12, regionCount = 7, daysOnRoad = 9, recent = trips.take(3), years = listOf(VehicleYearDistance(2026, 2160.0))), dashboardUnit = DistanceUnit.km, loaded = true)
    val achievements = BadgeDefinitions.all.filterNot { it.isHidden }.mapIndexed { index, badge -> AchievementRow(badge.id, badge.category, badge.rarity, index < 8, if (index < 8) 1 else 0) }
}
