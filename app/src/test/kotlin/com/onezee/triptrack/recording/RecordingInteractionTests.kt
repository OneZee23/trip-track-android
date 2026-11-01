// iOS: SlideToStartTests.swift, RecordingStatesTests.swift, RecordingFinishFlowTests.swift, TrackingTourTests.swift.
package com.onezee.triptrack.recording

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.location.recording.RecorderState
import com.onezee.triptrack.location.recording.StartRefusal
import com.onezee.triptrack.model.*
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecordingInteractionTests : RecordingScreenTest() {
    private val fixture = RecordingFixtures

    @Test fun testNoFixTouchStatesAReasonAndOffersStartAnyway() {
        var state by mutableStateOf(TrackingState())
        var starts = 0
        show { TrackingScreen(state, null, false, TrackingActions(blocked = { state = state.copy(refusal = StartRefusal.noFix) }, startAnyway = { starts++ }), fixture.now) }
        compose.onNodeWithTag("slide_to_start").performTouchInput {
            down(Offset(width * .06f, height * .5f)); moveTo(Offset(width * .97f, height * .5f)); up()
        }
        compose.onNodeWithTag("start_refused_toast").assertExists()
        compose.onNodeWithTag("tracking_pause").assertDoesNotExist()
        compose.onNodeWithTag("start_anyway").performClick()
        assertEquals(1, starts)
        compose.runOnUiThread { state = fixture.recording }
        compose.onNodeWithTag("tracking_pause").assertExists()
        compose.onNodeWithTag("slide_to_start").assertDoesNotExist()
    }

    @Test fun testWithAFixASlideStartsRecording() {
        var starts = 0
        show { SlideToStart(false, { starts++ }, {}, Modifier.padding(16.dp)) }
        compose.onNodeWithTag("slide_to_start").performTouchInput {
            swipe(Offset(width * .06f, height * .5f), Offset(width * .97f, height * .5f), 500)
        }
        compose.waitForIdle()
        assertEquals(1, starts)
    }

    @Test fun aTapAndAShortDragDoNotStartButAccessibilityActivationDoes() {
        var starts = 0
        show { SlideToStart(false, { starts++ }, {}, Modifier.padding(16.dp)) }
        compose.onNodeWithTag("slide_to_start").performTouchInput { click(Offset(20f, height * .5f)) }
        assertEquals(0, starts)
        compose.onNodeWithTag("slide_to_start").performTouchInput { swipe(Offset(20f, height * .5f), Offset(60f, height * .5f), 300) }
        compose.waitForIdle()
        assertEquals(0, starts)
        compose.onNodeWithTag("slide_to_start").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { assertTrue(it()) }
        assertEquals(1, starts)
    }

    @Test fun heldControlAnswersOncePerTouchAnywhereOnItsTrack() {
        var explanations = 0
        var starts = 0
        show { SlideToStart(true, { starts++ }, { explanations++ }, Modifier.padding(16.dp)) }
        compose.onNodeWithTag("slide_to_start").performTouchInput {
            down(Offset(width * .7f, height / 2f)); moveTo(Offset(width * .8f, height / 2f)); moveTo(Offset(width * .9f, height / 2f)); up()
        }
        assertEquals(1, explanations)
        assertEquals(0, starts)
        compose.onNodeWithTag("slide_to_start").performClick()
        assertEquals(2, explanations)
    }

    @Test fun idleScreenKeepsItsBackDiscAndHasNoRecordingActions() {
        var backs = 0
        show { TrackingScreen(TrackingState(), null, false, TrackingActions(back = { backs++ }), fixture.now) }
        compose.onNodeWithTag("screen_record").assertExists()
        compose.onNodeWithTag("tracking_stop").assertDoesNotExist()
        compose.onNodeWithTag("tracking_pause").assertDoesNotExist()
        compose.onNodeWithTag("tracking_back").performClick()
        assertEquals(1, backs)
    }

    @Test fun testShortRecordingWarnsBeforeDiscardAndReturningPreservesPause() {
        var state by mutableStateOf(fixture.recording)
        var question by mutableStateOf(false)
        var finishes = 0
        val preview = RecordingFinishPreview(fixture.id, fixture.start.plusSeconds(2), 0.0, 2.0, 0.0)
        show {
            TrackingScreen(state, fixture.car, false, TrackingActions(finish = { question = true }, pause = {
                state = state.copy(recorder = state.recorder.copy(isPaused = !state.recorder.isPaused))
            }), fixture.now)
            if (question) StopConfirmationSheet(preview, state.recorder.isPaused, false, { finishes++; question = false }, {}, { question = false })
        }
        compose.onNodeWithTag("tracking_stop").performClick()
        compose.onNodeWithTag("stop_confirm_title").assertTextEquals("Завершить без сохранения?")
        compose.onNodeWithTag("stop_confirm_explanation").assertTextEquals("Поездка слишком короткая и не попадёт в историю.")
        compose.onNodeWithTag("stop_confirm_finish").assertTextContains("Завершить без сохранения")
        compose.onNodeWithText("Завершить и сохранить").assertDoesNotExist()
        compose.onNodeWithTag("stop_confirm_cancel").performClick()
        compose.onNodeWithTag("tracking_pause").assertTextContains("Пауза")
        compose.onNodeWithTag("tracking_pause").performClick()
        compose.onNodeWithTag("tracking_pause").assertTextContains("Продолжить")
        compose.onNodeWithTag("tracking_stop").performClick()
        compose.onNodeWithTag("stop_confirm_pause").assertDoesNotExist()
        compose.onNodeWithTag("stop_confirm_cancel").performClick()
        compose.onNodeWithTag("tracking_pause").assertTextContains("Продолжить")
        assertTrue(state.recorder.isPaused)
        assertEquals(0, finishes)
        compose.onNodeWithTag("tracking_stop").performClick()
        compose.onNodeWithTag("stop_confirm_finish").performClick()
        assertEquals(1, finishes)
    }

    @Test fun testRecordingStatesTourAndCheckpointAnswer() {
        var state by mutableStateOf(fixture.recording.copy(recorder = fixture.recording.recorder.copy(checkpointCount = 0)))
        show { TrackingScreen(state, fixture.car, false, TrackingActions(pause = { state = state.copy(recorder = state.recorder.copy(isPaused = !state.recorder.isPaused)) },
            checkpoint = { state = state.copy(recorder = state.recorder.copy(checkpointCount = state.recorder.checkpointCount + 1)) }), fixture.now) }
        compose.onNodeWithTag("tracking_pause").performClick()
        compose.onNodeWithTag("tracking_speed").assertTextEquals("0")
        compose.onNodeWithText("Запись на паузе").assertExists()
        compose.onNodeWithTag("tracking_pause").performClick()
        compose.onNodeWithTag("tracking_speed").assertTextEquals("68")
        compose.onNodeWithTag("tracking_checkpoint").performClick()
        compose.onNodeWithTag("tracking_checkpoint").assertTextContains("1")
    }

    @Test fun metricsUseThePersonsMilesAndFeet() {
        show(unit = DistanceUnit.miles) { TrackingScreen(fixture.recording, fixture.car, false, TrackingActions(), fixture.now) }
        compose.onNodeWithTag("tracking_speed").assertTextEquals("42")
        compose.onNodeWithText("7.7").assertExists()
        compose.onNodeWithText("1 115").assertExists()
    }

    @Test fun controlsClearTheStatusAndGestureBars() {
        show { TrackingScreen(fixture.recording, fixture.car, false, TrackingActions(), fixture.now,
            contentInsets = WindowInsets(top = 28.dp, bottom = 34.dp)) }
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val back = compose.onNodeWithTag("tracking_back").fetchSemanticsNode().boundsInRoot
        val stop = compose.onNodeWithTag("tracking_stop").fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        assertTrue(back.top >= 28 * density)
        assertTrue(stop.bottom <= root.bottom - (34 + 24) * density + 1)
    }

    @Test fun recoveryCannotBeDismissedByTheScrimOrADrag() {
        var continued = 0
        var finished = 0
        show { RecoveryPromptSheet(fixture.recovery, false, { continued++ }, { finished++ }) }
        compose.onNodeWithTag("sheet_scrim").performTouchInput { click(Offset(10f, 10f)) }
        compose.onNodeWithTag("recovery_prompt").assertIsDisplayed()
        compose.onNodeWithTag("recovery_prompt").performTouchInput { swipeDown() }
        compose.onNodeWithTag("recovery_continue").assertIsDisplayed().performClick()
        compose.onNodeWithTag("recovery_finish").performClick()
        assertEquals(1, continued); assertEquals(1, finished)
        compose.onNodeWithText("Удалить").assertDoesNotExist()
    }

    @Test fun pickerExcludesArchivedAndSoldCarsAndKeepsPassengerDistinctFromNone() {
        var picked: java.util.UUID? = fixture.car.id
        var passenger = false
        val archive = fixture.car.copy(id = java.util.UUID.randomUUID(), name = "Archived", isArchived = true)
        val sold = fixture.car.copy(id = java.util.UUID.randomUUID(), name = "Sold", soldAt = fixture.now)
        show { VehiclePickerContent(listOf(fixture.car, archive, sold), fixture.car.id, false, { picked = it }, { passenger = true }, {}) }
        compose.onNodeWithText("Archived").assertDoesNotExist(); compose.onNodeWithText("Sold").assertDoesNotExist()
        compose.onNodeWithTag("vehicle_picker_none").performClick()
        assertNull(picked); assertFalse(passenger)
        compose.onNodeWithTag("vehicle_picker_transfer").performClick()
        assertTrue(passenger)
    }

    @Test fun cancellingTheDescriptionEditorDoesNotWriteOrReplaceTheSavedNote() {
        var saved: String? = null
        var done: Pair<String, Boolean>? = null
        show { TripCompleteSummary(fixture.trip.copy(tripDescription = "Saved"), fixture.rewards, null,
            { notes, publish -> done = notes to publish }, { saved = it }, {}, {}, {}) }
        compose.onNodeWithTag("summary_description").performScrollTo().performClick()
        compose.onNodeWithTag("summary_notes_field").performTextReplacement("Discarded")
        compose.onNodeWithTag("summary_notes_cancel").performClick()
        assertNull(saved)
        compose.onNodeWithTag("summary_done").performClick()
        assertEquals("Saved" to false, done)
    }
}
