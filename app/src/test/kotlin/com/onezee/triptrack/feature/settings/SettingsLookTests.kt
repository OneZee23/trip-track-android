// iOS: ProfileComponents.swift (SettingsIconRow, SettingsRowChevron), ProfileSettingsSheet.swift (rows, footer, sheets),
// AppPreferencesView.swift. The numbers are the iPhone's (SE-01…SE-16 of the 3 November audit); the frames are MeScreenshotTests.
package com.onezee.triptrack.feature.settings

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.onezee.triptrack.TripTrackApplication
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.theme.TripTrackTheme
import com.onezee.triptrack.feature.account.SyncState
import com.onezee.triptrack.designsystem.theme.ThemeMode
import com.onezee.triptrack.ui.LocalAppVersion
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w411dp-h914dp-420dpi")
class SettingsLookTests {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = RuntimeEnvironment.getApplication()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }).around(compose)

    private fun near(expected: Float, actual: Float, what: String, tolerance: Float = 0.75f) =
        assertTrue(abs(expected - actual) <= tolerance, "$what: expected $expected, was $actual")

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).getBoundsInRoot()

    private fun settings(signedIn: Boolean = true) {
        compose.setContent {
            TripTrackTheme(isDark = false, language = AppLanguage.ru, reduceMotion = true) {
                CompositionLocalProvider(LocalAppVersion provides "0.9.2-dev") {
                    Box(Modifier.size(411.dp, 914.dp)) {
                        SettingsContent(SettingsState(loaded = true), ThemeMode.light, AppLanguage.ru, signedIn, NotificationPreferencesState(loaded = true),
                            {}, {}, {}, {}, {}, account = { SettingsAccountRow(cloudSyncEnabled = true, state = SyncState.Off, onClick = {}) })
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun aRowWithNoSubtitleIs56TallAndOneWithASubtitleIsAboutTheSame() {
        settings()
        // iOS SettingsIconRow: 13 above and below a 30 tile. The toggle row: 6 above and below the 44 frame of the switch.
        near(56f, bounds("settings_language").height.value, "language")
        near(56f, bounds("settings_theme").height.value, "theme")
        near(56f, bounds("settings_account_sync").height.value, "account")
        near(56f, bounds("settings_notifications").height.value, "the toggle row")
        // SettingsLinkRow: 11 above and below a title, 2, and a subtitle; 55 on the iPhone.
        listOf("settings_privacy", "settings_write_author", "settings_telegram", "settings_logs").forEach { near(55f, bounds(it).height.value, it, 1.5f) }
    }

    @Test fun everyIconSquareIs30AndEveryRowStartsAt14() {
        settings()
        val tiles = compose.onAllNodesWithTag("settings_tile", useUnmergedTree = true)
        // privacy, notifications, account, language, theme, units, write, telegram, logs.
        tiles.assertCountEquals(9)
        for (index in 0 until 9) {
            val tile = tiles[index].getBoundsInRoot()
            near(30f, tile.width.value, "tile $index width"); near(30f, tile.height.value, "tile $index height")
            // The cards stand 14 from the edge and the rows are 14 inside them.
            near(28f, tile.left.value, "tile $index left")
        }
    }

    @Test fun theUnitsLiveBehindOneRowAndThereIsNoAboutRow() {
        settings()
        compose.onNodeWithTag("settings_app_prefs").assertExists()
        compose.onNodeWithTag("settings_units").assertDoesNotExist()
        compose.onNodeWithTag("settings_about").assertDoesNotExist()
    }

    @Test fun aGuestSeesThePrivacyRowAloneInTheFirstCard() {
        settings(signedIn = false)
        compose.onNodeWithTag("settings_privacy").assertExists()
        compose.onNodeWithTag("settings_notifications").assertDoesNotExist()
        compose.onNodeWithTag("settings_account_sync").assertDoesNotExist()
        compose.onAllNodesWithTag("settings_tile", useUnmergedTree = true).assertCountEquals(7)
    }

    @Test fun theFooterStacksTheLinksAndTheCreditsCentredUnderTheCards() {
        settings()
        val footer = bounds("settings_footer")
        val logs = bounds("settings_logs")
        val policy = bounds("settings_privacy_policy")
        val terms = bounds("settings_terms")
        val osm = bounds("settings_osm_attribution")
        val natural = bounds("settings_ne_attribution")
        // The column's 4 and the footer's own 12 above it.
        near(16f, footer.top.value - logs.bottom.value, "footer under the last card")
        near(policy.top.value, terms.top.value, "the legal links stand side by side")
        near(16f, terms.left.value - policy.right.value, "16 between the links")
        near(10f, osm.top.value - maxOf(policy.bottom.value, terms.bottom.value), "10 under the links")
        near(10f, natural.top.value - osm.bottom.value, "10 under the credit")
        near(411f / 2, (policy.left.value + terms.right.value) / 2, "the links are centred", 1.5f)
        near(411f / 2, (natural.left.value + natural.right.value) / 2, "the credit is centred", 1.5f)
    }

    /** The sheets iOS gives `presentationDragIndicator(.visible)`: Настройки, its sub-pages, auto-record and the stereo picker. */
    @Test fun onlyTheSheetsOfTheSettingsFlowShowTheGrabber() {
        val shown = SettingsSheet.entries.filter { it.hasGrabber }
        assertEquals(setOf(SettingsSheet.settings, SettingsSheet.appPreferences, SettingsSheet.privacy, SettingsSheet.autoRecord, SettingsSheet.stereo), shown.toSet())
    }

    // ---- The sub-pages are sheets of their own, one over the other ----

    private fun route(): com.onezee.triptrack.AppGraph {
        val graph = (RuntimeEnvironment.getApplication() as TripTrackApplication).appGraph
        compose.setContent { TripTrackTheme(isDark = false, language = AppLanguage.ru, reduceMotion = true) { SettingsRoute(graph) {} } }
        compose.waitForIdle()
        return graph
    }

    @Test fun privacyOpensAsASheetOverTheSettingsAndTheSettingsStayBeneath() {
        val graph = route()
        compose.onNodeWithTag("settings_privacy").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("settings_usage_analytics").assertExists()
        compose.onNodeWithTag("settings_sheet").assertExists()
        assertEquals(listOf(SettingsSheet.settings, SettingsSheet.privacy), graph.settingsManager.state.value.sheets)
        compose.runOnIdle { graph.settingsManager.dismissSheet() }
        compose.waitForIdle()
        compose.onNodeWithTag("settings_usage_analytics").assertDoesNotExist()
        compose.onNodeWithTag("settings_sheet").assertExists()
    }

    @Test fun theUnitsPickerOpensOverThePreferencesOverTheSettings() {
        val graph = route()
        compose.onNodeWithTag("settings_app_prefs").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("settings_units").performClick()
        compose.waitForIdle()
        // Three layers at once: the settings, the preferences card, the picker over it.
        compose.onNodeWithTag("settings_sheet").assertExists()
        compose.onNodeWithTag("settings_average_speed").assertExists()
        compose.onNodeWithTag("settings_units_row_0").assertExists()
        assertEquals(listOf(SettingsSheet.settings, SettingsSheet.appPreferences, SettingsSheet.units), graph.settingsManager.state.value.sheets)
    }
}
