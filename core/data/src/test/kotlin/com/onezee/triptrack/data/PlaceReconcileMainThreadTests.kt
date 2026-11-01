// iOS: PlaceReconcileMainThreadTests.swift. The JVM caller stands for the Android main dispatcher.
package com.onezee.triptrack.data

import com.onezee.triptrack.logic.cell
import com.onezee.triptrack.logic.encodePolyline
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.Place
import com.onezee.triptrack.model.PlacePass
import com.onezee.triptrack.model.TrackPoint
import com.onezee.triptrack.model.Trip
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Test
import kotlin.test.*

class PlaceReconcileMainThreadTests : RoomTestCase() {
    private class History : PendingPlaceHistory { override var ids = emptyList<UUID>() }

    @Test fun reconcileReadsListsOnceAndMarksInBatchesOf200() = test {
        val start = Instant.ofEpochSecond(1_760_000_000)
        val pending = (0 until 401).map { TripPreviewRef(UUID.randomUUID(), start, null) }
        var previewReads = 0
        var placeReads = 0
        val batches = mutableListOf<List<UUID>>()
        val repository = object : TripRepository by repo {
            override suspend fun checkpointsWithoutPlace() = emptyList<CheckpointRef>()
            override suspend fun tripPreviews(needingPlaceMatch: Boolean): List<TripPreviewRef> {
                assertTrue(needingPlaceMatch)
                previewReads++
                return pending
            }
            override suspend fun fetchTripDetail(id: UUID): Trip? = null
            override suspend fun markPlacesMatched(tripIds: List<UUID>) { batches.add(tripIds.toList()) }
        }
        val store = object : PlaceStore by RoomPlaceStore(db) {
            override suspend fun fetchPlaces(): List<Place> {
                placeReads++
                return listOf(Place(id = Place.id(Place.cell(45.0, 38.9)), cell = Place.cell(45.0, 38.9), latitude = 45.0, longitude = 38.9, createdAt = start))
            }
        }
        PlaceManager(repository, store, History(), CoroutineScope(currentCoroutineContext())).reconcile()
        assertEquals(1, previewReads)
        assertEquals(1, placeReads)
        assertEquals(listOf(200, 200, 1), batches.map { it.size })
        assertEquals(pending.map { it.id }, batches.flatten())
    }

    @Test fun overlappingReconcileReturnsAtOnceAndCancellationReleasesTheGate() = test {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var reads = 0
        val repository = object : TripRepository by repo {
            override suspend fun checkpointsWithoutPlace(): List<CheckpointRef> {
                reads++
                entered.complete(Unit)
                release.await()
                return emptyList()
            }
        }
        val manager = PlaceManager(repository, RoomPlaceStore(db), History(), CoroutineScope(currentCoroutineContext()))
        val first = CoroutineScope(currentCoroutineContext()).launch { manager.reconcile() }
        entered.await()
        withTimeout(200) { manager.reconcile() }
        assertEquals(1, reads)
        first.cancelAndJoin()
        release.complete(Unit)
        manager.reconcile()
        assertEquals(2, reads)
    }

    @Test fun reconcileKeepsCallerResponsiveUnderAdversarialStress() = test {
        val start = Instant.ofEpochSecond(1_760_000_000)
        val ids = (0 until 400).map { UUID.randomUUID() }
        val refs = ids.mapIndexed { t, id ->
            val lat = if (t < 40) 45.0 + t * 0.0005 else 47.0 + (t - 40) * 0.01
            val lon = if (t < 40) 38.9 else 40.9
            TripPreviewRef(id, start.minusSeconds((t + 1) * 3600L), Trip.encodePolyline((0 until 2000).map { p ->
                if (t < 40) GeoPoint(lat, lon + p * 0.00005) else GeoPoint(lat + p * 0.00003, lon + p * 0.00002)
            }))
        }
        val known = (0 until 60).map { i ->
            val lat = 45.0 + (40 / 2) * 0.0005
            val lon = 38.9 + i * (2000 * 0.00005 / 60)
            Place(id = Place.id(Place.cell(lat, lon)), cell = Place.cell(lat, lon), latitude = lat, longitude = lon, createdAt = start)
        }
        val details = AtomicInteger()
        val writes = AtomicInteger()
        var marked = 0
        val repository = object : TripRepository by repo {
            override suspend fun checkpointsWithoutPlace() = emptyList<CheckpointRef>()
            override suspend fun tripPreviews(needingPlaceMatch: Boolean) = refs
            override suspend fun fetchTripDetail(id: UUID): Trip {
                assertNotEquals("PlaceTestCaller", Thread.currentThread().name, "track reads must leave the caller")
                details.incrementAndGet()
                // A synchronous slow read catches a missing IO handoff even on a fast test machine.
                Thread.sleep(15)
                val t = ids.indexOf(id)
                val ref = refs[t]
                return Trip(id = id, startDate = ref.startDate, endDate = ref.startDate.plusSeconds(10_000),
                    trackPoints = (0 until 2000).map { p -> TrackPoint(latitude = 45.0 + t * 0.0005,
                        longitude = 38.9 + p * 0.00005, altitude = 40.0, speed = 14.0,
                        timestamp = ref.startDate.plusSeconds(p * 5L)) })
            }
            override suspend fun markPlacesMatched(tripIds: List<UUID>) { marked += tripIds.size }
        }
        val store = object : PlaceStore by RoomPlaceStore(db) {
            override suspend fun fetchPlaces() = known
            override suspend fun passesOfTrip(tripId: UUID) = emptyList<PlacePass>()
            override suspend fun replacePasses(placeId: UUID, tripId: UUID, passes: List<PlacePass>) { writes.incrementAndGet() }
        }
        val caller = Executors.newSingleThreadExecutor { Thread(it, "PlaceTestCaller") }.asCoroutineDispatcher()
        try {
            withContext(caller) {
                val manager = PlaceManager(repository, store, History(), CoroutineScope(currentCoroutineContext()))
                val job = launch { manager.reconcile() }
                var maxGapMs = 0.0
                do {
                    val before = System.nanoTime()
                    delay(15)
                    maxGapMs = maxOf(maxGapMs, (System.nanoTime() - before) / 1_000_000.0)
                } while (job.isActive)
                job.join()
                assertEquals(400, marked)
                assertEquals(40, details.get(), "far previews must not load tracks")
                assertTrue(writes.get() > 0)
                assertTrue(maxGapMs < 200, "caller stalled for $maxGapMs ms (iOS budget: 200 ms)")
            }
        } finally { caller.close() }
    }
}
