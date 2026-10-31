// iOS: SyncStatusSheetView.swift (SyncCountSnapshot, loadSnapshot, countEntity, countPhoto)
package com.onezee.triptrack.feature.account

import androidx.sqlite.db.SimpleSQLiteQuery
import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.model.PhotoUploadStatus
import com.onezee.triptrack.model.SyncStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Every synced type by its cloud state, read from the store when the sheet opens and after a pull,
 * so the person sees what is in the cloud and what is still only here, even when nothing is in
 * flight. Achievements have no row: they are not a synced entity.
 */
data class SyncCounts(
    val tripsSynced: Int = 0,
    val tripsPending: Int = 0,
    val vehiclesSynced: Int = 0,
    val vehiclesPending: Int = 0,
    /** Both sizes on the server. */
    val photosUploaded: Int = 0,
    /** Thumbnail only: usually waiting for Wi-Fi. */
    val photosPartial: Int = 0,
    val photosLocal: Int = 0,
    val photosFailed: Int = 0,
    val settingsExists: Boolean = false,
    val settingsSynced: Boolean = false,
) {
    val photosTotal: Int get() = photosUploaded + photosPartial + photosLocal + photosFailed

    companion object {
        suspend fun read(db: TripTrackDatabase, dispatcher: CoroutineDispatcher = Dispatchers.IO): SyncCounts = withContext(dispatcher) {
            val trips = grouped("SELECT syncStatus, COUNT(*) FROM TripEntity GROUP BY syncStatus", db)
            val vehicles = grouped("SELECT syncStatus, COUNT(*) FROM VehicleEntity GROUP BY syncStatus", db)
            // Photos of a soft-deleted trip live until the server confirms the delete; the trip rows
            // already leave them out, and so do these. An orphan photo still counts.
            val photos = grouped(
                "SELECT p.uploadStatus, COUNT(*) FROM TripPhotoEntity p LEFT JOIN TripEntity t ON t.id = p.tripId " +
                    "WHERE t.id IS NULL OR t.syncStatus != ${SyncStatus.pendingDelete.rawValue} GROUP BY p.uploadStatus",
                db,
            )
            val settings = db.query(SimpleSQLiteQuery("SELECT syncStatus FROM UserSettingsEntity LIMIT 1")).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else null
            }
            SyncCounts(
                tripsSynced = trips[SyncStatus.synced.rawValue.toInt()] ?: 0,
                tripsPending = trips[SyncStatus.pendingUpload.rawValue.toInt()] ?: 0,
                vehiclesSynced = vehicles[SyncStatus.synced.rawValue.toInt()] ?: 0,
                vehiclesPending = vehicles[SyncStatus.pendingUpload.rawValue.toInt()] ?: 0,
                photosUploaded = photos[PhotoUploadStatus.uploaded.rawValue.toInt()] ?: 0,
                photosPartial = photos[PhotoUploadStatus.uploading.rawValue.toInt()] ?: 0,
                photosLocal = photos[PhotoUploadStatus.localOnly.rawValue.toInt()] ?: 0,
                photosFailed = photos[PhotoUploadStatus.failed.rawValue.toInt()] ?: 0,
                settingsExists = settings != null,
                settingsSynced = settings == SyncStatus.synced.rawValue.toInt(),
            )
        }

        private fun grouped(sql: String, db: TripTrackDatabase): Map<Int, Int> = db.query(SimpleSQLiteQuery(sql)).use { cursor ->
            buildMap { while (cursor.moveToNext()) put(cursor.getInt(0), cursor.getInt(1)) }
        }
    }
}
