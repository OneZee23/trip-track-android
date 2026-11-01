// iOS: RecordingStatesTests.swift, TrackingTourTests.swift (deterministic screen states).
package com.onezee.triptrack.recording

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.onezee.triptrack.data.RecordingOrphan
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.location.recording.RecorderState
import com.onezee.triptrack.location.recording.RecoverableRecording
import com.onezee.triptrack.location.world.TripRewardsResult
import com.onezee.triptrack.logic.BadgeDefinitions
import com.onezee.triptrack.logic.LocationFix
import com.onezee.triptrack.model.*
import com.onezee.triptrack.ui.LocalLiveMaps
import java.time.Instant
import java.util.UUID
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

internal object RecordingFixtures {
    val start = Instant.ofEpochSecond(1_760_000_000)
    val now = start.plusSeconds(1392)
    val id: UUID = UUID.fromString("00000000-0000-0000-0000-000000000123")
    val car = Vehicle(id = UUID.fromString("00000000-0000-0000-0000-000000000456"), name = "Honda Civic", avatarEmoji = "pixel_car_blue", odometerKm = 142_500.0)
    val points = listOf(GeoPoint(45.0, 38.0), GeoPoint(45.002, 38.003), GeoPoint(45.004, 38.002), GeoPoint(45.006, 38.006))
        .mapIndexed { i, p -> TrackPoint(latitude = p.latitude, longitude = p.longitude, timestamp = start.plusSeconds(i * 60L), speed = 20.0, horizontalAccuracy = 8.0, altitude = 340.0) }
    val trip = Trip(id = id, startDate = start, endDate = now, distance = 12_400.0, maxSpeed = 100.0 / 3.6,
        averageSpeed = 32.0 / 3.6, vehicleId = car.id, trackPoints = points)
    val recording = TrackingState(recorder = RecorderState(tripId = id, isRecording = true, startDate = start, vehicleId = car.id,
        distance = 12_400.0, speed = 68.0 / 3.6, gpsAccuracy = 8.0, gpsStale = false, checkpointCount = 2,
        location = LocationFix(latitude = 45.006, longitude = 38.006, altitude = 340.0, speed = 68.0 / 3.6, timestamp = now)),
        coordinates = points.map { GeoPoint(it.latitude, it.longitude) }, route = recordingRoute(points))
    val rewards = TripRewardsResult(150, XPBreakdown(base = 12), 2, 3, 190, 340, DriverRank.novice, DriverRank.driver,
        142_487.6, 142_500.0, 2, 2, BadgeDefinitions.all.filter { it.id in setOf("first_trip", "first_km") }, emptyMap(), 3)
    val found = TripDiscoveries(id, 4.2, listOf("region-fixture"), emptyList(), emptyList(), emptyList())
    val recovery = RecoverableRecording(RecordingOrphan(id, start, 12_400.0, 100.0 / 3.6, 32.0 / 3.6, car.id, false,
        TripConfirmation.confirmed, emptyList(), 100, now), 1392.0, false)
}

// iOS: XCTest's isolated app launch. No application services, GL library, tiles or emulator.
@Config(sdk = [35], qualifiers = "ru-w411dp-h914dp-420dpi", application = Application::class)
abstract class RecordingScreenTest {
    protected val compose = createAndroidComposeRule<ComponentActivity>()
    @get:org.junit.Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = RuntimeEnvironment.getApplication()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }).around(compose)

    protected fun show(dark: Boolean = false, unit: DistanceUnit = DistanceUnit.km, content: @Composable () -> Unit) {
        compose.setContent {
            TripTrackTheme(dark, AppLanguage.ru, reduceMotion = true) {
                CompositionLocalProvider(LocalLiveMaps provides false, LocalDistanceUnit provides unit, content = content)
            }
        }
    }
}
