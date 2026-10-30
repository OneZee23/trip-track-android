package com.onezee.triptrack.sync

import com.onezee.triptrack.model.*
import java.io.File
import java.util.UUID
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Test

// iOS: ManualTripTrackLoader.swift (loadIfNeeded), ManualTripHydrationBudgetTests.swift
class ManualDetailHydrationTests : SyncTestCase() {
    @Test fun openingHydratesOnceAndHandsBackFullPoints() = runBlocking<Unit> {
        val trip = tripPayload().copy(source = TripOrigin.manual)
        repo.applyRemoteTrip(trip)
        val points = List(2) { TrackPointPayload(UUID.randomUUID(), 45.0, 38.9, 0.0, -1.0, -1.0, -1.0, t0.plusSeconds(it * 30L), false) }
        server.enqueue(okValue(trip.copy(trackPoints = points)))
        var settled: UUID? = null
        val loader = ManualTripTrackLoader(repo, client, { account }, { cloud }, { settled = it })
        assertEquals(2, loader.loadIfNeeded(trip.id)!!.trackPoints.size)
        assertEquals(trip.id, settled)
        assertNull(loader.loadIfNeeded(trip.id))
        assertEquals(1, server.requestCount)
    }
    @Test fun cloudOffAndInvalidPayloadPreserveTheExistingPreview() = runBlocking<Unit> {
        val trip = tripPayload().copy(source = TripOrigin.manual)
        repo.applyRemoteTrip(trip)
        val loader = ManualTripTrackLoader(repo, client, { account }, { cloud })
        cloud = false
        assertNull(loader.loadIfNeeded(trip.id)); assertEquals(0, server.requestCount)
        cloud = true
        server.enqueue(okValue(trip.copy(trackPoints = emptyList())))
        assertNull(loader.loadIfNeeded(trip.id))
        assertNotNull(repo.fetchTripDetail(trip.id))
        assertTrue(repo.fetchTripDetail(trip.id)!!.trackPoints.isEmpty())
    }
    @Test fun accountChangeAfterAftermathDoesNotHandTheTripToAnotherAccount() = runBlocking<Unit> {
        val trip = tripPayload().copy(source = TripOrigin.manual)
        repo.applyRemoteTrip(trip)
        val points = List(2) { TrackPointPayload(UUID.randomUUID(), 45.0, 38.9, 0.0, -1.0, -1.0, -1.0, t0.plusSeconds(it * 30L), false) }
        server.enqueue(okValue(trip.copy(trackPoints = points)))
        val loader = ManualTripTrackLoader(repo, client, { account }, { cloud }, { account = UUID.randomUUID() })
        assertNull(loader.loadIfNeeded(trip.id))
    }
    // iOS: DiscussionArchive.swift (successful comments reads replace the cached first page)
    @Test fun commentsReadCanSaveAndReplaceItsDiscussionArchive() = runBlocking<Unit> {
        val archive = DiscussionArchive(client, File(directory, "Discussions"))
        val tripId = UUID.randomUUID()
        val comment = TripComment(UUID.randomUUID(), SocialAuthor(UUID.randomUUID(), profileLevel = 1), "road", t0, false)
        archive.save(tripId, listOf(comment))
        assertEquals(listOf(comment), archive.load(tripId))
        archive.save(tripId, emptyList())
        assertTrue(archive.load(tripId).isEmpty())
    }
}
