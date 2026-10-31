package com.onezee.triptrack.location

import com.onezee.triptrack.location.permissions.HealthIssue
import com.onezee.triptrack.location.permissions.Oem
import com.onezee.triptrack.location.permissions.PermissionFlow
import com.onezee.triptrack.location.permissions.PermissionLedger
import com.onezee.triptrack.location.permissions.PermissionPlan
import com.onezee.triptrack.location.permissions.PermissionStep
import com.onezee.triptrack.location.permissions.RecordingHealth
import com.onezee.triptrack.location.permissions.RecordingPermission
import com.onezee.triptrack.location.permissions.RecordingPermission.activityRecognition
import com.onezee.triptrack.location.permissions.RecordingPermission.backgroundLocation
import com.onezee.triptrack.location.permissions.RecordingPermission.bluetoothConnect
import com.onezee.triptrack.location.permissions.RecordingPermission.fineLocation
import com.onezee.triptrack.location.permissions.RecordingPermission.notifications
import com.onezee.triptrack.location.permissions.RequestMethod
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

// iOS: OnboardingPermissionTests.swift (one question at a time, a refusal still advances, a late
// answer cannot finish the next page), with Android's own permissions and SDK levels.
class PermissionPlanTests {
    @Test
    fun preciseAndApproximateAreRequestedTogetherOnBothRecordingAndOnboarding() {
        assertEquals(listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"), PermissionPlan.manifestNames(fineLocation).toList())
        assertEquals(listOf(notifications.manifestName), PermissionPlan.manifestNames(notifications).toList())
    }

    @Test
    fun approximateAccessNeverCountsAsPreciseOrAllowsBackgroundUpgrade() {
        val coarse = "android.permission.ACCESS_COARSE_LOCATION"
        assertTrue(PermissionPlan.hasApproximateLocation { it == coarse })
        assertFalse(PermissionPlan.hasApproximateLocation { true })
        assertFalse(PermissionPlan.hasApproximateLocation { false })
        val grants = PermissionPlan.effectiveGrants(31) { it.manifestName == coarse }
        val next = PermissionPlan.next(31, grants, setOf(fineLocation)) as PermissionStep.Explain
        assertEquals(activityRecognition, next.permission)
    }

    /** Walks the plan as the screens do: every question answered with [answers], asked once. */
    private fun walk(sdk: Int, answers: (RecordingPermission) -> Boolean): List<Pair<RecordingPermission, RequestMethod>> {
        val granted = mutableSetOf<RecordingPermission>()
        val asked = mutableSetOf<RecordingPermission>()
        val steps = mutableListOf<Pair<RecordingPermission, RequestMethod>>()
        while (true) {
            val step = PermissionPlan.next(sdk, granted, asked) as? PermissionStep.Explain ?: break
            steps += step.permission to step.method
            asked += step.permission
            if (answers(step.permission)) granted += step.permission
        }
        return steps
    }

    @Test
    fun android14AsksEverythingInOrderAndBackgroundThroughSettings() {
        assertEquals(
            listOf(
                fineLocation to RequestMethod.dialog,
                backgroundLocation to RequestMethod.settingsPage,
                activityRecognition to RequestMethod.dialog,
                notifications to RequestMethod.dialog,
                bluetoothConnect to RequestMethod.dialog,
            ),
            walk(34) { true },
        )
    }

    @Test
    fun android10HasABackgroundDialogAndNoNotificationOrBluetoothQuestion() {
        assertEquals(
            listOf(fineLocation to RequestMethod.dialog, backgroundLocation to RequestMethod.dialog, activityRecognition to RequestMethod.dialog),
            walk(29) { true },
        )
    }

    @Test
    fun android12AsksBluetoothButNotNotifications() {
        assertEquals(listOf(fineLocation, backgroundLocation, activityRecognition, bluetoothConnect), walk(31) { true }.map { it.first })
    }

    // A refusal is a decision too: the flow moves on, and never asks for «all the time» on top of a
    // denied precise location, where the system has nothing to offer.
    @Test
    fun aDeniedLocationSkipsTheBackgroundQuestionAndTheRestStillComes() {
        assertEquals(
            listOf(fineLocation, activityRecognition, notifications, bluetoothConnect),
            walk(34) { it != fineLocation }.map { it.first },
        )
    }

    @Test
    fun nothingIsAskedTwice() {
        val asked = setOf(fineLocation, backgroundLocation)
        val step = PermissionPlan.next(34, granted = setOf(fineLocation), asked = asked) as PermissionStep.Explain
        assertEquals(activityRecognition, step.permission)
        assertEquals(PermissionStep.Done, PermissionPlan.next(34, emptySet(), RecordingPermission.entries.toSet()))
    }

    @Test
    fun onlyTheWantedPermissionsAreAsked() {
        val step = PermissionPlan.next(34, setOf(fineLocation), emptySet(), wanted = setOf(fineLocation, notifications))
        assertEquals(PermissionStep.Explain(notifications, RequestMethod.dialog), step)
    }

    @Test
    fun whatAnOlderAndroidDoesNotHaveCountsAsGranted() {
        val grants = PermissionPlan.effectiveGrants(29) { it == fineLocation }
        assertEquals(setOf(fineLocation, notifications, bluetoothConnect), grants)
    }

    // iOS: testDoubleTapCannotIssueAnotherPermissionRequest / testLateCompletionFromPreviousPage….
    @Test
    fun aDoubleTapPutsOneQuestionAndALateAnswerFinishesNothing() {
        val ledger = PermissionLedger.InMemory()
        val flow = PermissionFlow(ledger)
        val first = flow.begin(fineLocation)!!
        assertNull(flow.begin(fineLocation), "the duplicate tap is ignored")
        assertTrue(flow.isRequesting)
        assertTrue(flow.complete(first))
        assertFalse(flow.complete(first), "an answer arrives once")
        val second = flow.begin(notifications)!!
        assertFalse(flow.complete(first), "the previous page's late answer")
        assertTrue(flow.isRequesting)
        assertTrue(flow.complete(second))
        assertEquals(setOf(fineLocation, notifications), ledger.asked())
    }

    @Test
    fun healthListsTheWorstFirstAndAutoRecordIssuesOnlyWhenItIsOn() {
        val healthy = RecordingHealth(
            locationEnabled = true, preciseLocation = true, backgroundLocation = false, activityRecognition = false,
            notifications = true, bluetoothConnect = false, ignoringBatteryOptimizations = true,
            backgroundRestricted = false, powerSave = false, oem = Oem.other, autoRecord = false,
        )
        assertEquals(emptyList<HealthIssue>(), healthy.issues)
        val broken = healthy.copy(locationEnabled = false, ignoringBatteryOptimizations = false, autoRecord = true, powerSave = true)
        assertEquals(
            listOf(
                HealthIssue.locationOff, HealthIssue.batteryOptimized, HealthIssue.noBackgroundLocation,
                HealthIssue.noActivityRecognition, HealthIssue.noBluetooth, HealthIssue.powerSave,
            ),
            broken.issues,
        )
    }

    @Test
    fun vendorsAreRecognisedByManufacturerOrBrand() {
        assertEquals(Oem.samsung, Oem.from("samsung"))
        assertEquals(Oem.xiaomi, Oem.from("Xiaomi"))
        assertEquals(Oem.xiaomi, Oem.from("unknown", "POCO"))
        assertEquals(Oem.huawei, Oem.from("HONOR"))
        assertEquals(Oem.other, Oem.from("Google", "google"))
    }
}
