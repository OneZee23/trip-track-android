// iOS: ProfileSettingsSheet.swift, AppPreferencesView.swift, PrivacySettingsView.swift. A sub-page of Настройки is a sheet of its
// own over the one before it (SE-10, SE-11), and the settings sheet shows the system grabber (SE-02).
package com.onezee.triptrack.feature.settings

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.onezee.triptrack.TripTrackApplication
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.TripTrackTheme
import com.onezee.triptrack.ui.LocalAppVersion
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
class SettingsStackShotTests(private val dark: Boolean) {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = RuntimeEnvironment.getApplication()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }).around(compose)

    private fun route() {
        val graph = (RuntimeEnvironment.getApplication() as TripTrackApplication).appGraph
        compose.setContent {
            TripTrackTheme(isDark = dark, language = AppLanguage.ru, reduceMotion = true) {
                CompositionLocalProvider(LocalAppVersion provides "0.9.2-dev") {
                    Box(Modifier.fillMaxSize().background(AppTheme.colors.bg)) { SettingsRoute(graph) {} }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun open(vararg tags: String) { tags.forEach { compose.onNodeWithTag(it).performClick(); compose.waitForIdle() } }

    private fun capture(name: String) { compose.waitForIdle(); captureScreenRoboImage("src/test/screenshots/settings_stack_${name}_${if (dark) "dark" else "light"}.png") }

    @Test fun theSettingsSheetShowsTheGrabber() { route(); capture("sheet") }
    @Test fun privacyIsASheetOverTheSettings() { route(); open("settings_privacy"); capture("privacy") }
    @Test fun thePreferencesAreASheetOverTheSettings() { route(); open("settings_app_prefs"); capture("preferences") }
    @Test fun theUnitsPickerStandsOverThePreferences() { route(); open("settings_app_prefs", "settings_units"); capture("units") }

    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
