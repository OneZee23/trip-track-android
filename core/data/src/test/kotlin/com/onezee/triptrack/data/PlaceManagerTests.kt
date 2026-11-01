// iOS: PlaceManagerTests.swift. Coordinates, timestamps, directions and assertions are the iOS cases.
package com.onezee.triptrack.data

import android.content.Context
import com.onezee.triptrack.data.db.entity.TrackPointEntity
import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.logic.cell
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.Place
import com.onezee.triptrack.model.TripCheckpoint
import com.onezee.triptrack.model.TripConfirmation
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import kotlin.test.*

class PlaceManagerTests : RoomTestCase() {
    private val t0 = Instant.ofEpochSecond(1_760_000_000)
    private val lat0 = 44.30
    private val lon0 = 38.70
    private val stepLat = 0.00045
    private lateinit var store: RoomPlaceStore
    private lateinit var manager: PlaceManager
    private lateinit var history: PreferencesPendingPlaceHistory
    private lateinit var scope: CoroutineScope

    @Before fun makeManager() {
        val preferences = RuntimeEnvironment.getApplication().getSharedPreferences("place-manager-tests", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        history = PreferencesPendingPlaceHistory(preferences)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        store = RoomPlaceStore(db)
        manager = PlaceManager(repo, store, history, scope)
    }

    @After fun finishManager() = blocking { manager.settle(); scope.cancel(); history.ids = emptyList() }

    private suspend fun trip(start: Instant = t0, reverse: Boolean = false, offsetLon: Double = 0.0,
        count: Int = 120, confirmation: TripConfirmation = TripConfirmation.confirmed, ended: Boolean = true): UUID {
        val id = UUID.randomUUID()
        insert(TripEntity(id = id, startDate = start, endDate = start.plusSeconds(count * 10L).takeIf { ended },
            distance = count * 50.0, isPrivate = true, confirmation = confirmation.rawValue))
        db.tripDao().insertTrackPoints((0 until count).map { k ->
            val i = if (reverse) count - 1 - k else k
            TrackPointEntity(id = UUID.randomUUID(), latitude = lat0 + i * stepLat, longitude = lon0 + offsetLon,
                speed = 5.0, course = if (reverse) 180.0 else 0.0, horizontalAccuracy = 5.0,
                timestamp = start.plusSeconds(k * 10L), tripId = id)
        })
        return id
    }

    private suspend fun checkpoint(tripId: UUID, index: Int = 30, name: String? = null): TripCheckpoint {
        val cp = TripCheckpoint(timestamp = t0.plusSeconds(index * 10L), latitude = lat0 + index * stepLat,
            longitude = lon0, distanceFromStart = index * 50.0, elapsedFromStart = index * 10.0, name = name)
        assertNotNull(repo.addCheckpoint(cp, tripId))
        return cp
    }

    @Test fun checkpointBirthsAPlaceAndBackfillsHistory() = test {
        val earlier = trip(t0.minusSeconds(86_400))
        val today = trip()
        val cp = checkpoint(today, name = "Джубга")
        manager.registerCheckpoint(cp, today)
        manager.settle()
        val place = manager.places.value.single()
        assertEquals(Place.id(Place.cell(cp.latitude, cp.longitude)), place.id)
        assertEquals("Джубга", place.name)
        assertEquals(place.id, repo.fetchTripDetail(today)?.checkpoints?.first()?.placeId)
        assertEquals(setOf(earlier, today), store.passes(place.id).map { it.tripId }.toSet())
        assertEquals(2, manager.stats(place.id).passCount)
    }

    @Test fun finishedTripAddsPassesAndIsMarkedMatched() = test {
        val today = trip()
        manager.registerCheckpoint(checkpoint(today), today)
        manager.settle()
        val back = trip(t0.plusSeconds(7_200), reverse = true)
        manager.process(back)
        assertEquals(180.0, store.passesOfTrip(back).single().course, 0.5)
        assertEquals(2, manager.stats(manager.places.value.single().id).directions.size)
        assertFalse(repo.tripPreviews(true).any { it.id == back })
    }

    @Test fun farTripGetsNoPassesButIsStillMarked() = test {
        val today = trip()
        manager.registerCheckpoint(checkpoint(today), today)
        manager.settle()
        val far = trip(t0.plusSeconds(7_200), offsetLon = 1.0)
        manager.process(far)
        assertTrue(store.passesOfTrip(far).isEmpty())
        assertFalse(repo.tripPreviews(true).any { it.id == far })
    }

    @Test fun forgettingATripDropsItsPasses() = test {
        val today = trip()
        manager.registerCheckpoint(checkpoint(today), today)
        manager.settle()
        val back = trip(t0.plusSeconds(7_200), reverse = true)
        manager.process(back)
        manager.forget(back)
        assertEquals(1, manager.stats(manager.places.value.single().id).passCount)
    }

    @Test fun reconcileRegistersOrphanCheckpointsAndMatchesTrips() = test {
        val a = trip()
        checkpoint(a)
        trip(t0.plusSeconds(7_200), reverse = true)
        manager.reconcile()
        assertEquals(2, store.passes(manager.places.value.single().id).size)
        assertTrue(repo.tripPreviews(true).isEmpty())
        val before = store.passes(manager.places.value.single().id).map { it.id }
        manager.reconcile()
        assertEquals(before, store.passes(manager.places.value.single().id).map { it.id })
    }

    @Test fun reconcileWithoutPlacesMarksEverythingWithoutLoadingPoints() = test {
        trip(); trip(t0.plusSeconds(7_200)); trip(t0.plusSeconds(14_400))
        var previewReads = 0
        val batches = mutableListOf<List<UUID>>()
        val counted = object : TripRepository by repo {
            override suspend fun tripPreviews(needingPlaceMatch: Boolean): List<TripPreviewRef> {
                previewReads++
                return repo.tripPreviews(needingPlaceMatch)
            }
            override suspend fun fetchTripDetail(id: UUID): Nothing = error("no places: do not load points")
            override suspend fun markPlacesMatched(tripIds: List<UUID>) { batches.add(tripIds.toList()); repo.markPlacesMatched(tripIds) }
        }
        PlaceManager(counted, store, history, scope).reconcile()
        assertEquals(1, previewReads)
        assertEquals(listOf(3), batches.map { it.size })
        assertTrue(repo.tripPreviews(true).isEmpty())
        assertTrue(store.fetchPlaces().isEmpty())
    }

    @Test fun adoptNameNamesAnUnnamedPlaceOnce() = test {
        val today = trip()
        val cp = checkpoint(today)
        manager.registerCheckpoint(cp, today); manager.settle()
        manager.adoptName("Джубга", cp.id, today)
        manager.adoptName("Поворот", cp.id, today)
        assertEquals("Джубга", manager.places.value.single().name)
        manager.rename(manager.places.value.single().id, "Моё место")
        manager.adoptName("Джубга", cp.id, today)
        assertEquals("Моё место", manager.places.value.single().name)
    }

    @Test fun geocoderNameArrivesBeforeRegistration() = test {
        val earlier = trip(t0.minusSeconds(86_400))
        val today = trip()
        val cp = checkpoint(today)
        manager.adoptName("Джубга", cp.id, today)
        manager.registerCheckpoint(cp, today); manager.settle()
        val place = manager.places.value.single()
        assertEquals(Place.id(Place.cell(cp.latitude, cp.longitude)), place.id)
        assertEquals("Джубга", place.name)
        assertEquals(setOf(earlier, today), store.passes(place.id).map { it.tripId }.toSet())
    }

    @Test fun historyBackfillIsDeferredWhileRecording() = test {
        val earlier = trip(t0.minusSeconds(86_400))
        val today = trip()
        manager.registerCheckpoint(checkpoint(today), today, recording = true); manager.settle()
        val place = manager.places.value.single()
        assertTrue(store.passes(place.id).isEmpty())
        assertEquals(listOf(place.id), history.ids)
        manager.process(today)
        assertEquals(setOf(earlier, today), store.passes(place.id).map { it.tripId }.toSet())
        assertTrue(history.ids.isEmpty())
    }

    @Test fun pendingHistorySurvivesNewManagerAndReconcileDrainsIt() = test {
        val earlier = trip(t0.minusSeconds(86_400))
        val today = trip(ended = false)
        val cp = checkpoint(today)
        manager.registerCheckpoint(cp, today, recording = true)
        manager.registerCheckpoint(cp, today, recording = true)
        val id = manager.places.value.single().id
        val preferences = RuntimeEnvironment.getApplication().getSharedPreferences("place-manager-tests", Context.MODE_PRIVATE)
        val recovered = PreferencesPendingPlaceHistory(preferences)
        assertEquals(listOf(id), recovered.ids)
        PlaceManager(repo, store, recovered, scope).reconcile()
        assertEquals(listOf(earlier), store.passes(id).map { it.tripId })
        assertTrue(recovered.ids.isEmpty())
        assertNull(repo.fetchEntity(today)?.placesMatchedAt)
    }

    @Test fun draftCheckpointMintsNoPlaceUntilConfirmedThenNamesItFromTheCheckpoint() = test {
        val draft = trip(confirmation = TripConfirmation.draft)
        val cp = checkpoint(draft, name = "Джубга")
        manager.registerCheckpoint(cp, draft)
        manager.adoptName("Другое", cp.id, draft); manager.settle()
        assertTrue(manager.places.value.isEmpty())
        assertNull(repo.fetchTripDetail(draft)?.checkpoints?.first()?.placeId)
        repo.setConfirmation(TripConfirmation.confirmed, draft)
        manager.registerCheckpoint(cp, draft); manager.settle()
        assertEquals("Джубга", manager.places.value.single().name)
        assertNotNull(repo.fetchTripDetail(draft)?.checkpoints?.first()?.placeId)
    }

    @Test fun deletingAPlaceKeepsCheckpointTombstoneSoItIsNotReborn() = test {
        val today = trip()
        val cp = checkpoint(today)
        manager.registerCheckpoint(cp, today); manager.settle()
        val id = manager.places.value.single().id
        manager.delete(id)
        manager.reconcile()
        manager.adoptName("Джубга", cp.id, today)
        assertTrue(manager.places.value.isEmpty())
        assertEquals(id, repo.fetchTripDetail(today)?.checkpoints?.first()?.placeId)
        manager.registerCheckpoint(checkpoint(today), today); manager.settle()
        assertEquals(id, manager.places.value.single().id)
    }

    @Test fun deletedPendingPlaceIsForgottenOnReconcile() = test {
        val today = trip()
        manager.registerCheckpoint(checkpoint(today), today, recording = true)
        manager.delete(manager.places.value.single().id)
        manager.reconcile()
        assertTrue(manager.places.value.isEmpty())
        assertTrue(history.ids.isEmpty())
    }

    @Test fun suggestionCreatesAPlaceWithoutACheckpointAndBackfills() = test {
        val today = trip()
        val coordinate = GeoPoint(lat0 + 30 * stepLat, lon0)
        val place = manager.createPlace(Place.cell(coordinate.latitude, coordinate.longitude), coordinate, "Джубга")
        manager.settle()
        assertEquals(Place.id(place.cell), place.id)
        assertEquals(listOf(today), store.passes(place.id).map { it.tripId })
        assertTrue(repo.fetchTripDetail(today)!!.checkpoints.isEmpty())
    }

    @Test fun coordinateLessCheckpointAndUnfinishedTripAreNeverMatched() = test {
        val today = trip(ended = false)
        val cp = TripCheckpoint(timestamp = t0, latitude = 0.0, longitude = 0.0, distanceFromStart = 0.0, elapsedFromStart = 0.0)
        repo.addCheckpoint(cp, today)
        manager.registerCheckpoint(cp, today)
        manager.adoptName("Скрыто", cp.id, today)
        manager.process(today)
        assertTrue(manager.places.value.isEmpty())
        assertNull(repo.fetchEntity(today)?.placesMatchedAt)
    }
}
