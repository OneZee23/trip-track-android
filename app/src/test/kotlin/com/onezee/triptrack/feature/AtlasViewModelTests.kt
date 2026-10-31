// iOS: AtlasRegionTests.swift (logic), ReloadCoalescerTests.swift, AtlasPlacesTests.swift
package com.onezee.triptrack.feature

import com.onezee.triptrack.data.*
import com.onezee.triptrack.feature.atlas.*
import com.onezee.triptrack.feature.settings.*
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Test
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AtlasViewModelTests : WorldTest() {
    private val start = GeoPoint(45.035, 38.975)
    private val end = GeoPoint(45.045, 38.985)
    private val home = MutableStateFlow(HomeState(start))
    private val permission = MutableStateFlow(AtlasLocationPermission.allowed)
    private fun vm(repository: TripRepository = repo, events: Flow<WorldChange> = emptyFlow()) = own(MyMapViewModel(repository,
        RoomTerritoryStore(db), RoomRevealedTileStore(db), places, preferences,
        object : AtlasLocationSource { override val permission = this@AtlasViewModelTests.permission }, home, dispatcher, events,
        now = { t0 }, zone = ZoneOffset.UTC))
    @Test fun backFromListReturnsToListAndCloseReturnsToSummary() = runTest(dispatcher) {
        insertTrip(preview = Trip.encodePolyline(listOf(start, end)))
        val vm = vm(); vm.loadIfNeeded(); advanceUntilIdle()
        val region = vm.state.value.exploration.regions.first()
        vm.expand(); vm.select(AtlasSelection.Region(region.id))
        assertEquals(AtlasSheetDetent.collapsed, vm.state.value.detent)
        assertEquals(1, vm.state.value.selectedRegionTrips.size)
        vm.setDetent(AtlasSheetDetent.expanded); vm.backFromSubstate()
        assertNull(vm.state.value.selection); assertEquals(AtlasSheetDetent.expanded, vm.state.value.detent)
        vm.select(AtlasSelection.Region(region.id)); vm.closeSelection()
        assertEquals(AtlasSheetDetent.collapsed, vm.state.value.detent)
    }
    @Test fun roadTapNeverMovesCameraAndTripsSortNewestFirst() = runTest(dispatcher) {
        val old = insertTrip(preview = Trip.encodePolyline(listOf(start, end)))
        val new = insertTrip(start = t0.plusSeconds(50), preview = Trip.encodePolyline(listOf(start, end)))
        val vm = vm(); vm.loadIfNeeded(); advanceUntilIdle()
        vm.selectRoad(listOf(old, new)); assertEquals(listOf(new, old), vm.state.value.selectedRoad?.map { it.id })
        assertNull(vm.state.value.camera)
        vm.selectRoad(listOf(old)); assertNotNull(vm.state.value.selectedTrip); assertNull(vm.state.value.camera)
    }
    @Test fun regionFromMapReturnsToSummaryAndMapStripCollapses() = runTest(dispatcher) {
        insertTrip(preview = Trip.encodePolyline(listOf(start, end)))
        val vm = vm(); vm.loadIfNeeded(); advanceUntilIdle()
        vm.selectRegion(start); assertNotNull(vm.state.value.selection)
        vm.backFromSubstate(); assertEquals(AtlasSheetDetent.collapsed, vm.state.value.detent)
        vm.expand(); vm.tapMap(start); assertNull(vm.state.value.selection); assertEquals(AtlasSheetDetent.collapsed, vm.state.value.detent)
    }
    @Test fun threeFinishEventsCauseOneReloadAfter250Milliseconds() = runTest(dispatcher) {
        insertTrip(preview = Trip.encodePolyline(listOf(start, end)))
        var reads = 0
        val repository = object : TripRepository by repo {
            override suspend fun fetchTripsForMap(): List<Trip> { reads++; return repo.fetchTripsForMap() }
        }
        val events = MutableSharedFlow<WorldChange>()
        val vm = vm(repository, events)
        events.emit(WorldChange.trips); runCurrent(); assertEquals(0, reads)
        vm.loadIfNeeded(); advanceUntilIdle(); assertEquals(1, reads)
        events.emit(WorldChange.trips); events.emit(WorldChange.revealed); events.emit(WorldChange.territory); runCurrent()
        assertFalse(vm.state.value.isLoading)
        advanceTimeBy(249); runCurrent(); assertEquals(1, reads)
        advanceTimeBy(1); runCurrent(); assertEquals(2, reads)
        vm.loadIfNeeded(); runCurrent(); assertEquals(2, reads)
    }
    @Test fun placeOutsidePeriodStaysMutedAndHomeAndPlaceAreExclusive() = runTest(dispatcher) {
        insertTrip(preview = Trip.encodePolyline(listOf(start, end)))
        val place = places.upsertPlace("cell", end, "Sea").place
        places.replacePasses(place.id, java.util.UUID.randomUUID(), listOf(PlacePass(placeId = place.id,
            tripId = java.util.UUID.randomUUID(), timestamp = t0.minusSeconds(90 * 86400), elapsedFromStart = 600.0, distanceFromStart = 1000.0, course = 0.0)))
        val vm = vm(); vm.loadIfNeeded(); advanceUntilIdle()
        assertEquals(1, vm.state.value.homeTripCount)
        vm.selectPlace(place.id); assertEquals(AtlasSheetDetent.peek, vm.state.value.detent)
        vm.selectHome(); assertNull(vm.state.value.selectedPlaceId); assertTrue(vm.state.value.homeSelected)
        vm.selectPlace(place.id); assertFalse(vm.state.value.homeSelected)
        vm.setPeriod(AtlasPeriod.Last30Days); advanceUntilIdle()
        assertEquals(1, vm.state.value.placePins.size); assertFalse(vm.state.value.placePins.first().inPeriod)
        home.value = HomeState(start, showsOnMap = false); runCurrent(); assertNull(vm.state.value.home.mapPin)
    }
    @Test fun appearanceLivesInViewModelAndLocationDenialFitsAtlas() = runTest(dispatcher) {
        insertTrip(preview = Trip.encodePolyline(listOf(start, end)))
        val vm = vm(); vm.loadIfNeeded(); advanceUntilIdle()
        vm.setAppearance(AtlasAppearance(AtlasMapAppearance.Style.cells, false))
        assertTrue(vm.state.value.fog.look.cells); assertNotNull(preferences.string("atlas.mapAppearance"))
        permission.value = AtlasLocationPermission.denied; runCurrent(); vm.locateUser()
        assertTrue(vm.state.value.showsLocationDialog)
        vm.dismissLocationDialog(); assertFalse(vm.state.value.showsLocationDialog)
        assertTrue(vm.state.value.camera is AtlasCameraAction.Command)
        val revision = vm.state.value.cameraRevision
        vm.acknowledgeCamera(revision - 1); assertNotNull(vm.state.value.camera)
        vm.acknowledgeCamera(revision); assertNull(vm.state.value.camera)
    }
    @Test fun discoveriesReloadWithoutRebuildingFog() = runTest(dispatcher) {
        val vm = vm(); vm.loadIfNeeded(); advanceUntilIdle()
        val before = vm.fogRebuilds
        vm.reloadDiscoveries(); advanceUntilIdle()
        assertEquals(before, vm.fogRebuilds)
        assertNotNull(vm.state.value.fog)
    }
}
