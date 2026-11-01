// iOS: TripManager.nameCheckpointFromPlace/applyPlaceName; PlaceManagerTests.testGeocoderNameArrivesBeforeRegistration.
package com.onezee.triptrack.location

import com.onezee.triptrack.location.recording.StartResult
import com.onezee.triptrack.location.world.CheckpointNamer
import com.onezee.triptrack.location.world.Placemark
import com.onezee.triptrack.logic.GeohashEncoder
import kotlinx.coroutines.*
import org.junit.Test
import kotlin.test.*

class CheckpointNamerTests : RecorderTestCase() {
    @Test fun warmCacheNamesBeforeRegistrationButHistoryIsStillDeferred() = test {
        val id = assertIs<StartResult.Started>(recorder.start()).tripId
        drive(straight(0..100))
        val cp = assertNotNull(recorder.markCheckpoint())
        val cell = GeohashEncoder.encode(cp.latitude, cp.longitude, 5)
        repo.saveGeocode(cell, "Джубга", null)
        // A separate cell's unregistered checkpoint exercises the warm-cache ordering.
        val unregistered = cp.copy(id = java.util.UUID.randomUUID(), longitude = cp.longitude + 1.0)
        repo.addCheckpoint(unregistered, id)
        repo.saveGeocode(GeohashEncoder.encode(unregistered.latitude, unregistered.longitude, 5), "Джубга", null)
        val namer = CheckpointNamer(repo, placeManager, { error("warm cache never calls the geocoder") }, { listOf("Отметка", "Checkpoint") }, CoroutineScope(currentCoroutineContext()))
        namer.name(unregistered, id)
        placeManager.registerCheckpoint(unregistered, id, recording = true)
        assertEquals("Джубга", places.fetchPlace(repo.fetchTripDetail(id)!!.checkpoints.first { it.id == unregistered.id }.placeId!!)!!.name)
        assertTrue(places.fetchPlaces().all { places.passes(it.id).isEmpty() })
    }

    @Test fun lateGeocoderAnswerPreservesHandNamesAndDeletedPlaceTombstone() = test {
        val id = assertIs<StartResult.Started>(recorder.start()).tripId
        drive(straight(0..100))
        val cp = assertNotNull(recorder.markCheckpoint())
        val place = places.fetchPlaces().single()
        val started = CompletableDeferred<Unit>()
        val answer = CompletableDeferred<Unit>()
        coroutineScope {
            val namer = CheckpointNamer(repo, placeManager, {
                started.complete(Unit); answer.await(); Placemark("Джубга", null, null)
            }, { listOf("Отметка", "Checkpoint") }, this)
            namer.name(cp, id)
            started.await()
            repo.updateCheckpoint(cp.id, "Моё имя", null, emptyList())
            placeManager.rename(place.id, "Моё место")
            answer.complete(Unit)
        }
        assertEquals("Моё имя", repo.fetchTripDetail(id)!!.checkpoints.single().name)
        assertEquals("Моё место", places.fetchPlace(place.id)!!.name)
        placeManager.delete(place.id)
        placeManager.adoptName("Джубга", cp.id, id)
        assertNull(places.fetchPlace(place.id))
    }
}
