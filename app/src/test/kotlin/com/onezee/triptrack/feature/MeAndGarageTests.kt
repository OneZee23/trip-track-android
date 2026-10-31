// iOS: PreparedProfileHistoryTests.swift, ProfileMonthlyJournalTests.swift, OdometerFieldTests.swift
package com.onezee.triptrack.feature

import com.onezee.triptrack.feature.me.*
import com.onezee.triptrack.feature.garage.*
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import java.time.*
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Test
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class MeAndGarageTests : WorldTest() {
    private val identity = object : ProfileIdentitySource {
        override val identity = MutableStateFlow(ProfileIdentity())
        override suspend fun setPinnedTrips(ids: List<UUID>) { identity.value = identity.value.copy(pinnedTripIds = ids) }
    }
    private fun me() = own(MeViewModel(repo, vehicles, identity, preferences, dispatcher, locale = Locale.ENGLISH, zone = ZoneOffset.UTC, now = { t0 }))
    @Test fun sameCountSameDatesReloadReplacesContentAndHistoricalXp() = runTest(dispatcher) {
        val id = insertTrip(title = "Old title", xp = 49)
        val vm = me(); advanceUntilIdle(); assertEquals(1, vm.state.value.history.historicalLevels?.get(id))
        db.tripDao().upsertTrip(db.tripDao().trip(id)!!.copy(title = "Renamed", xpEarned = 150))
        vm.reload(); advanceUntilIdle(); vm.search("renamed"); runCurrent()
        assertEquals(id, vm.state.value.history.visibleTrips.single().id)
        assertEquals(3, vm.state.value.history.historicalLevels?.get(id))
        assertEquals("Renamed", vm.state.value.history.visibleTrips.single().title)
    }
    @Test fun historyKeepsXpThroughSearchAndCalendar() = runTest(dispatcher) {
        val old = insertTrip(start = t0.minusSeconds(10 * 86400), title = "Oldest", xp = 60)
        insertTrip(start = t0.minusSeconds(5 * 86400), title = "Middle", xp = 100)
        val new = insertTrip(title = "Target", xp = 200)
        val vm = me(); advanceUntilIdle(); vm.search("target"); vm.setCalendarRange(t0, null); runCurrent()
        assertEquals(listOf(new), vm.state.value.history.visibleTrips.map { it.id })
        assertEquals(4, vm.state.value.history.historicalLevels?.get(new))
        assertEquals(2, vm.state.value.history.historicalLevels?.get(old))
        assertEquals(3, vm.state.value.calendar.kmByDay.size)
        assertEquals(1.0, vm.state.value.calendar.maxKmDay)
    }
    @Test fun pinsAreUnduplicatedAndUnknownPhonePinsSurvive() = runTest(dispatcher) {
        val missing = UUID.randomUUID()
        val known = insertTrip(title = "Public", isPrivate = false)
        val private = insertTrip(title = "Private")
        identity.identity.value = ProfileIdentity(pinnedTripIds = listOf(known, missing, private))
        val vm = me(); advanceUntilIdle()
        assertEquals(listOf(known, missing), identity.identity.value.pinnedTripIds)
        assertEquals(listOf(known), vm.state.value.history.pinned.map { it.id })
        assertFalse(vm.state.value.history.rows.any { it.id == known })
        assertEquals(listOf(missing), vm.state.value.showcaseElsewhere)
    }
    @Test fun routineHasSevenVisibleTripsAndSearchUnfoldsIt() = runTest(dispatcher) {
        val a = GeohashEncoder.centerCoordinate("ucftjk"); val b = GeohashEncoder.centerCoordinate("ucftm1")
        repeat(8) { insertTrip(start = t0.minusSeconds(it * 86400L), preview = Trip.encodePolyline(listOf(a, b)), title = "Work") }
        val vm = me(); advanceUntilIdle()
        val routine = (vm.state.value.history.rows.single() as HistoryRow.Routine).value
        assertEquals(7, vm.visibleRoutineTrips(routine).size)
        vm.expandRoutine(routine.id); assertEquals(8, vm.visibleRoutineTrips(routine).size)
        assertEquals(8, vm.state.value.history.gridRuns.flatten().size)
        vm.search("Work"); runCurrent(); assertEquals(8, vm.state.value.history.rows.size)
    }
    @Test fun heroSceneIsHiddenByPhotoOrBackgroundAndChangesOnForeground() = runTest(dispatcher) {
        val vm = me(); advanceUntilIdle(); assertTrue(vm.state.value.hero.showsRoad)
        identity.identity.value = ProfileIdentity(coverPhotoIds = listOf(UUID.randomUUID())); runCurrent()
        assertFalse(vm.state.value.hero.showsRoad)
        identity.identity.value = ProfileIdentity(effectiveBackground = ProfileBackground.ocean); runCurrent()
        assertFalse(vm.state.value.hero.showsRoad)
        vm.appearedOrBecameActive(); assertEquals(RoadSceneTime.slot(t0, ZoneOffset.UTC), vm.state.value.hero.roadTime)
    }
    @Test fun metricToyotaUsesDashboardUnitInMileAppAndCancelWritesNothing() = runTest(dispatcher) {
        val id = vehicles.addVehicle("Toyota", "🚗")
        vehicles.setDashboardUnits(id, DashboardUnits.metric)
        val vm = own(VehicleEditViewModel(vehicles.vehicle(id), vehicles, DistanceUnit.miles, dispatcher))
        vm.setOdometer("142000"); vm.save(); advanceUntilIdle()
        assertEquals(142000.0, vehicles.vehicle(id)?.manualOdometerKm)
    }
    @Test fun dashboardPickerConvertsFieldAndNameOnlySaveDoesNotRoundOdometer() = runTest(dispatcher) {
        val id = vehicles.addVehicle("Mustang", "🚗")
        vehicles.setManualOdometer(id, 100000.0); vehicles.setDashboardUnits(id, DashboardUnits.imperial)
        val vm = own(VehicleEditViewModel(vehicles.vehicle(id), vehicles, DistanceUnit.km, dispatcher))
        assertEquals("62137", vm.state.value.odometerText)
        vm.setName("Renamed"); vm.save(); advanceUntilIdle()
        assertEquals(100000.0, vehicles.vehicle(id)?.manualOdometerKm)
    }
    @Test fun pickerChoicesAreLocalUntilSavedAndUnknownEngineClearsOldVariant() = runTest(dispatcher) {
        val id = vehicles.addVehicle("Polo", "🚗")
        vehicles.updateVehicleGarage(id, catalogModelId = "old", catalogGenerationId = "old", catalogVariantId = "old")
        val vm = own(VehicleEditViewModel(vehicles.vehicle(id), vehicles, DistanceUnit.km, dispatcher))
        vm.setDashboardUnits(DashboardUnits.imperial)
        vm.selectCatalog(VehicleCatalogSelection("Volkswagen", "Polo", "car", 2018, "volkswagen-polo", "volkswagen-polo-sedan-2015", null))
        assertEquals(DashboardUnits.app, vehicles.vehicle(id)?.dashboardUnits)
        vm.save(); advanceUntilIdle()
        assertEquals("", vehicles.entity(id)?.catalogVariantId)
        assertEquals(2018, vehicles.vehicle(id)?.year)
    }
    @Test fun passportUsesYearDistanceAndOnlyVehicleTrips() = runTest(dispatcher) {
        val id = vehicles.addVehicle("Polo", "🚗")
        insertTrip(vehicleId = id)
        insertTrip(vehicleId = id, start = t0.minusSeconds(400 * 86400))
        insertTrip()
        val vm = own(VehiclePassportViewModel(id, vehicles, photos, repo, com.onezee.triptrack.logic.CarCatalog(), dispatcher, MutableStateFlow(DistanceUnit.km)))
        advanceUntilIdle()
        assertEquals(2, vm.state.value.passport.tripCount)
        assertEquals(listOf(2024, 2025), vm.state.value.passport.years.map { it.year })
        assertEquals(2.0, vm.state.value.passport.years.sumOf { it.km })
        assertFalse(vm.setDrive2("https://example.com/r/test/model/123/"))
        assertTrue(vm.setDrive2("https://m.drive2.ru/r/vw/polo/123/?x=1")); advanceUntilIdle()
        assertEquals("https://www.drive2.ru/r/vw/polo/123/", vehicles.vehicle(id)?.drive2URL)
    }
}
