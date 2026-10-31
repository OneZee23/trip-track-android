package com.onezee.triptrack

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.theme.TripTrackTheme
import com.onezee.triptrack.feature.settings.SettingsRoute
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MeSettingsPickerTests {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = RuntimeEnvironment.getApplication()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }).around(compose)

    /**
     * A language pick recreates the activity at once, while a sheet would still be playing its
     * exit, and the state saved then is the state restored: a picker still open in it opens again
     * in the new language. So the pick closes the picker on the very next frame.
     */
    @Test fun aLanguagePickClosesThePickerAtOnce() {
        val graph = (RuntimeEnvironment.getApplication() as TripTrackApplication).appGraph
        compose.setContent {
            TripTrackTheme(isDark = false, language = AppLanguage.ru, reduceMotion = false) {
                SettingsRoute(graph) {}
            }
        }
        compose.onNodeWithTag("settings_language").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("settings_language_row_1").assertExists()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("settings_language_row_1").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("settings_language_row_1").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    /** The theme changes in place, so its picker leaves the ordinary way, with its exit. */
    @Test fun aThemePickClosesThePickerWithItsExit() {
        val graph = (RuntimeEnvironment.getApplication() as TripTrackApplication).appGraph
        compose.setContent {
            TripTrackTheme(isDark = false, language = AppLanguage.ru, reduceMotion = false) {
                SettingsRoute(graph) {}
            }
        }
        compose.onNodeWithTag("settings_theme").performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("settings_theme_row_2").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("settings_theme_row_2").assertExists()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("settings_theme_row_2").assertDoesNotExist()
    }
}
