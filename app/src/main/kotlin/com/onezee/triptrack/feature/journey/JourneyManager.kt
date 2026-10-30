package com.onezee.triptrack.feature.journey

import com.onezee.triptrack.data.TripRepository
import com.onezee.triptrack.data.sync.*
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// iOS: JourneyManager.swift (JourneyError)
sealed class JourneyError : Exception() {
    data object Empty : JourneyError()
    data class Overlaps(val id: UUID) : JourneyError()
    data object NotFound : JourneyError()
    data object SignInRequired : JourneyError()
}

// iOS: JourneyManager.swift. Share one instance between composer/edit/detail; the sink is SyncEnqueuer.
class JourneyManager(
    private val repository: TripRepository,
    private val syncSink: SyncRequestSink,
    private val signedIn: () -> Boolean,
    private val cloudSyncEnabled: () -> Boolean,
) {
    private val edits = Mutex()

    suspend fun create(trips: List<Trip>, title: String?): Journey = edits.withLock {
        val sorted = trips.sortedBy { it.startDate }
        val first = sorted.firstOrNull() ?: throw JourneyError.Empty
        val last = sorted.last()
        val end = last.endDate ?: last.startDate
        checkOverlap(first.startDate, end, null)
        val chosen = trips.map { it.id }.toSet()
        val excluded = repository.fetchTrips(first.startDate, end).filterNot { it.id in chosen }.sortedBy { it.startDate }.map { it.id }
        repository.saveJourney(Journey(title = title?.trim()?.takeIf { it.isNotEmpty() }, startDate = first.startDate,
            endDate = end, excludedTripIds = excluded)).also { enqueue(it.id, SyncAction.upload) }
    }
    suspend fun update(journey: Journey): Journey = edits.withLock {
        checkOverlap(journey.startDate, journey.endDate, journey.id)
        repository.saveJourney(journey).also { enqueue(it.id, SyncAction.update) }
    }
    private suspend fun checkOverlap(start: Instant, end: Instant?, excluding: UUID?) {
        repository.journeyOverlapping(start, end, excluding)?.let { throw JourneyError.Overlaps(it.id) }
    }
    suspend fun neighbours(anchor: Trip): List<Trip> {
        val existing = repository.fetchJourneys()
        return repository.fetchTrips(anchor.startDate.minusSeconds(7 * 86_400), anchor.startDate.plusSeconds(7 * 86_400))
            .filter { it.id != anchor.id && existing.none { journey -> journey.contains(it) } }.sortedBy { it.startDate }
    }
    suspend fun privateLegs(journey: Journey): List<Trip> = repository.trips(journey).filter { it.isPrivate }
    suspend fun publish(id: UUID) = edits.withLock {
        if (!signedIn()) throw JourneyError.SignInRequired
        val journey = repository.fetchJourney(id) ?: throw JourneyError.NotFound
        for (leg in privateLegs(journey)) repository.updatePrivacy(leg.id, false)
        repository.saveJourney(journey.copy(isPrivate = false))
        enqueue(id, SyncAction.upload)
    }
    suspend fun hide(id: UUID) = edits.withLock {
        val journey = repository.fetchJourney(id) ?: throw JourneyError.NotFound
        if (!signedIn() && journey.serverCreatedAt != null) throw JourneyError.SignInRequired
        repository.saveJourney(journey.copy(isPrivate = true))
        syncSink.cancel(id, SyncEntityType.journey)
        enqueue(id, SyncAction.unpublish)
    }
    suspend fun delete(id: UUID) = edits.withLock {
        repository.markJourneyDeleted(id)
        syncSink.cancel(id, SyncEntityType.journey)
        enqueue(id, SyncAction.delete)
        if (!cloudSyncEnabled()) repository.deleteJourneyHard(id)
    }
    private fun enqueue(id: UUID, action: SyncAction) = syncSink.enqueue(SyncRequest(SyncEntityType.journey, id, action))
}
