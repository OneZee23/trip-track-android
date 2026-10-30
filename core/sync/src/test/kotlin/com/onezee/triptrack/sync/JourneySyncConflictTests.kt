package com.onezee.triptrack.sync

import com.onezee.triptrack.data.sync.*
import com.onezee.triptrack.model.*
import com.onezee.triptrack.network.*
import java.util.UUID
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Test

// iOS: JourneySyncConflictTests.swift, unchanged time/window/version cases over Room + HTTP.
class JourneySyncConflictTests : SyncTestCase() {
    private suspend fun local() = repo.saveJourney(Journey(title = "Местное имя", startDate = t0, endDate = t0.plusSeconds(2 * 86_400)))
    private fun serverJourney(id: UUID) = JourneySyncPayload(id, "Серверное имя", t0, t0.plusSeconds(5 * 86_400), emptyList(),
        isPrivate = true, conflictVersion = 7, lastModifiedAt = t0.plusSeconds(86_400), serverCreatedAt = t0)
    private suspend fun upload(id: UUID) = transport().execute(SyncOperation(SyncEntityType.journey, id, SyncAction.update))

    @Test fun testConflictAdoptsServerVersionAndUnlocksTheRow() = runBlocking<Unit> {
        val local = local(); server.enqueue(error("CONFLICT_DETECTED")); server.enqueue(okValue(JourneyListResponse(listOf(serverJourney(local.id)))))
        upload(local.id)
        val after = repo.fetchJourney(local.id)!!
        assertEquals(SyncStatus.synced.rawValue, repo.journeySyncStatus(local.id)); assertEquals("Серверное имя", after.title)
        assertEquals(7, after.conflictVersion); assertEquals(t0.plusSeconds(5 * 86_400), after.endDate)
        assertEquals("/journeys/upsert", server.takeRequest().url.encodedPath); assertEquals("/journeys/list", server.takeRequest().url.encodedPath)
    }
    @Test fun testConflictWithoutServerRowLeavesTheLocalOneAlone() = runBlocking<Unit> {
        val local = local(); server.enqueue(error("CONFLICT_DETECTED")); server.enqueue(okValue(JourneyListResponse(emptyList())))
        upload(local.id); assertEquals("Местное имя", repo.fetchJourney(local.id)?.title); assertEquals(SyncStatus.pendingUpload.rawValue, repo.journeySyncStatus(local.id))
    }
    @Test fun testAcceptedUploadStillMarksSynced() = runBlocking<Unit> {
        val local = local(); server.enqueue(ok("{\"id\":\"${local.id}\",\"conflictVersion\":3}")); upload(local.id)
        assertEquals(SyncStatus.synced.rawValue, repo.journeySyncStatus(local.id)); assertEquals(3, repo.fetchJourney(local.id)?.conflictVersion)
        assertEquals("Местное имя", repo.fetchJourney(local.id)?.title); assertEquals(1, server.requestCount)
    }
    @Test fun testOtherServerErrorStillThrows() = runBlocking<Unit> {
        val local = local(); server.enqueue(error("TOO_MANY_REQUESTS")); assertFailsWith<ApiError.TooManyRequests> { upload(local.id) }
        assertEquals(SyncStatus.pendingUpload.rawValue, repo.journeySyncStatus(local.id))
    }
    @Test fun testDeleteOfAJourneyTheServerDoesNotKnowFinishesLocally() = runBlocking<Unit> {
        val local = local(); repo.markJourneyDeleted(local.id); server.enqueue(error("JOURNEY_NOT_FOUND"))
        transport().execute(SyncOperation(SyncEntityType.journey, local.id, SyncAction.delete)); assertNull(repo.fetchJourney(local.id)); assertNull(repo.journeySyncStatus(local.id))
    }
    @Test fun testAcceptedDeleteRemovesTheRow() = runBlocking<Unit> {
        val local = local(); repo.markJourneyDeleted(local.id); server.enqueue(ok())
        transport().execute(SyncOperation(SyncEntityType.journey, local.id, SyncAction.delete)); assertNull(repo.journeySyncStatus(local.id))
    }
    @Test fun unpublishUsesPrivateUpsertWithoutChangingLegs() = runBlocking<Unit> {
        cloud = false; val local = local(); val leg = tripPayload(); repo.applyRemoteTrip(leg)
        server.enqueue(ok("{\"id\":\"${local.id}\",\"conflictVersion\":3}"))
        transport().execute(SyncOperation(SyncEntityType.journey, local.id, SyncAction.unpublish))
        val request = server.takeRequest(); assertEquals("/journeys/upsert", request.url.encodedPath)
        assertTrue(request.body!!.utf8().contains("\"isPrivate\":true")); assertFalse(repo.fetchEntity(leg.id)!!.isPrivate)
    }
}
