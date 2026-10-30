package com.onezee.triptrack.sync

import com.onezee.triptrack.data.ManualTrackSnapshot
import com.onezee.triptrack.data.TripRepository
import com.onezee.triptrack.model.*
import com.onezee.triptrack.network.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

// iOS: ManualTripTrackLoader.swift. Failed hydration leaves the upload pending.
class ManualTripTrackLoader(
    private val repository: TripRepository,
    private val client: ApiClient,
    private val accountId: () -> UUID?,
    private val cloudSyncEnabled: () -> Boolean,
    private val afterLoad: suspend (UUID) -> Unit = {},
) {
    // iOS: ManualTripTrackLoader.loadIfNeeded. The caller supplies its worker dispatcher.
    suspend fun loadIfNeeded(id: UUID): Trip? {
        currentCoroutineContext().ensureActive()
        if (!cloudSyncEnabled()) return null
        val owner = accountId() ?: return null
        val snapshot = repository.missingManualTrack(id) ?: return null
        return try {
            val payload: TripSyncPayload = client.post(ApiEndpoint.tripDetail, TripDetailRequest(id, true))
            val points = validatedPoints(payload, snapshot) ?: return null
            currentCoroutineContext().ensureActive()
            if (!cloudSyncEnabled() || accountId() != owner || repository.missingManualTrack(id) != snapshot ||
                !repository.applyMissingManualTrack(points, snapshot)) return null
            afterLoad(id)
            currentCoroutineContext().ensureActive()
            if (accountId() != owner) return null
            repository.fetchTripDetail(id).also {
                currentCoroutineContext().ensureActive()
                if (accountId() != owner) return null
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    }

    suspend fun prepareUpload(id: UUID, zoneActive: Boolean) {
        val trip = repository.fetchTripDetail(id) ?: return
        if (!mustLoadBeforeUpload(trip.source, trip.isPrivate, trip.trackPoints.isNotEmpty(), trip.isOnServer, zoneActive)) return
        val snapshot = repository.missingManualTrack(id) ?: throw ApiError.Transport("Route not downloaded yet")
        val owner = accountId() ?: throw ApiError.UserNotAuth
        if (!cloudSyncEnabled()) throw ApiError.Transport("Route not downloaded yet")
        val payload: TripSyncPayload = client.post(ApiEndpoint.tripDetail, TripDetailRequest(id, true))
        val points = validatedPoints(payload, snapshot) ?: throw ApiError.Transport("Route not downloaded yet")
        if (accountId() != owner || !cloudSyncEnabled() || !repository.applyMissingManualTrack(points, snapshot)) {
            throw ApiError.Transport("Route not downloaded yet")
        }
    }

    companion object {
        const val maximumPoints = 10_000
        fun mustLoadBeforeUpload(source: TripOrigin, isPrivate: Boolean, hasLocalPoints: Boolean, isOnServer: Boolean, zoneActive: Boolean): Boolean =
            source == TripOrigin.manual && isOnServer && !hasLocalPoints && !isPrivate && zoneActive

        fun validatedPoints(payload: TripSyncPayload, expected: ManualTrackSnapshot): List<TrackPointPayload>? {
            if (payload.id != expected.id || payload.source != TripOrigin.manual || payload.conflictVersion != expected.conflictVersion ||
                payload.lastModifiedAt != expected.lastModifiedAt) return null
            val end = payload.endDate ?: return null
            val points = payload.trackPoints ?: return null
            if (end < payload.startDate || points.size !in 2..maximumPoints) return null
            var previous = payload.startDate
            val ids = mutableSetOf<UUID>()
            for (point in points) {
                if (!point.latitude.isFinite() || point.latitude !in -90.0..90.0 || !point.longitude.isFinite() || point.longitude !in -180.0..180.0 ||
                    !point.altitude.isFinite() || !point.speed.isFinite() || !point.course.isFinite() || !point.horizontalAccuracy.isFinite() ||
                    point.timestamp < previous || point.timestamp > end || !ids.add(point.id)) return null
                previous = point.timestamp
            }
            return points
        }
    }
}
