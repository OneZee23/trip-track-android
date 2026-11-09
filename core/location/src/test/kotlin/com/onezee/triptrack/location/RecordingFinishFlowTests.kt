// iOS: RecordingFinishFlowTests.swift, RecordingFinishManagerTests in RecordingFinishPreviewTests.swift.
package com.onezee.triptrack.location

import com.onezee.triptrack.location.recording.FinishConfirmation
import com.onezee.triptrack.location.recording.DiscardReason
import com.onezee.triptrack.location.recording.StartResult
import com.onezee.triptrack.location.recording.StopOutcome
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.TrackPoint
import com.onezee.triptrack.model.TripJunkClassifier
import java.time.Instant
import kotlin.test.*
import org.junit.Test

class RecordingFinishFlowTests : RecorderTestCase() {
    @Test fun testRecordingWithNoStoredPointsIsDiscardedEvenAfterGpsLoss() = test {
        val id = assertIs<StartResult.Started>(recorder.start()).tripId
        now = now.plusSeconds(180)
        recorder.tick()
        assertTrue(recorder.state.value.gpsLost)
        val outcome = assertIs<StopOutcome.Discarded>(recorder.stop())
        assertEquals(DiscardReason.noTrackPoints, outcome.reason)
        assertNull(db.tripDao().trip(id))
    }

    @Test fun testShortRecordingWarnsBeforeDiscardAndReturningPreservesPause() = test {
        val id = assertIs<StartResult.Started>(recorder.start()).tripId
        now = now.plusSeconds(2)
        val preview = assertNotNull(recorder.finishPreview())
        assertEquals(0.0, preview.distance)
        assertEquals(TripJunkClassifier.Reason.tooShort, preview.discardReason)
        assertTrue(recorder.state.value.isRecording)
        assertFalse(recorder.state.value.isPaused)
        assertNull(db.tripDao().trip(id)!!.endDate)
        recorder.togglePause()
        val paused = assertNotNull(recorder.finishPreview())
        assertEquals(preview.discardReason, paused.discardReason)
        assertTrue(recorder.state.value.isPaused, "returning from the question preserves the intentional pause")
        val result = assertIs<FinishConfirmation.Finished>(recorder.stopConfirming(paused))
        assertIs<StopOutcome.Discarded>(result.outcome)
        assertIs<StopOutcome.Discarded>(recorder.completion.value)
        assertNull(db.tripDao().trip(id))
        assertFalse(recorder.state.value.isRecording)
    }

    @Test fun testCrossingATimeBoundaryRequiresANewConfirmation() = test {
        recorder.start()
        val start = now
        now = start.plusSeconds(119)
        val short = assertNotNull(recorder.finishPreview())
        now = start.plusSeconds(120)
        val savedQuestion = assertIs<FinishConfirmation.Changed>(recorder.stopConfirming(short)).preview
        assertNull(savedQuestion.discardReason)
        assertTrue(recorder.state.value.isRecording)
        now = start.plusSeconds(181)
        val slow = assertIs<FinishConfirmation.Changed>(recorder.stopConfirming(savedQuestion)).preview
        assertEquals(TripJunkClassifier.Reason.noDrivingSpeed, slow.discardReason)
        assertNull(recorder.completion.value)
        assertIs<StopOutcome.Discarded>(assertIs<FinishConfirmation.Finished>(recorder.stopConfirming(slow)).outcome)
    }

    @Test fun testPreviewUsesTheSameTrimAndOdometerAsFinishingWithoutMutatingRecording() = test {
        val start = Instant.ofEpochSecond(1_760_000_000)
        now = start
        val id = assertIs<StartResult.Started>(recorder.start()).tripId
        val samples = listOf(Triple(0L, 45.0, 10.0), Triple(30L, 45.001, 10.0), Triple(110L, 45.001, 0.0))
        store.appendPoints(id, samples.map { (seconds, latitude, speed) ->
            TrackPoint(latitude = latitude, longitude = 38.0, speed = speed, horizontalAccuracy = 8.0, timestamp = start.plusSeconds(seconds))
        }, 90_000.0, 90.0, 0.0)
        recorder.setPaused(true)
        // Persist the stale live values after the pause flush; opening the question must leave them alone.
        db.tripDao().upsertTrip(db.tripDao().trip(id)!!.copy(distance = 90_000.0, maxSpeed = 90.0))
        now = start.plusSeconds(300)
        val preview = assertNotNull(recorder.finishPreview())
        assertEquals(start.plusSeconds(30), preview.endDate)
        assertEquals(30.0, preview.duration)
        assertTrue(preview.distance < 500)
        assertEquals(10.0, preview.maxSpeed)
        assertEquals(TripJunkClassifier.Reason.tooShort, preview.discardReason)
        assertNull(db.tripDao().trip(id)!!.endDate)
        assertEquals(90_000.0, db.tripDao().trip(id)!!.distance)
        assertTrue(recorder.state.value.isRecording)
        assertTrue(recorder.state.value.isPaused)
        recorder.stop(preview.endDate)
        assertNull(recorder.finishPreview())
        assertIs<StopOutcome.Discarded>(recorder.completion.value)
    }

    @Test fun testEmptyRecordingPreviewUsesTheSameTimeClampAsStop() = test {
        val id = assertIs<StartResult.Started>(recorder.start()).tripId
        val start = now
        now = start.minusSeconds(10)
        val preview = assertNotNull(recorder.finishPreview())
        assertEquals(0.0, preview.duration)
        assertEquals(start, preview.endDate)
        assertEquals(TripJunkClassifier.Reason.tooShort, preview.discardReason)
        recorder.stop(preview.endDate)
        assertNull(db.tripDao().trip(id))
    }

    @Test fun pendingPointsArePreviewedWithoutDrainingOrWritingTheBatch() = test {
        val id = assertIs<StartResult.Started>(recorder.start()).tripId
        drive(straight(0..8))
        val preview = assertNotNull(recorder.finishPreview())
        assertEquals(0, storedPointCount(id))
        assertEquals(9, recorder.track.value.size)
        assertTrue(preview.distance > 70)
        drive(straight(9..9, startNorth = 90.0))
        assertEquals(10, storedPointCount(id), "the question did not drain the pending batch")
    }

    @Test fun recoveredFinishUsesTheLastOldFixWithoutAddingTheRelaunchWait() = test {
        val id = assertIs<StartResult.Started>(recorder.start()).tripId
        drive(straight(0..180))
        recorder.setPaused(true)
        val last = now
        relaunch()
        now = last.plusSeconds(3600)
        assertFalse(assertNotNull(recorder.recoverOrphans()).isFresh)
        val saved = assertIs<StopOutcome.Saved>(recorder.finishRecoverable())
        assertEquals(last, saved.trip.endDate)
        assertEquals(180.0, saved.trip.duration)
        assertEquals(last, db.tripDao().trip(id)!!.endDate)
    }

    @Test fun recoveredLiveTrackContainsTheSavedPointsOnlyOnce() = test {
        val id = assertIs<StartResult.Started>(recorder.start()).tripId
        drive(straight(0..180))
        recorder.setPaused(true)
        val count = storedPointCount(id)
        relaunch(); recorder.recoverOrphans(); recorder.adoptRecoverable()
        assertEquals(count, recorder.track.value.size)
        recorder.finishPreview()
        recorder.stop()
        assertEquals(count, storedPointCount(id))
    }

    @Test fun aMidDriveVehiclePickReassignsTheWholeTripAndClearsPassengerFirst() = test {
        val car = vehicles.addVehicle("Car", "🚗")
        val id = assertIs<StartResult.Started>(recorder.start(isTransfer = true)).tripId
        recorder.assignVehicle(car)
        assertFalse(recorder.state.value.isTransfer)
        assertEquals(car, recorder.state.value.vehicleId)
        assertFalse(db.tripDao().trip(id)!!.isTransfer)
        assertEquals(car, db.tripDao().trip(id)!!.vehicleId)
        recorder.assignVehicle(null, transfer = true)
        assertNull(recorder.state.value.vehicleId)
        assertTrue(db.tripDao().trip(id)!!.isTransfer)
    }
}
