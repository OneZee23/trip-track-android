package com.onezee.triptrack.sync

import androidx.room.withTransaction
import com.onezee.triptrack.data.VehiclePhotoStore
import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.data.db.entity.VehiclePhotoEntity
import com.onezee.triptrack.data.files.VehiclePhotoFiles
import com.onezee.triptrack.model.EmptyRequest
import com.onezee.triptrack.model.SyncStatus
import com.onezee.triptrack.network.*
import java.time.Instant
import java.util.UUID

// iOS: VehiclePhotoRestore.swift. Missing server entries never infer local deletion.
class VehiclePhotoRestore(
    private val client: ApiClient,
    private val db: TripTrackDatabase,
    private val files: VehiclePhotoFiles,
    private val store: VehiclePhotoStore,
    private val cloudSyncEnabled: () -> Boolean,
    private val accountId: () -> UUID?,
    private val clock: () -> Instant = Instant::now,
    private val downloadBytes: suspend (String) -> ByteArray = client::getBytes,
) {
    suspend fun run() {
        val owner = accountId() ?: return
        if (!cloudSyncEnabled()) return
        val response: VehiclePhotoListResponse = client.post(ApiEndpoint.vehiclePhotoList, EmptyRequest())
        for (item in response.photos) {
            if (!cloudSyncEnabled() || accountId() != owner) return
            val source = item.url ?: item.thumbUrl ?: continue
            val existing = db.vehicleDao().photo(item.id)
            if (existing != null && files.exists(existing.filename)) continue
            val bytes = try { downloadBytes(source) } catch (_: ApiError) { continue }
            if (!cloudSyncEnabled() || accountId() != owner) return
            val filename = files.save(bytes) ?: continue
            db.withTransaction {
                val current = db.vehicleDao().photo(item.id)
                if (current != null && files.exists(current.filename)) { files.delete(filename); return@withTransaction }
                db.vehicleDao().upsertPhoto((current ?: VehiclePhotoEntity(item.id, timestamp = item.takenAt ?: clock())).copy(
                    vehicleId = item.vehicleId, filename = filename, isMain = item.isMain,
                    timestamp = item.takenAt ?: clock(), syncStatus = SyncStatus.synced.rawValue,
                ))
            }
        }
        // Signed URLs from this list expire. They are never stored as R2 keys.
        store.invalidateMainCache()
    }
}
