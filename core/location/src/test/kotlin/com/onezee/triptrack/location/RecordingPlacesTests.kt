// iOS: TripManager.markCheckpoint, TripWorldEntry.placesAndReveal, PlaceManagerTests.swift (draft gate).
package com.onezee.triptrack.location

import com.onezee.triptrack.location.recording.StartResult
import com.onezee.triptrack.location.recording.StopOutcome
import com.onezee.triptrack.model.TripConfirmation
import org.junit.Test
import kotlin.test.*

class RecordingPlacesTests : RecorderTestCase() {
    @Test fun checkpointDuringRecordingWaitsForFinalTrackAndSurvivesRelaunch() = test {
        val id = assertIs<StartResult.Started>(recorder.start()).tripId
        drive(straight(0..100))
        val checkpoint = assertNotNull(recorder.markCheckpoint("Место"))
        assertEquals(recorder.state.value.distance, checkpoint.distanceFromStart)
        val place = places.fetchPlaces().single()
        assertTrue(places.passes(place.id).isEmpty())
        assertEquals(place.id, repo.fetchTripDetail(id)!!.checkpoints.single().placeId)
        relaunch()
        recorder.recoverOrphans()
        assertTrue(recorder.adoptRecoverable())
        drive(straight(101..180, startNorth = 1010.0))
        assertIs<StopOutcome.Saved>(recorder.stop())
        assertEquals(listOf(id), places.passes(place.id).map { it.tripId })
        assertNotNull(repo.fetchEntity(id)!!.placesMatchedAt)
    }

    @Test fun draftRecordingCheckpointDoesNotCreateAPlace() = test {
        val id = assertIs<StartResult.Started>(recorder.start(confirmation = TripConfirmation.draft)).tripId
        drive(straight(0..100))
        assertNotNull(recorder.markCheckpoint("Место"))
        assertTrue(places.fetchPlaces().isEmpty())
        assertNull(repo.fetchTripDetail(id)!!.checkpoints.single().placeId)
        assertIs<StopOutcome.AwaitingConfirmation>(recorder.stop())
        assertTrue(places.fetchPlaces().isEmpty())
    }
}
