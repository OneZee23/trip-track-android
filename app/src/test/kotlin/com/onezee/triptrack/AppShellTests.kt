package com.onezee.triptrack

import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import com.onezee.triptrack.designsystem.navigation.AppTab
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.theme.AppColors
import com.onezee.triptrack.designsystem.theme.ThemeMode
import org.junit.After
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// iOS: ContentView.swift, CustomTabBar.swift, AppTab.swift, ThemeManager.swift, LanguageManager.swift.
// The real MainActivity, AppCompat theme and manifest included.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru")
class AppShellTests {
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(OnboardingDone()).around(compose)

    private val graph get() = (RuntimeEnvironment.getApplication() as TripTrackApplication).appGraph

    @After fun forgetTheLanguage() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    @Test fun opensOnTheFeedWithTheBarAndEveryTabSwitchesAndIsRemembered() {
        compose.onNodeWithTag("screen_feed").assertExists()
        compose.onNodeWithTag("tab_home").assertIsSelected()
        for (tab in listOf(AppTab.maps, AppTab.places, AppTab.profile)) {
            compose.onNodeWithTag("tab_${tab.rawValue}").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("tab_${tab.rawValue}").assertIsSelected()
            val screen = mapOf(AppTab.maps to "screen_atlas", AppTab.places to "screen_places", AppTab.profile to "screen_me").getValue(tab)
            compose.onNodeWithTag(screen).assertExists()
            val stored = compose.activity.getSharedPreferences("${compose.activity.packageName}_preferences", Context.MODE_PRIVATE)
                .getString(AppTab.storageKey, null)
            assertEquals(tab.rawValue, stored)
        }
    }

    @Test fun theRecordTabHidesTheBarAndItsBackButtonReturnsToTheFeed() {
        compose.onNodeWithTag("tab_record").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("screen_record").assertExists()
        compose.onNodeWithTag("tab_bar").assertDoesNotExist()
        compose.onNodeWithTag("tracking_back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("screen_feed").assertExists()
        compose.onNodeWithTag("tab_bar").assertExists()
    }

    /** Android's start destination: back on any other tab goes to Лента before it leaves the app. */
    @Test fun backOnAnotherTabReturnsToTheFeed() {
        compose.onNodeWithTag("tab_places").performClick()
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag("screen_feed").assertExists()
        assertFalse(compose.activity.isFinishing)
        assertFalse(compose.activity.onBackPressedDispatcher.hasEnabledCallbacks())
    }

    @Test fun theThemePickerChangesTheThemeAndTheSystemBarsFollow() {
        val window = compose.activity.window
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        compose.onNodeWithTag("tab_profile").performClick()
        compose.onNodeWithTag("settings_theme").performClick()
        compose.onNodeWithTag("settings_theme_row_2").performClick()
        compose.waitForIdle()
        assertEquals(ThemeMode.dark, graph.themeManager.mode)
        assertFalse(bars.isAppearanceLightStatusBars)
        assertEquals(AppColors.dark.bg.toArgb(), (window.decorView.background as ColorDrawable).color)

        compose.onNodeWithTag("settings_theme").performClick()
        compose.onNodeWithTag("settings_theme_row_1").performClick()
        compose.waitForIdle()
        assertEquals(ThemeMode.light, graph.themeManager.mode)
        assertTrue(bars.isAppearanceLightStatusBars)
        assertEquals(AppColors.light.bg.toArgb(), (window.decorView.background as ColorDrawable).color)
    }

    /** The per-app language: nothing chosen follows the phone; a pick is Android's own setting. */
    @Test fun theLanguagePickerSetsThePerAppLanguage() {
        assertNull(graph.languageManager.chosen)
        assertEquals(AppLanguage.ru, graph.languageManager.current(compose.activity.resources.configuration))
        compose.onNodeWithTag("tab_profile").performClick()
        compose.onNodeWithTag("settings_language").performClick()
        // Display order: ru, en, de…
        compose.onNodeWithTag("settings_language_row_2").performClick()
        compose.waitForIdle()
        assertEquals("de", AppCompatDelegate.getApplicationLocales().toLanguageTags())
        assertEquals(AppLanguage.de, graph.languageManager.chosen)
    }
}
