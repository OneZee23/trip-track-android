// iOS: PlacesTabViewModelTests.swift, PlaceDetailViewModelTests.swift
package com.onezee.triptrack.feature

import com.onezee.triptrack.feature.places.*
import com.onezee.triptrack.feature.settings.WorldChange
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import org.junit.Test
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PlacesTabViewModelTests : WorldTest() {
    private val home = GeoPoint(45.035, 38.975)
    private val sea = GeoPoint(44.561, 38.077)
    private fun vm(events: MutableSharedFlow<WorldChange> = MutableSharedFlow()) = own(PlacesTabViewModel(places, repo, preferences,
        PlaceCreator { places.upsertPlace(it.cell, it.coordinate, it.name) }, dispatcher, events, Locale.ENGLISH, ZoneOffset.UTC, { t0 }))
    @Test fun suggestionsStartNullAndDoNotFlashOnRefresh() = runTest(dispatcher) {
        insertTrip(preview = Trip.encodePolyline(listOf(home, sea)))
        val vm = vm()
        assertNull(vm.state.value.suggestions)
        runCurrent()
        assertNull(vm.state.value.suggestions)
        advanceTimeBy(300); runCurrent()
        assertEquals(setOf(Place.cell(home.latitude, home.longitude), Place.cell(sea.latitude, sea.longitude)), vm.state.value.knownSuggestions.map { it.cell }.toSet())
        vm.reload(); runCurrent()
        assertNotNull(vm.state.value.suggestions)
    }
    @Test fun noSuggestionsIsEmptyInsteadOfNull() = runTest(dispatcher) {
        val vm = vm(); advanceUntilIdle()
        assertEquals(emptyList(), vm.state.value.suggestions)
        assertFalse(vm.state.value.isComputingSuggestions)
    }
    @Test fun initialSnapshotRetainsEmptyPlacesAndLatestComparison() = runTest(dispatcher) {
        suspend fun place(index: Int, name: String) = places.upsertPlace("cell-$index", GeoPoint(44.0 + index * .1, 38.0), name).place
        val older = place(0, "Older"); val latest = place(1, "Latest"); val zulu = place(2, "Zulu"); val alpha = place(3, "Alpha")
        fun pass(place: Place, offset: Long, elapsed: Double) = PlacePass(placeId = place.id, tripId = UUID.randomUUID(),
            timestamp = t0.plusSeconds(offset), elapsedFromStart = elapsed, distanceFromStart = 10_000.0, course = 0.0)
        places.replacePasses(older.id, UUID.randomUUID(), listOf(pass(older, 20, 1200.0)))
        places.replacePasses(latest.id, UUID.randomUUID(), listOf(pass(latest, 30, 1800.0), pass(latest, 50, 600.0), pass(latest, 40, 1800.0)))
        val vm = vm(); runCurrent()
        val state = vm.state.value
        assertEquals(listOf(latest.id, older.id, alpha.id, zulu.id), state.items.map { it.place.id })
        assertEquals(3, state.items.first().stats.passCount)
        assertEquals(1800.0, state.items.first().usual)
        assertEquals(0, state.items.last().stats.passCount)
        assertEquals(latest.id, state.lastPass?.placeId); assertEquals(1200.0, state.lastPass?.delta)
        vm.markLastPassSeen(); vm.reload(); runCurrent(); assertNull(vm.state.value.lastPass)
    }
    @Test fun deletionNotificationRefreshesMountedList() = runTest(dispatcher) {
        val place = places.upsertPlace("cell", home, "Джубга").place
        val events = MutableSharedFlow<WorldChange>()
        val vm = vm(events); runCurrent(); assertEquals(1, vm.state.value.items.size)
        places.deletePlace(place.id); events.emit(WorldChange.places); runCurrent()
        assertTrue(vm.state.value.items.isEmpty())
    }
    @Test fun existingPlacesAndCheckpointTombstonesAreNotSuggested() = runTest(dispatcher) {
        insertTrip(preview = Trip.encodePolyline(listOf(home, sea)))
        places.upsertPlace(Place.cell(home.latitude, home.longitude), home, "Дом")
        val vm = vm(); advanceUntilIdle()
        assertFalse(vm.state.value.knownSuggestions.any { it.cell == Place.cell(home.latitude, home.longitude) })
    }
    @Test fun saveDropsSuggestionImmediatelyAndCreatesPlace() = runTest(dispatcher) {
        insertTrip(preview = Trip.encodePolyline(listOf(home, sea)))
        val vm = vm(); advanceUntilIdle()
        val first = vm.state.value.knownSuggestions.first()
        vm.save(first)
        assertFalse(vm.state.value.knownSuggestions.any { it.id == first.id })
        advanceUntilIdle()
        assertEquals(first.id, places.fetchPlaces().single().id)
    }
    @Test fun lastTripIsNewestAndOnlyThreeRecentTripsAreShown() = runTest(dispatcher) {
        repeat(4) { insertTrip(start = t0.plusSeconds(it.toLong()), preview = Trip.encodePolyline(listOf(home, sea))) }
        val latest = insertTrip(start = t0.plusSeconds(8), preview = Trip.encodePolyline(listOf(home, sea)))
        val vm = vm(); advanceUntilIdle()
        assertEquals(latest, vm.state.value.lastTripId)
        assertEquals(3, vm.state.value.recentTrips.size)
    }
    @Test fun searchIsModalAndDoesNotReplaceLibraryGroups() = runTest(dispatcher) {
        places.upsertPlace("home", home, "Café")
        places.upsertPlace("sea", sea, "Sea")
        val vm = vm(); advanceUntilIdle()
        vm.openSearch(); vm.search("CAFE")
        assertEquals("Café", vm.state.value.search?.results?.single()?.place?.name)
        assertEquals(2, vm.state.value.items.size)
        vm.dismissSearch(); assertNull(vm.state.value.search)
    }
    @Test fun detailLoadsStatsAndRoutesFromPreviewOnly() = runTest(dispatcher) {
        val today = insertTrip(preview = Trip.encodePolyline(listOf(home, sea)))
        val driveBy = insertTrip(start = t0.plusSeconds(7200))
        val place = places.upsertPlace("cell", home, null).place
        val all = listOf(PlacePass(placeId = place.id, tripId = today, timestamp = t0, elapsedFromStart = 300.0, distanceFromStart = 1500.0, course = 0.0),
            PlacePass(placeId = place.id, tripId = driveBy, timestamp = t0.plusSeconds(7200), elapsedFromStart = 300.0, distanceFromStart = 1500.0, course = 180.0))
        places.replacePasses(place.id, today, all.take(1)); places.replacePasses(place.id, driveBy, all.drop(1))
        val vm = own(PlaceDetailViewModel(place.id, places, repo, dispatcher, zone = ZoneOffset.UTC, localityLookup = { "к морю" }))
        vm.load(); runCurrent()
        assertEquals(2, vm.state.value.stats.passCount); assertEquals(driveBy, vm.state.value.passes.first().tripId)
        assertEquals(1, vm.state.value.routes.size)
        assertEquals("к морю", vm.state.value.directionLabels[today])
        assertEquals("к морю", vm.state.value.routeNames[today])
        assertEquals(1, vm.state.value.tiles.size)
        assertEquals(PlaceTripFocus.Top, vm.focus(driveBy))
        vm.rename("Джубга"); runCurrent(); assertEquals("Джубга", vm.state.value.place?.name)
        vm.delete(); assertNull(vm.state.value.place); runCurrent(); assertNull(places.fetchPlace(place.id))
    }
}
