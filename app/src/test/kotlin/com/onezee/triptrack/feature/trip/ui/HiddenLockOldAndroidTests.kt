package com.onezee.triptrack.feature.trip.ui

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.theme.TripTrackTheme
import com.onezee.triptrack.model.TrackPoint
import com.onezee.triptrack.model.Trip
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// iOS: HiddenPartsEditor.swift.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29, 30], qualifiers = "ru-w393dp-h780dp-420dpi")
class HiddenLockOldAndroidTests {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = RuntimeEnvironment.getApplication()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }).around(compose)

    @Test
    fun editorShowsBothHandlesAndHintOnOldAndroid() {
        val start = Instant.parse("2026-06-02T10:00:00Z")
        val trip = Trip(
            startDate = start,
            endDate = start.plusSeconds(120),
            distance = 1_800.0,
            trackPoints = listOf(0, 900, 1_800).mapIndexed { index, north ->
                TrackPoint(
                    latitude = 45.0 + north / 111_320.0,
                    longitude = 38.0,
                    altitude = 10.0,
                    speed = 12.0,
                    horizontalAccuracy = 5.0,
                    timestamp = start.plusSeconds(index * 60L),
                )
            },
        )
        compose.setContent {
            TripTrackTheme(isDark = false, language = AppLanguage.ru, reduceMotion = true) {
                HiddenPartsEditorSheet(trip, 0, null, {}, {})
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("hidden_handle_start").fetchSemanticsNode()
        compose.onNodeWithTag("hidden_handle_end").fetchSemanticsNode()
        compose.onNodeWithTag("hidden_drag_hint").fetchSemanticsNode()
    }
}
