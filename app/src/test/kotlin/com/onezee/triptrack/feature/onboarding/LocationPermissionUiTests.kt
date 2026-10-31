// iOS: OnboardingPermissionTests.swift; Android precise/approximate permission integration.
package com.onezee.triptrack.feature.onboarding

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.theme.TripTrackTheme
import com.onezee.triptrack.location.permissions.PermissionLedger
import com.onezee.triptrack.location.permissions.RecordingPermission
import com.onezee.triptrack.model.DistanceUnit
import kotlin.test.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocationPermissionUiTests {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = RuntimeEnvironment.getApplication()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }).around(compose)

    @Test fun approximateAnswerAdvancesShowsSettingsAndNeverRequestsLocationAgain() {
        val ledger = PermissionLedger.InMemory()
        compose.setContent {
            TripTrackTheme(false, AppLanguage.en, reduceMotion = true) {
                OnboardingScreen(ledger, DistanceUnit.km, 0, {}, startPage = 2)
            }
        }
        compose.onNodeWithTag("onboarding_continue_location").performClick()
        val request = shadowOf(compose.activity).lastRequestedPermission
        assertContentEquals(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), request.requestedPermissions)
        compose.runOnUiThread {
            shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
            compose.activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions,
                intArrayOf(PackageManager.PERMISSION_DENIED, PackageManager.PERMISSION_GRANTED))
        }
        compose.onNodeWithTag("onboarding_page_3").assertExists()
        compose.onNodeWithTag("onboarding_precise_settings").performScrollTo().performClick()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity.action)
        compose.onNodeWithTag("onboarding_continue_background").performClick()
        assertContentEquals(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), shadowOf(compose.activity).lastRequestedPermission.requestedPermissions)
        assertEquals(setOf(RecordingPermission.fineLocation, RecordingPermission.activityRecognition), ledger.asked())
    }

    @Test fun backgroundAccessOpensSettingsBeforeAskingForActivity() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val ledger = PermissionLedger.InMemory()
        compose.setContent {
            TripTrackTheme(false, AppLanguage.en, reduceMotion = true) {
                OnboardingScreen(ledger, DistanceUnit.km, 0, {}, startPage = 3)
            }
        }
        compose.onNodeWithTag("onboarding_continue_background").performClick()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity.action)
        assertEquals(setOf(RecordingPermission.backgroundLocation), ledger.asked())
        assertNull(shadowOf(compose.activity).lastRequestedPermission)
    }
}
