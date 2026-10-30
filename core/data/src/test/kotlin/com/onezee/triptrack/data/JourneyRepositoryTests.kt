// iOS: JourneyRepositoryTests.swift, JourneySyncConflictTests.swift (store half), JourneySyncRecoveryTests.swift
package com.onezee.triptrack.data

import com.onezee.triptrack.data.db.entity.JourneyEntity
import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.data.sync.SyncAction
import com.onezee.triptrack.data.sync.SyncEntityType
import com.onezee.triptrack.data.sync.SyncRequest
import com.onezee.triptrack.model.Journey
import com.onezee.triptrack.model.JourneySyncPayload
import com.onezee.triptrack.model.SyncStatus
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** A journey is a date window: its legs are computed, not stored. */
class JourneyRepositoryTests : RoomTestCase() {
    private val t0 = Instant.ofEpochSecond(1_760_000_000)
    private val day = 86_400L

    private suspend fun makeTrip(daysFromT0: Long, km: Double = 100.0): UUID {
        val start = t0.plusSeconds(daysFromT0 * day)
        return insert(TripEntity(id = UUID.randomUUID(), startDate = start, endDate = start.plusSeconds(3_600), distance = km * 1_000, isPrivate = true))
    }

    private fun remote(journey: Journey) = JourneySyncPayload(
        id = journey.id, title = journey.title, startDate = journey.startDate, endDate = journey.endDate,
        excludedTripIds = journey.excludedTripIds, coverPhotoId = journey.coverPhotoId, isPrivate = journey.isPrivate,
        conflictVersion = journey.conflictVersion, lastModifiedAt = journey.lastModifiedAt, serverCreatedAt = journey.serverCreatedAt,
    )

    @Test
    fun testTripsInsideTheWindowAreTheLegs() = test {
        val a = makeTrip(0)
        val b = makeTrip(1)
        makeTrip(9)
        val j = repo.saveJourney(Journey(startDate = t0, endDate = t0.plusSeconds(3 * day)))
        assertEquals(setOf(a, b), repo.trips(j).map { it.id }.toSet())
        assertEquals(listOf(j.id), repo.fetchJourneys().map { it.id })
    }

    /** The DATABASE cuts the window, exactly at its edges: inclusive by start, ascending. */
    @Test
    fun testFetchTripsInWindowKeepsTheBoundariesAndDropsTheNeighbours() = test {
        val ids = (0L until 10L).map { makeTrip(it) }

        val window = repo.fetchTrips(t0.plusSeconds(3 * day), t0.plusSeconds(5 * day))

        assertEquals(listOf(ids[3], ids[4], ids[5]), window.map { it.id }, "three days of the window, inclusive, by start")
    }

    @Test
    fun testExcludedTripLeavesTheJourney() = test {
        val a = makeTrip(0)
        val b = makeTrip(1)
        var j = repo.saveJourney(Journey(startDate = t0, endDate = t0.plusSeconds(2 * day)))
        j = repo.saveJourney(j.copy(excludedTripIds = listOf(b)))
        assertEquals(listOf(a), repo.trips(j).map { it.id })
        assertEquals(j.id, repo.journeyContaining(a)?.id)
        assertNull(repo.journeyContaining(b))
    }

    /** An open window has no upper bound: it takes every later trip. */
    @Test
    fun testAnOpenWindowTakesEverythingAfterItsStart() = test {
        makeTrip(-1)
        val a = makeTrip(0)
        val b = makeTrip(30)
        val j = repo.saveJourney(Journey(startDate = t0, endDate = null))
        assertEquals(listOf(a, b), repo.trips(j).map { it.id })
    }

    @Test
    fun testOverlappingWindowIsDetected() = test {
        val j = repo.saveJourney(Journey(startDate = t0, endDate = t0.plusSeconds(2 * day)))
        assertEquals(j.id, repo.journeyOverlapping(t0.plusSeconds(day), t0.plusSeconds(5 * day), null)?.id)
        assertNull(repo.journeyOverlapping(t0.plusSeconds(3 * day), t0.plusSeconds(5 * day), null))
        assertNull(repo.journeyOverlapping(t0, t0.plusSeconds(day), j.id))
    }

    @Test
    fun testSavingFlipsPendingUploadAndSoftDeleteHidesIt() = test {
        val j = repo.saveJourney(Journey(startDate = t0, endDate = t0))
        assertEquals(pendingUpload, repo.journeySyncStatus(j.id))
        assertEquals(localUserId, db.journeyDao().journey(j.id)?.userId, "a new journey is stamped with this install")
        repo.markJourneyDeleted(j.id)
        assertTrue(repo.fetchJourneys().isEmpty(), "pendingDelete hides from the UI")
        repo.deleteJourneyHard(j.id)
        assertNull(repo.fetchJourney(j.id))
    }

    /** A journey arriving from the server for the first time lands `synced`, or the queue would send it back. */
    @Test
    fun testApplyRemoteJourneyLandsAsSynced() = test {
        val id = UUID.randomUUID()
        repo.applyRemoteJourney(remote(Journey(id = id, title = "Юг", startDate = t0, endDate = t0.plusSeconds(2 * day))))
        assertEquals(synced, repo.journeySyncStatus(id))
        assertEquals("Юг", repo.fetchJourney(id)?.title)
    }

    @Test
    fun testApplyRemoteJourneyDoesNotOverwritePendingUpload() = test {
        val local = repo.saveJourney(Journey(title = "Местное имя", startDate = t0, endDate = t0))

        repo.applyRemoteJourney(remote(Journey(id = local.id, title = "Серверное имя", startDate = t0, endDate = t0)))

        assertEquals(pendingUpload, repo.journeySyncStatus(local.id))
        assertEquals("Местное имя", repo.fetchJourney(local.id)?.title)
    }

    // MARK: JourneySyncConflictTests: the store half of «a conflict must not lock the journey»

    /**
     * The transport resolves a conflict by clearing the pending flag (markJourneySynced) and then
     * applying the server copy. Before 0.6.6 both doors were shut at once and the row neither left
     * nor updated, ever.
     */
    @Test
    fun testConflictAdoptsServerVersionAndUnlocksTheRow() = test {
        val local = repo.saveJourney(Journey(title = "Местное имя", startDate = t0, endDate = t0.plusSeconds(2 * day)))
        val server = Journey(
            id = local.id, title = "Серверное имя", startDate = t0, endDate = t0.plusSeconds(5 * day),
            conflictVersion = 7, lastModifiedAt = t0.plusSeconds(day), serverCreatedAt = t0,
        )

        repo.applyRemoteJourney(remote(server))
        assertEquals("Местное имя", repo.fetchJourney(local.id)?.title, "a pull alone never beats the unsent edit")

        repo.markJourneySynced(local.id, 7)
        repo.applyRemoteJourney(remote(server))

        assertEquals(synced, repo.journeySyncStatus(local.id))
        val after = assertNotNull(repo.fetchJourney(local.id))
        assertEquals("Серверное имя", after.title, "the server wins")
        assertEquals(7, after.conflictVersion)
        assertEquals(t0.plusSeconds(5 * day), after.endDate, "the window is the server's too")
    }

    @Test
    fun testAcceptedUploadStillMarksSynced() = test {
        val local = repo.saveJourney(Journey(title = "Местное имя", startDate = t0, endDate = t0))
        repo.markJourneySynced(local.id, 3)
        assertEquals(synced, repo.journeySyncStatus(local.id))
        assertEquals(3, repo.fetchJourney(local.id)?.conflictVersion)
        assertEquals("Местное имя", repo.fetchJourney(local.id)?.title)
        assertNotNull(db.journeyDao().journey(local.id)?.serverCreatedAt)
    }

    /** A deletion the server has not confirmed must not come back as `synced` from the same exchange. */
    @Test
    fun testAPullDoesNotResurrectAJourneyWaitingForItsDelete() = test {
        val local = repo.saveJourney(Journey(title = "Юг", startDate = t0, endDate = t0))
        repo.markJourneyDeleted(local.id)

        repo.applyRemoteJourney(remote(local.copy(conflictVersion = 9)))

        assertEquals(pendingDelete, repo.journeySyncStatus(local.id))
        assertTrue(repo.fetchJourneys().isEmpty())
    }

    @Test
    fun testDeleteOfAJourneyTheServerDoesNotKnowFinishesLocally() = test {
        val local = repo.saveJourney(Journey(title = "В самолёте", startDate = t0, endDate = t0))
        repo.markJourneyDeleted(local.id)
        assertEquals(pendingDelete, repo.journeySyncStatus(local.id))

        repo.deleteJourneyHard(local.id)

        assertNull(repo.fetchJourney(local.id), "the row has to go completely")
        assertNull(repo.journeySyncStatus(local.id))
    }

    // MARK: JourneySyncRecoveryTests

    private suspend fun journeyRow(status: SyncStatus, userId: UUID = localUserId): UUID {
        val id = UUID.randomUUID()
        db.journeyDao().upsert(JourneyEntity(id = id, startDate = t0, endDate = t0.plusSeconds(3 * day), userId = userId, syncStatus = status.rawValue))
        return id
    }

    private suspend fun recovered(): List<SyncRequest> = PendingSyncRecovery(db).pendingJourneyOperations(localUserId)

    @Test
    fun testPendingUploadComesBackAsAnUpdate() = test {
        val id = journeyRow(SyncStatus.pendingUpload)
        assertEquals(listOf(SyncRequest(SyncEntityType.journey, id, SyncAction.update)), recovered())
    }

    @Test
    fun testPendingDeleteComesBackAsADelete() = test {
        val id = journeyRow(SyncStatus.pendingDelete)
        assertEquals(listOf(SyncRequest(SyncEntityType.journey, id, SyncAction.delete)), recovered())
    }

    @Test
    fun testSyncedJourneyGoesNowhere() = test {
        journeyRow(SyncStatus.synced)
        assertTrue(recovered().isEmpty())
    }

    @Test
    fun testAnotherAccountsJourneyIsNotPickedUp() = test {
        journeyRow(SyncStatus.pendingUpload, userId = UUID.randomUUID())
        assertTrue(recovered().isEmpty())
    }

    @Test
    fun testEachPendingRowYieldsExactlyOneOperation() = test {
        val a = journeyRow(SyncStatus.pendingUpload)
        val b = journeyRow(SyncStatus.pendingDelete)
        journeyRow(SyncStatus.synced)
        val ops = recovered()
        assertEquals(2, ops.size)
        assertEquals(setOf(a, b), ops.map { it.entityId }.toSet())
    }
}
