// iOS: OnboardingPermissionTests.swift (coordinator half), TripTrackApp hasCompletedOnboarding
package com.onezee.triptrack.feature.onboarding

import android.Manifest
import android.content.Context
import com.onezee.triptrack.feature.onboarding.OnboardingPermissionCoordinator.Step
import com.onezee.triptrack.location.permissions.PermissionLedger
import com.onezee.triptrack.location.permissions.RecordingPermission
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnboardingTests {
    private val ledger = PermissionLedger.InMemory()
    private var granted = setOf<RecordingPermission>()
    private val asks = mutableListOf<Pair<RecordingPermission, Int>>()

    private fun coordinator(sdk: Int = 34) = OnboardingPermissionCoordinator(ledger, sdk) { granted }.also { c ->
        c.ask = { permission, token -> asks += permission to token }
    }

    private val askedNames get() = asks.map { it.first }

    @Test fun testLocationDenialStillAdvancesOnce() {
        val c = coordinator()
        var advances = 0
        c.proceed(Step.location) { advances++ }
        assertTrue(c.isRequesting)
        assertEquals(listOf(RecordingPermission.fineLocation), askedNames)
        // The answer has no granted/denied gate: refusal is a decision and the page moves on.
        val token = asks.single().second
        c.answered(token); c.answered(token)
        assertEquals(1, advances)
        assertFalse(c.isRequesting)
    }

    @Test fun testBackgroundWaitsForLocationBeforeActivityAndForActivityBeforeAdvancing() {
        granted = setOf(RecordingPermission.fineLocation)
        val c = coordinator()
        var advances = 0
        c.proceed(Step.background) { advances++ }
        assertEquals(listOf(RecordingPermission.backgroundLocation), askedNames)
        c.answered(asks.last().second); c.answered(asks.first().second)
        assertEquals(listOf(RecordingPermission.backgroundLocation, RecordingPermission.activityRecognition), askedNames)
        assertEquals(0, advances)
        assertTrue(c.isRequesting)
        c.answered(asks.last().second)
        assertEquals(1, advances)
        assertFalse(c.isRequesting)
    }

    @Test fun testDoubleTapCannotIssueAnotherPermissionRequest() {
        granted = setOf(RecordingPermission.fineLocation)
        val c = coordinator()
        var advances = 0
        c.proceed(Step.background) { advances++ }
        c.proceed(Step.background) { fail("Duplicate tap must be ignored") }
        assertEquals(listOf(RecordingPermission.backgroundLocation), askedNames)
        c.answered(asks.last().second)
        c.proceed(Step.notifications) { fail("Activity recognition has not resolved yet") }
        assertEquals(listOf(RecordingPermission.backgroundLocation, RecordingPermission.activityRecognition), askedNames)
        c.answered(asks.last().second)
        assertEquals(1, advances)
    }

    @Test fun testUnavailableOrPreviouslyAnsweredPermissionsCompleteSynchronously() {
        // Granted already, or not a question on this Android: nothing to ask, every page moves on.
        granted = RecordingPermission.entries.toSet()
        val c = coordinator()
        var advances = 0
        for (step in Step.entries) {
            c.proceed(step) { advances++ }
            assertFalse(c.isRequesting)
        }
        assertEquals(3, advances)
        assertTrue(asks.isEmpty())
    }

    /** Android 10 and 11 have no notification permission: that page moves on without a question. */
    @Test fun aQuestionThatDoesNotExistOnThisAndroidIsSkipped() {
        val c = coordinator(sdk = 30)
        var advances = 0
        c.proceed(Step.notifications) { advances++ }
        assertEquals(1, advances)
        assertTrue(asks.isEmpty())
    }

    @Test fun testLateCompletionFromPreviousPageCannotFinishNextPage() {
        val c = coordinator()
        var advances = 0
        c.proceed(Step.location) { advances++ }
        val old = asks.single().second
        c.answered(old)
        c.proceed(Step.notifications) { advances++ }
        c.answered(old)
        assertEquals(1, advances)
        assertTrue(c.isRequesting)
        c.answered(asks.last().second)
        assertEquals(2, advances)
    }

    /** iOS: testAnsweredLocationRequestsNeverAskAgainOrAskAlwaysAfterDenial. */
    @Test fun answeredQuestionsAreNeverAskedAgainAndBackgroundNeedsPreciseLocation() {
        val c = coordinator()
        ledger.markAsked(RecordingPermission.fineLocation)
        assertNull(c.nextQuestion(Step.location, emptySet()), "a refusal is not asked again")
        // Location denied: «all the time» is not offered, the activity question still comes.
        assertEquals(RecordingPermission.activityRecognition, c.nextQuestion(Step.background, emptySet()))
        assertEquals(RecordingPermission.backgroundLocation, c.nextQuestion(Step.background, setOf(RecordingPermission.fineLocation)))
        assertNull(c.nextQuestion(Step.notifications, setOf(RecordingPermission.notifications)))
    }

    /** The ledger is the recording's: what onboarding asked, the record screen does not ask again. */
    @Test fun aQuestionIsMarkedInTheSharedLedgerWhenItIsPut() {
        val c = coordinator()
        c.proceed(Step.location) {}
        assertEquals(setOf(RecordingPermission.fineLocation), ledger.asked())
    }

    /** From Android 12 precise location is ignored unless approximate is asked with it. */
    @Test fun preciseLocationIsAskedTogetherWithApproximate() {
        assertContentEquals(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            OnboardingPermissionCoordinator.manifestNames(RecordingPermission.fineLocation))
        assertContentEquals(arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            OnboardingPermissionCoordinator.manifestNames(RecordingPermission.notifications))
    }

    @Test fun completionIsRememberedUnderTheIosKey() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("onboarding-test", Context.MODE_PRIVATE)
        val store = OnboardingStore(prefs)
        assertFalse(store.completed)
        store.complete()
        assertTrue(OnboardingStore(prefs).completed)
        assertEquals("true", prefs.getString("hasCompletedOnboarding", null))
        store.reset()
        assertFalse(OnboardingStore(prefs).completed)
    }
}
