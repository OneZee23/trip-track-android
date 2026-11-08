// iOS: RecordingStatesTests.swift, TrackingTourTests.swift, RecordingFinishFlowTests.swift.
package com.onezee.triptrack.recording

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.onezee.triptrack.location.recording.StartRefusal
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.location.recording.RecorderState
import com.onezee.triptrack.model.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordingScreenshotTests(private val dark: Boolean) : RecordingScreenTest() {
    private val fixture = RecordingFixtures
    private val theme get() = if (dark) "dark" else "light"
    private fun shot(name: String) { compose.waitForIdle(); compose.onRoot().captureRoboImage("src/test/screenshots/recording_${name}_$theme.png") }
    private fun screen(state: TrackingState, denied: Boolean = false, discarded: String? = null, unit: DistanceUnit = DistanceUnit.km, restricted: Boolean = false) {
        show(dark, unit) { TrackingScreen(state, fixture.car, denied, TrackingActions(), fixture.now, discarded = discarded, backgroundGpsRestricted = restricted) }
    }

    @Test fun idleNoGPS() { screen(TrackingState()); shot("idle_no_gps") }
    @Test fun idleGoodGPS() { screen(TrackingState(recorder = RecorderState(gpsAccuracy = 8.0), totalMetres = 142_500_000.0, tripCount = 42)); shot("idle_good_gps") }
    /** The owner's POCO, 7 Nov 2026: background-restricted, GPS gone a minute after the screen locked. */
    @Test fun idleBackgroundRestricted() {
        screen(TrackingState(recorder = RecorderState(gpsAccuracy = 8.0), totalMetres = 142_500_000.0, tripCount = 42), restricted = true)
        shot("idle_gps_restricted")
    }
    @Test fun gpsLostWhileRecording() { screen(fixture.recording.copy(recorder = fixture.recording.recorder.copy(gpsLost = true))); shot("gps_lost") }
    @Test fun locationDenied() { screen(TrackingState(), denied = true); shot("location_denied") }
    @Test fun recording() { screen(fixture.recording); shot("running") }
    // iOS: RecordingStatesTests.test_recording_over_a_light_map.
    @Test fun recordingOverLightMap() {
        show(dark) { TrackingScreen(fixture.recording, fixture.car, false, TrackingActions(), fixture.now,
            map = { TrackingMapPlaceholder(fixture.recording.coordinates, Modifier.fillMaxSize(), Color(0xFFE9E7DF)) }) }
        shot("running_day_map")
    }
    @Test fun idleOverLightMap() {
        show(dark) { TrackingScreen(TrackingState(recorder = RecorderState(gpsAccuracy = 8.0)), fixture.car, false, TrackingActions(), fixture.now,
            map = { TrackingMapPlaceholder(emptyList(), Modifier.fillMaxSize(), Color(0xFFE9E7DF)) }) }
        shot("idle_day_map")
    }
    @Test fun paused() { screen(fixture.recording.copy(recorder = fixture.recording.recorder.copy(isPaused = true, pauseStartedAt = fixture.now, gpsStale = true))); shot("paused") }
    @Test fun draft() { screen(fixture.recording.copy(recorder = fixture.recording.recorder.copy(confirmation = TripConfirmation.draft))); shot("draft") }
    @Test fun finishing() { screen(TrackingState(recorder = RecorderState(isFinishing = true))); shot("finishing") }
    @Test fun miles() { screen(fixture.recording, unit = DistanceUnit.miles); shot("miles") }
    @Test fun shortTripDiscard() {
        show(dark) { Box(Modifier.fillMaxSize()) {
            TrackingScreen(fixture.recording, fixture.car, false, TrackingActions(), fixture.now)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .5f)))
            Box(Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                StopConfirmationContent(RecordingFinishPreview(fixture.id, fixture.start.plusSeconds(2), 0.0, 2.0, 0.0), false, false, {}, {}, {})
            }
        } }
        shot("short_discard")
    }
    @Test fun stopSave() {
        show(dark) { Box(Modifier.fillMaxSize()) {
            TrackingScreen(fixture.recording, fixture.car, false, TrackingActions(), fixture.now)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .5f)))
            Box(Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                StopConfirmationContent(RecordingFinishPreview(fixture.id, fixture.now, 12_400.0, 1392.0, 100.0 / 3.6), false, false, {}, {}, {})
            }
        } }
        shot("stop_save")
    }
    @Test fun discardedIdle() {
        show(dark) { TrackingScreen(TrackingState(), fixture.car, false, TrackingActions(), fixture.now, discarded = appStrings.junkTripDiscarded()) }
        shot("discarded_idle")
    }
    @Test fun summary() {
        show(dark) { TripCompleteSummary(fixture.trip, fixture.rewards, fixture.found, { _, _ -> }, {}, {}, {}, {}) }
        shot("summary")
    }
    // RC-16: the signal banners, which no earlier render showed.
    @Test fun signalLost() { screen(fixture.recording.copy(recorder = fixture.recording.recorder.copy(gpsStale = true))); shot("signal_lost") }
    @Test fun signalWeak() { screen(fixture.recording.copy(recorder = fixture.recording.recorder.copy(gpsAccuracy = 60.0))); shot("signal_weak") }

    // RC-20: the refusal with the way past it.
    @Test fun startRefused() { screen(TrackingState(refusal = StartRefusal.noFix)); shot("start_refused") }

    // RC-17: the legend, over the top right of the map, in the dark palette in both themes.
    @Test fun gpsLegend() {
        show(dark) { Box(Modifier.fillMaxSize().background(Color(0xFF262B36)).padding(top = 60.dp, end = 0.dp), contentAlignment = Alignment.TopEnd) { GPSLegendCard(GPSSignal.searching, {}) } }
        shot("gps_legend")
    }

    // RC-25 and RC-32: the badge card in place, and the editor of the note: both are windows of their own.
    @Test fun summaryBadge() {
        show(dark) { TripCompleteSummary(fixture.trip, fixture.rewards, fixture.found, { _, _ -> }, {}, {}, {}, {}) }
        compose.onNodeWithTag("summary_badge_first_trip").performScrollTo().performClick()
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/recording_summary_badge_$theme.png")
    }
    @Test fun summaryNotes() {
        show(dark) { TripCompleteSummary(fixture.trip, fixture.rewards, fixture.found, { _, _ -> }, {}, {}, {}, {}) }
        compose.onNodeWithTag("summary_description").performScrollTo().performClick()
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/recording_summary_notes_$theme.png")
    }

    @Test fun recovery() {
        show(dark) { Box(Modifier.fillMaxSize().background(AppTheme.colors.bg)) {
            TrackingScreen(TrackingState(), fixture.car, false, TrackingActions(), fixture.now)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .3f)))
            Box(Modifier.align(Alignment.BottomCenter).background(AppTheme.colors.bg, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))) {
                RecoveryPromptContent(fixture.recovery, false, {}, {})
            }
        } }
        shot("recovery")
    }
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
