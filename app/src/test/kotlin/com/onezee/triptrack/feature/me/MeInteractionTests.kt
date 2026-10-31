// iOS: GarageProfileShotTests (DRIVE2, picker hit targets), VehicleCatalogPickerShotTests, OdometerFieldTests
package com.onezee.triptrack.feature.me

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.theme.TripTrackTheme
import com.onezee.triptrack.feature.garage.*
import com.onezee.triptrack.feature.settings.SettingsRoute
import com.onezee.triptrack.TripTrackApplication
import androidx.test.espresso.Espresso
import com.onezee.triptrack.model.*
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
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru-w411dp-h914dp-420dpi")
class MeInteractionTests {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = RuntimeEnvironment.getApplication()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }).around(compose)

    @Test fun drive2RejectsForeignHostsAndSavesTheTypedValueOnce() {
        var saved: String? = null
        compose.setContent { TripTrackTheme(false, AppLanguage.ru, reduceMotion = true) { Drive2Sheet("", false, { saved = it; true }, {}) } }
        compose.onNodeWithTag("garage_drive2_field").performTextInput("https://example.com/r/a/b/12/")
        compose.onNodeWithTag("garage_drive2_save").assertIsNotEnabled()
        assertNull(saved)
        compose.onNodeWithTag("garage_drive2_field").performTextReplacement("https://www.drive2.ru/r/volkswagen/polo/123/")
        compose.onNodeWithTag("garage_drive2_save").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals("https://www.drive2.ru/r/volkswagen/polo/123/", saved)
    }
    @Test fun dashboardChoiceReportsThenClosesAndMarksTheSelection() {
        var chosen: DashboardUnits? = null
        var open by mutableStateOf(true)
        compose.setContent { TripTrackTheme(false, AppLanguage.ru, reduceMotion = true) {
            if (open) DashboardUnitsPicker(DashboardUnits.metric, DistanceUnit.miles, true, { chosen = it }, { open = false })
        } }
        compose.onNodeWithTag("vehicle_dashboard_row_1").assertIsSelected()
        compose.onNodeWithTag("vehicle_dashboard_row_2").performClick()
        compose.waitForIdle()
        assertEquals(DashboardUnits.imperial, chosen)
        compose.onNodeWithTag("sheet").assertDoesNotExist()
    }
    @Test fun routineStartsCollapsedThenShowsSevenAndTheRest() {
        val trips = MeFixtures.trips.drop(1)
        val group = ProfileRoutineTrips(trips, trips.sumOf { it.distance }, trips.take(3))
        compose.setContent { TripTrackTheme(false, AppLanguage.ru, reduceMotion = true) { ProfileRoutineTripsView(group, DistanceUnit.km, {}) } }
        compose.onNodeWithTag("profile_routine_trip_${trips[0].id}").assertDoesNotExist()
        compose.onNodeWithTag("profile_routine_toggle").performClick()
        compose.onNodeWithTag("profile_routine_trip_${trips[6].id}").assertExists()
        compose.onNodeWithTag("profile_routine_trip_${trips[7].id}").assertDoesNotExist()
        compose.onNodeWithTag("profile_routine_more").performClick()
        compose.onNodeWithTag("profile_routine_trip_${trips[7].id}").assertExists()
    }
    @Test fun saleRequiresTheHouseConfirmationAndRunsOnce() {
        var changes = 0
        compose.setContent { TripTrackTheme(false, AppLanguage.ru, reduceMotion = true) { VehicleSaleAction(false) { changes++ } } }
        compose.onNodeWithTag("vehicle_sold").performClick()
        compose.waitForIdle(); assertEquals(0, changes)
        compose.onNodeWithTag("vehicle_sell_confirm").performClick()
        compose.waitForIdle(); assertEquals(1, changes)
        compose.onNodeWithTag("vehicle_sell_confirm").assertDoesNotExist()
    }
    @Test fun settingsDetailBackReturnsToRootWithoutDismissingTheSheet() {
        val graph = (RuntimeEnvironment.getApplication() as TripTrackApplication).appGraph
        var dismissed = false
        compose.setContent { TripTrackTheme(false, AppLanguage.ru, reduceMotion = true) { SettingsRoute(graph) { dismissed = true } } }
        compose.onNodeWithTag("settings_app_prefs").performClick()
        compose.onNodeWithTag("settings_units").assertExists()
        Espresso.pressBack()
        compose.waitForIdle()
        assertFalse(dismissed)
        compose.onNodeWithTag("settings_app_prefs").assertIsDisplayed()
    }
}
