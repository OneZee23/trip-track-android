// iOS: AuthService.swift (publishedTripCount, unpublishAllPublicTrips, unpublishAllPublicJourneys,
// wipeServerData, forgetServerDiscoveries, wipeServerVehicles)
package com.onezee.triptrack.feature.account

import android.util.Log
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.onezee.triptrack.data.TripRepository
import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.model.EmptyRequest
import com.onezee.triptrack.model.EmptyResponse
import com.onezee.triptrack.model.JourneySyncPayload
import com.onezee.triptrack.model.SyncStatus
import com.onezee.triptrack.network.ApiClient
import com.onezee.triptrack.network.ApiEndpoint
import com.onezee.triptrack.network.ApiError
import com.onezee.triptrack.network.JourneyDeleteRequest
import com.onezee.triptrack.network.JourneyUpsertResponse
import com.onezee.triptrack.network.TripDeleteRequest
import com.onezee.triptrack.network.TripUpsertResponse
import com.onezee.triptrack.network.VehicleDeleteRequest
import com.onezee.triptrack.sync.PrivacyZoneSpec
import com.onezee.triptrack.sync.TripSyncPayloadMapper
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The account card's server operations. Each object is sent on its own and fails on its own: one
 * refused trip must not leave the rest public, and the count of failures is what the screen says.
 */
class AccountServerActions(
    private val client: ApiClient,
    private val db: TripTrackDatabase,
    private val trips: TripRepository,
    private val zone: () -> PrivacyZoneSpec?,
    private val clock: () -> Instant = Instant::now,
) {
    /** Trips on the server with `isPrivate = false`; decides which question the sign-out dialog asks. */
    suspend fun publishedTripCount(): Int = db.tripDao().publicTrips().count { it.serverCreatedAt != null }

    /**
     * «Hide public and sign out»: a regular upsert with `isPrivate = true`, not a delete. The trip
     * stays on the server, out of the feed, so its reactions and comments survive a later sign-in.
     */
    suspend fun unpublishAllPublicTrips() {
        for (entity in db.tripDao().publicTrips().filter { it.serverCreatedAt != null }) {
            val trip = trips.fetchTripDetail(entity.id) ?: continue
            val payload = TripSyncPayloadMapper.map(trip.copy(isPrivate = true), entity, trips.photoRows(entity.id), zone(), clock())
            try {
                val response: TripUpsertResponse = client.post(ApiEndpoint.tripUpsert, payload)
                db.withTransaction {
                    val current = db.tripDao().trip(entity.id) ?: return@withTransaction
                    db.tripDao().upsertTrip(current.copy(isPrivate = true, lastModifiedAt = payload.lastModifiedAt))
                }
                trips.markSynced(entity.id, response.conflictVersion, response.serverCreatedAt)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: ApiError) {
                Log.w(TAG, "soft unpublish failed for a trip: ${error.message}")
            }
        }
    }

    /** Journeys first (the caller's order): a public journey without legs is an empty card. */
    suspend fun unpublishAllPublicJourneys() {
        for (journey in trips.fetchJourneys().filter { !it.isPrivate && it.serverCreatedAt != null }) {
            val payload = JourneySyncPayload(journey.id, journey.title, journey.startDate, journey.endDate, journey.excludedTripIds,
                journey.coverPhotoId, true, journey.conflictVersion, journey.lastModifiedAt, journey.serverCreatedAt)
            try {
                val response: JourneyUpsertResponse = client.post(ApiEndpoint.journeyUpsert, payload)
                db.withTransaction {
                    val current = db.journeyDao().journey(journey.id) ?: return@withTransaction
                    db.journeyDao().upsert(current.copy(isPrivate = true, lastModifiedAt = payload.lastModifiedAt))
                }
                trips.markJourneySynced(journey.id, response.conflictVersion)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: ApiError) {
                Log.w(TAG, "soft unpublish failed for a journey: ${error.message}")
            }
        }
    }

    /**
     * «Clear server data»: deletes every server copy and keeps the local ones, flipped private with
     * their server bookkeeping cleared. The caller has already switched Cloud Sync off and emptied
     * the queue, so nothing re-uploads. Returns how many objects could NOT be deleted; a repeat is
     * safe because what is gone no longer has a server copy.
     */
    suspend fun wipeServerData(): Int {
        var failures = 0
        for (id in serverIds("TripEntity")) {
            val current = db.tripDao().trip(id) ?: continue
            try { client.post<TripDeleteRequest, EmptyResponse>(ApiEndpoint.tripDelete, TripDeleteRequest(current.id, current.conflictVersion)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: ApiError) {
                if (error != ApiError.TripNotFound) { failures++; continue }
            }
            db.withTransaction {
                val row = db.tripDao().trip(current.id) ?: return@withTransaction
                db.tripDao().upsertTrip(row.copy(isPrivate = true))
            }
            trips.markUnpublished(current.id)
        }
        for (id in serverIds("JourneyEntity")) {
            try { client.post<JourneyDeleteRequest, EmptyResponse>(ApiEndpoint.journeyDelete, JourneyDeleteRequest(id)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: ApiError) {
                if (error != ApiError.JourneyNotFound) { failures++; continue }
            }
            // Private and pending: it returns to the server only through a new publication by hand.
            db.withTransaction {
                val row = db.journeyDao().journey(id) ?: return@withTransaction
                db.journeyDao().upsert(row.copy(isPrivate = true, serverCreatedAt = null, syncStatus = SyncStatus.pendingUpload.rawValue))
            }
        }
        if (!forgetServerDiscoveries()) failures++
        failures += wipeServerVehicles()
        return failures
    }

    /** One request for every find: the server has no per-find delete. The phone keeps its seals. */
    suspend fun forgetServerDiscoveries(): Boolean = try {
        client.post<EmptyRequest, EmptyResponse>(ApiEndpoint.secretsForgetAll, EmptyRequest())
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: ApiError) {
        Log.w(TAG, "forgetting server discoveries failed: ${error.message}")
        false
    }

    /**
     * The garage too: «clear my data from the server» once left a PUBLIC garage behind. The car stays
     * on the phone as `pendingUpload`: there is no server copy, and a later Cloud Sync must send it.
     */
    private suspend fun wipeServerVehicles(): Int {
        var failures = 0
        for (vehicle in db.vehicleDao().vehicles()) {
            try { client.post<VehicleDeleteRequest, EmptyResponse>(ApiEndpoint.vehicleDelete, VehicleDeleteRequest(vehicle.id)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: ApiError) {
                if (error != ApiError.VehicleNotFound) { failures++; continue }
            }
            db.withTransaction {
                val row = db.vehicleDao().vehicle(vehicle.id) ?: return@withTransaction
                db.vehicleDao().upsertVehicle(row.copy(syncStatus = SyncStatus.pendingUpload.rawValue))
            }
        }
        return failures
    }

    /** Every row with a server copy, whatever its sync state: a queued delete was just dropped too. */
    private suspend fun serverIds(table: String): List<UUID> = withContext(Dispatchers.IO) {
        db.query(SimpleSQLiteQuery("SELECT id FROM $table WHERE serverCreatedAt IS NOT NULL")).use { cursor ->
            buildList { while (cursor.moveToNext()) cursor.getString(0)?.let { runCatching { UUID.fromString(it) }.getOrNull() }?.let(::add) }
        }
    }

    private companion object {
        const val TAG = "TripTrackAccount"
    }
}
