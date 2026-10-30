// iOS: SyncCoordinator.recoverPendingEntities, pendingJourneyOperations, pendingVehiclePhotoOperations
package com.onezee.triptrack.data

import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.data.sync.SyncAction
import com.onezee.triptrack.data.sync.SyncEntityType
import com.onezee.triptrack.data.sync.SyncRequest
import com.onezee.triptrack.model.SyncStatus
import java.util.UUID

/**
 * The queue lives in memory and dies with the process; the pending FLAGS live in the database. On
 * launch every pending row becomes an operation again, or an edit made on a plane would stay on the
 * phone forever. The enqueuer's gate still decides what may leave.
 *
 * Scoped to this install's user id, so a fresh sign-in never drags another account's leftovers into
 * the queue, except for car photos: they have no user column, and the cars' own one is stamped by a
 * single old migration, so a filter would drop nearly the whole garage.
 */
class PendingSyncRecovery(private val db: TripTrackDatabase) {
    suspend fun pendingOperations(userId: UUID): List<SyncRequest> {
        val trips = db.tripDao()
        val pendingDelete = SyncStatus.pendingDelete.rawValue
        val result = mutableListOf<SyncRequest>()
        for (t in trips.pendingTrips(userId)) {
            val action = if (t.syncStatus == pendingDelete) SyncAction.delete else SyncAction.upload
            result.add(SyncRequest(SyncEntityType.trip, t.id, action))
        }
        for (v in db.vehicleDao().pendingVehicles(userId)) result.add(SyncRequest(SyncEntityType.vehicle, v.id, SyncAction.upload))
        for (p in trips.pendingPhotos(userId)) result.add(SyncRequest(SyncEntityType.photo, p.id, SyncAction.upload))
        db.settingsDao().pendingSettings()?.let { result.add(SyncRequest(SyncEntityType.settings, it.id, SyncAction.upload)) }
        result.addAll(pendingJourneyOperations(userId))
        result.addAll(pendingVehiclePhotoOperations())
        return result
    }

    /**
     * pendingUpload is an `update` (one upsert on the wire, and «update» describes a row that has lain
     * in the store), pendingDelete a `delete`, synced goes nowhere.
     */
    suspend fun pendingJourneyOperations(userId: UUID): List<SyncRequest> =
        db.journeyDao().pendingJourneys(userId).map { j ->
            val action = if (j.syncStatus == SyncStatus.pendingDelete.rawValue) SyncAction.delete else SyncAction.update
            SyncRequest(SyncEntityType.journey, j.id, action)
        }

    /**
     * «Not yet uploaded» is asked of the links, not of `syncStatus`: two sizes, two columns, and
     * while either is empty the server does not hold the whole photo. Rows of a DEAD car are
     * skipped, or the server's refusal would park them in the failed queue forever.
     */
    suspend fun pendingVehiclePhotoOperations(): List<SyncRequest> {
        val vehicles = db.vehicleDao()
        val alive = vehicles.vehicleIds().toSet()
        if (alive.isEmpty()) return emptyList()
        return vehicles.photosNotFullyUploaded()
            .filter { it.vehicleId != null && it.vehicleId in alive }
            .map { SyncRequest(SyncEntityType.vehiclePhoto, it.id, SyncAction.upload) }
            .take(MAX_VEHICLE_PHOTOS_PER_PASS)
    }

    companion object {
        /** A poll, not a one-off mark: the rest is picked up by the next pass. */
        const val MAX_VEHICLE_PHOTOS_PER_PASS = 200
    }
}
