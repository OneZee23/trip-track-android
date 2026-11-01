// iOS: PlaceManagerTests.swift, TripDetailView.reloadPlaceChips/reloadSegmentHistory, TripFocus.checkpoint.
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.onezee.triptrack.feature.trip

import com.onezee.triptrack.data.PendingPlaceHistory
import com.onezee.triptrack.data.PlaceManager
import com.onezee.triptrack.data.RoomPlaceStore
import com.onezee.triptrack.feature.places.PlaceDetailViewModel
import com.onezee.triptrack.model.TripFocus
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import java.util.UUID
import kotlinx.coroutines.test.*
import org.junit.Test
import kotlin.test.*

class TripPlacesTests : PresentationTestCase() {
    private class History : PendingPlaceHistory { override var ids = emptyList<UUID>() }

    @Test fun addingCheckpointOnTripScreenRegistersItAndUsesRecordingDeferral() = runTest(dispatcher) {
        val trip = insert()
        val store = RoomPlaceStore(db)
        val history = History()
        val manager = PlaceManager(repository, store, history, scope, dispatcher, dispatcher)
        val vm = own(TripDetailViewModel(trip.id, repository, api, dispatcher, { owner }, places = store,
            placeManager = manager, recording = { true }))
        vm.load().join()
        val points = listOf(TrackPoint(latitude = 44.30, longitude = 38.70, speed = 5.0, timestamp = t0),
            TrackPoint(latitude = 44.31, longitude = 38.70, speed = 5.0, timestamp = t0.plusSeconds(100)))
        val fix = assertNotNull(TripRouteLocator.fix(t0.plusSeconds(50), points, t0))
        vm.addCheckpoint(fix)!!.join()
        vm.load().join()
        val place = store.fetchPlaces().single()
        assertEquals(place.id, repository.fetchTripDetail(trip.id)!!.checkpoints.single().placeId)
        assertEquals(listOf(place.id), history.ids)
        assertTrue(store.passes(place.id).isEmpty())
        assertEquals(place.id, vm.state.value.placeChips.values.single().placeId)
    }

    @Test fun onlyLivePlacesGetChipsAndSegmentHistoryUsesBothPlacesPasses() = runTest(dispatcher) {
        val trip = insert()
        val other = insert(day = 1)
        val store = RoomPlaceStore(db)
        val fromPlace = store.upsertPlace("from", GeoPoint(44.30, 38.70), null).place
        val toPlace = store.upsertPlace("to", GeoPoint(44.31, 38.70), null).place
        suspend fun checkpoint(place: Place, elapsed: Double): TripCheckpoint {
            val cp = TripCheckpoint(timestamp = t0.plusSeconds(elapsed.toLong()), latitude = place.latitude,
                longitude = place.longitude, elapsedFromStart = elapsed, distanceFromStart = elapsed * 20)
            repository.addCheckpoint(cp, trip.id)
            repository.setPlaceId(cp.id, place.id)
            return cp
        }
        val from = checkpoint(fromPlace, 1000.0)
        val to = checkpoint(toPlace, 1300.0)
        repository.addSegment(trip.id, from.id, to.id)
        for ((id, delta) in listOf(trip.id to 300.0, other.id to 600.0)) {
            fun pass(place: Place, elapsed: Double) = PlacePass(placeId = place.id, tripId = id,
                timestamp = t0.plusSeconds(elapsed.toLong()), elapsedFromStart = elapsed, distanceFromStart = elapsed * 20, course = 0.0)
            store.replacePasses(fromPlace.id, id, listOf(pass(fromPlace, 1000.0)))
            store.replacePasses(toPlace.id, id, listOf(pass(toPlace, 1000.0 + delta)))
        }
        val vm = own(TripDetailViewModel(trip.id, repository, api, dispatcher, { owner }, places = store))
        vm.load().join()
        assertEquals(setOf(from.id, to.id), vm.state.value.placeChips.keys)
        assertEquals(listOf(300.0, 600.0), vm.state.value.segments.single().history)
        val placeDetail = own(PlaceDetailViewModel(fromPlace.id, store, repository, dispatcher))
        assertEquals(TripFocus.Checkpoint(from.id), placeDetail.focus(trip.id))
        assertEquals(TripFocus.Top, placeDetail.focus(other.id))
        store.deletePlace(fromPlace.id)
        vm.load().join()
        assertEquals(setOf(to.id), vm.state.value.placeChips.keys)
        assertTrue(vm.state.value.segments.single().history.isEmpty())
    }
}
