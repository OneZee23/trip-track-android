// iOS: VehicleOdometerTests.swift, VehicleAssignmentTests.swift (store half), ManualTripRewardsTests.swift
// (the reward/odometer split), VehicleStateTests.swift (recordable rule)
package com.onezee.triptrack.data

import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.data.db.entity.VehicleEntity
import com.onezee.triptrack.model.TripOrigin
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The odometer is DERIVED from the trips on the car: moving, deleting and restoring trips are
 * correct by construction. A real user moved a trip to another car and the kilometres stayed.
 */
class VehicleOdometerTests : RoomTestCase() {
    private suspend fun makeVehicle(odometerKm: Double = 0.0): UUID {
        val v = VehicleEntity(id = UUID.randomUUID(), name = "Car", odometerKm = odometerKm, vehicleLevel = 1)
        db.vehicleDao().upsertVehicle(v)
        return v.id
    }

    private suspend fun makeTrip(km: Double, vehicle: UUID?, ended: Boolean = true, source: TripOrigin = TripOrigin.recorded, transfer: Boolean = false): UUID =
        insert(
            TripEntity(
                id = UUID.randomUUID(), startDate = Instant.now(), endDate = if (ended) Instant.now() else null,
                distance = km * 1_000, vehicleId = vehicle, syncStatus = synced, source = source.rawValue, isTransfer = transfer,
            ),
        )

    private suspend fun odometer(id: UUID): Double = assertNotNull(db.vehicleDao().vehicle(id)).odometerKm

    @Test
    fun testMovingATripToAnotherCarMovesItsKilometres() = test {
        val a = makeVehicle()
        val b = makeVehicle()
        val trip = makeTrip(300.0, a)
        repo.recomputeOdometers(listOf(a, b))
        assertEquals(300.0, odometer(a), 0.001)
        assertEquals(0.0, odometer(b), 0.001)

        repo.updateVehicle(trip, b)

        assertEquals(0.0, odometer(a), 0.001, "kilometres must leave the old car")
        assertEquals(300.0, odometer(b), 0.001, "and arrive at the new one")
    }

    @Test
    fun testClearingTheVehicleTakesTheKilometresAway() = test {
        val a = makeVehicle()
        val trip = makeTrip(120.0, a)
        repo.recomputeOdometers(listOf(a))
        assertEquals(120.0, odometer(a), 0.001)

        repo.updateVehicle(trip, null)

        assertEquals(0.0, odometer(a), 0.001)
    }

    /** Level 2 starts at 100 km, level 3 at 300. */
    @Test
    fun testLevelFollowsTheRecomputedOdometer() = test {
        val a = makeVehicle()
        makeTrip(250.0, a)

        repo.recomputeOdometers(listOf(a))

        assertEquals(250.0, odometer(a), 0.001)
        assertEquals(VehicleLevelSystem.level(250.0), db.vehicleDao().vehicle(a)?.vehicleLevel)
        assertEquals(2, VehicleLevelSystem.level(250.0))
        assertEquals(2, VehicleLevelSystem.level(100.0), "the round threshold is reached, not missed by a hair")
        assertEquals(3, VehicleLevelSystem.level(300.0))
        assertEquals(1, VehicleLevelSystem.level(0.0))
    }

    @Test
    fun testDeletingATripSubtractsItsKilometres() = test {
        val a = makeVehicle()
        val keep = makeTrip(100.0, a)
        val drop = makeTrip(400.0, a)
        update(drop) { it.copy(serverCreatedAt = Instant.now()) } // forces the soft-delete path
        repo.recomputeOdometers(listOf(a))
        assertEquals(500.0, odometer(a), 0.001)

        repo.deleteTrip(drop)

        assertEquals(100.0, odometer(a), 0.001)
        assertNotNull(repo.fetchEntity(keep))
    }

    @Test
    fun testGlobalRecomputeFixesDriftedOdometers() = test {
        val a = makeVehicle(341.0)
        val b = makeVehicle(301.0)
        makeTrip(1_000.0, a)
        makeTrip(500.0, a)
        makeTrip(800.0, b)

        repo.recomputeAllVehicleOdometers()

        assertEquals(1_500.0, odometer(a), 0.001)
        assertEquals(800.0, odometer(b), 0.001)
    }

    /** With no library the odometers are the only surviving record: zeroing them is the bug itself. */
    @Test
    fun testGlobalRecomputeDoesNothingWhenThereAreNoTrips() = test {
        val a = makeVehicle(341.0)
        repo.recomputeAllVehicleOdometers()
        assertEquals(341.0, odometer(a), 0.001)
    }

    @Test
    fun testACarWithNoTripsReadsZeroOnceTheLibraryExists() = test {
        val driven = makeVehicle(0.0)
        val idle = makeVehicle(999.0)
        makeTrip(200.0, driven)

        repo.recomputeAllVehicleOdometers()

        assertEquals(200.0, odometer(driven), 0.001)
        assertEquals(0.0, odometer(idle), 0.001)
    }

    @Test
    fun testAnInProgressRecordingIsNotCounted() = test {
        val a = makeVehicle()
        makeTrip(100.0, a)
        makeTrip(50.0, a, ended = false)

        repo.recomputeAllVehicleOdometers()

        assertEquals(100.0, odometer(a), 0.001)
    }

    /** A passenger's trip does not wind up the car (the same rule as VehicleOdometer). */
    @Test
    fun testTransferKilometresStayOutOfTheCarOdometer() = test {
        val car = makeVehicle()
        makeTrip(100.0, car)
        makeTrip(400.0, car, transfer = true)
        repo.recomputeOdometers(listOf(car))
        assertEquals(100.0, odometer(car), 0.01, "passenger kilometres do not wind up someone's car")
    }

    /** Marking a trip a transfer takes the car off it and its kilometres off the odometer. */
    @Test
    fun testUpdateTransferTakesTheCarOffTheTrip() = test {
        val car = makeVehicle()
        val tripId = makeTrip(150.0, car)
        repo.recomputeOdometers(listOf(car))

        repo.updateTransfer(tripId, true)

        assertNull(trip(tripId)?.vehicleId)
        assertEquals(true, trip(tripId)?.isTransfer)
        assertEquals(0.0, odometer(car), 0.001)
    }

    /** Odometer and level come from DIFFERENT sums: a drawn route is distance, not a reward. */
    @Test
    fun testManualTripCountsForTheOdometerButNotForTheLevel() = test {
        val car = makeVehicle()
        makeTrip(50.0, car)
        makeTrip(9_000.0, car, source = TripOrigin.manual)

        repo.recomputeOdometers(listOf(car))

        assertEquals(9_050.0, odometer(car), 0.001)
        assertEquals(VehicleLevelSystem.level(50.0), db.vehicleDao().vehicle(car)?.vehicleLevel)
        assertEquals(mapOf(car to 50.0), repo.rewardKmByVehicle())
        assertEquals(9_000.0, repo.unrewardedKm(car), 0.001)
    }

    // MARK: VehicleAssignmentTests

    private suspend fun insertTrip(isPrivate: Boolean = true, vehicleId: UUID? = null): UUID = insert(
        TripEntity(
            id = UUID.randomUUID(), userId = localUserId, startDate = Instant.now(), endDate = Instant.now().plusSeconds(600),
            isPrivate = isPrivate, syncStatus = synced, vehicleId = vehicleId, lastModifiedAt = Instant.EPOCH,
        ),
    )

    @Test
    fun testUpdateVehicleAssignsAndClears() = test {
        val id = insertTrip()
        val car = UUID.randomUUID()
        repo.updateVehicle(id, car)
        assertEquals(car, repo.fetchTripDetail(id)?.vehicleId)
        repo.updateVehicle(id, null)
        assertNull(repo.fetchTripDetail(id)?.vehicleId)
    }

    @Test
    fun testUpdateVehicleAdvancesLastModified() = test {
        val id = insertTrip()
        repo.updateVehicle(id, UUID.randomUUID())
        assertTrue(assertNotNull(trip(id)?.lastModifiedAt) > Instant.EPOCH)
    }

    @Test
    fun testUpdateVehiclePublicTripFlipsPendingUpload() = test {
        val id = insertTrip(isPrivate = false)
        repo.updateVehicle(id, UUID.randomUUID())
        assertEquals(pendingUpload, trip(id)?.syncStatus)
    }

    @Test
    fun testUpdateVehiclePrivateTripSyncOffDoesNotFlipPendingUpload() = test {
        val id = insertTrip(isPrivate = true)
        repo.updateVehicle(id, UUID.randomUUID())
        assertEquals(synced, trip(id)?.syncStatus)
    }

    // MARK: VehicleStateTests: only a sold (or still archived) car refuses a NEW trip

    @Test
    fun testOnlyAReadyCarTakesANewTrip() = test {
        val ready = makeVehicle()
        val sold = makeVehicle()
        val archived = makeVehicle()
        db.vehicleDao().upsertVehicle(db.vehicleDao().vehicle(sold)!!.copy(soldAt = Instant.now()))
        db.vehicleDao().upsertVehicle(db.vehicleDao().vehicle(archived)!!.copy(isArchived = true))

        assertEquals(ready, repo.recordableVehicleId(ready))
        assertNull(repo.recordableVehicleId(sold))
        assertNull(repo.recordableVehicleId(archived))
        assertNull(repo.recordableVehicleId(UUID.randomUUID()))
        assertNull(repo.recordableVehicleId(null), "no vehicle is a choice, not a failure")
    }
}
