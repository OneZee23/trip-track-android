package com.onezee.triptrack.feature.trip.ui

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.TripTrackTheme
import com.onezee.triptrack.feature.settings.PrivacyEdgeQuestionSheet
import com.onezee.triptrack.feature.settings.SettingsRoute
import com.onezee.triptrack.feature.onboarding.OnboardingContent
import com.onezee.triptrack.TripTrackApplication
import com.onezee.triptrack.model.DistanceUnit
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.TrackPoint
import com.onezee.triptrack.model.Trip
import java.time.Instant
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
class HiddenPartsScreenshotTests(private val dark: Boolean) {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = RuntimeEnvironment.getApplication()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }).around(compose)

    private val theme get() = if (dark) "dark" else "light"
    private val t0 = Instant.parse("2026-06-02T10:00:00Z")
    private val trip = Trip(startDate = t0, endDate = t0.plusSeconds(240), distance = 3_600.0, title = "Берег и перевал",
        trackPoints = listOf(0, 900, 1800, 2700, 3600).mapIndexed { index, north ->
            TrackPoint(latitude = 45.0 + north / 111_320.0, longitude = 38.0, altitude = 10.0 + index, speed = 12.0,
                horizontalAccuracy = 5.0, timestamp = t0.plusSeconds(index * 60L))
        })

    private fun frame(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            TripTrackTheme(isDark = dark, language = AppLanguage.ru, reduceMotion = true) {
                Box(Modifier.fillMaxSize().background(AppTheme.colors.bg)) { content() }
            }
        }
        compose.waitForIdle()
    }

    @Test fun question() {
        frame { PrivacyEdgeQuestionSheet({}, {}) }
        captureScreenRoboImage("src/test/screenshots/hidden_edge_question_$theme.png")
    }

    @Test fun editor() {
        frame { HiddenPartsEditorSheet(trip, 500, GeoPoint(45.0, 38.0) to 100.0, {}, {}) }
        captureScreenRoboImage("src/test/screenshots/hidden_parts_editor_$theme.png")
    }

    @Test fun publishSheet() {
        frame { PublishTripSheet(false, trip, 500, GeoPoint(45.0, 38.0) to 100.0, {}, null, {}, {}, {}) }
        captureScreenRoboImage("src/test/screenshots/hidden_parts_publish_$theme.png")
    }

    @Test fun settingsPrivacy() {
        val graph = (RuntimeEnvironment.getApplication() as TripTrackApplication).appGraph
        frame { SettingsRoute(graph) {} }
        compose.onNodeWithTag("settings_privacy").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("privacy_edges").performScrollTo()
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/hidden_settings_privacy_$theme.png")
        compose.onNodeWithTag("privacy_edges").performClick()
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/hidden_settings_edges_picker_$theme.png")
    }

    @Test fun onboardingQuestion() {
        frame {
            OnboardingContent(page = 4, distanceUnit = DistanceUnit.km, isRequesting = false,
                locationGranted = true, backgroundGranted = true, notificationsGranted = true,
                onContinue = {}, privacyEdgeMetres = 500)
        }
        captureScreenRoboImage("src/test/screenshots/hidden_onboarding_$theme.png")
    }

    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
