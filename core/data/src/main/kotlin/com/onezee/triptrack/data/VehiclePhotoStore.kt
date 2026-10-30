// iOS: VehiclePhotoStore.swift (VehiclePhotoStore, VehiclePhoto)
package com.onezee.triptrack.data

import androidx.room.withTransaction
import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.data.db.entity.VehiclePhotoEntity
import com.onezee.triptrack.data.files.VehiclePhotoFiles
import com.onezee.triptrack.data.sync.SyncAction
import com.onezee.triptrack.data.sync.SyncEntityType
import com.onezee.triptrack.data.sync.SyncRequest
import com.onezee.triptrack.data.sync.SyncRequestSink
import java.time.Instant
import java.util.UUID

/**
 * iOS: VehiclePhoto. Apart from `TripPhoto`: no caption, but a «main» flag and the SOURCE trip
 * whose caption it borrows. `remoteURL` tells «file lost for good» from «file lost, server has it».
 */
data class VehiclePhoto(
    val id: UUID,
    val vehicleId: UUID,
    val filename: String,
    val isMain: Boolean,
    val timestamp: Instant,
    val sourceTripId: UUID?,
    val remoteURL: String? = null,
)

/** iOS: VehiclePhotoStore. Car photos: files on disk, relationless rows, one «main». */
interface VehiclePhotoStore {
    /** The main one first, then by time; tombstone rows (no file, no server copy) are removed on read. */
    suspend fun photos(vehicleId: UUID): List<VehiclePhoto>
    suspend fun mainPhoto(vehicleId: UUID): VehiclePhoto?
    /** Main photos of the whole garage in one read, remembered until a writer drops the memory. */
    suspend fun mainPhotos(): Map<UUID, VehiclePhoto>
    fun invalidateMainCache()
    /** `jpeg` is encoded at `VehiclePhotoFiles.JPEG_QUALITY`. The first photo becomes the main one. */
    suspend fun add(jpeg: ByteArray, vehicleId: UUID, sourceTripId: UUID? = null): VehiclePhoto?
    suspend fun makeMain(photoId: UUID, vehicleId: UUID)
    suspend fun delete(photoId: UUID, vehicleId: UUID)
    suspend fun deleteAll(vehicleId: UUID)
    /** For a car deleted on the OTHER phone: local rows and files go, nothing is queued. */
    suspend fun deleteAllLocally(vehicleId: UUID)
}

/**
 * Car photos: files on disk, rows without a relationship, one «main». Its own store and directory:
 * trip photos die with their trip, these belong to the car and outlive any of its trips.
 *
 * A car photo is personal data like the car, so every operation goes to the queue and the
 * enqueuer lets it out only with Cloud Sync on.
 */
class RoomVehiclePhotoStore(
    private val db: TripTrackDatabase,
    private val files: VehiclePhotoFiles,
    private val sync: SyncRequestSink = SyncRequestSink.None,
    private val clock: () -> Instant = Instant::now,
) : VehiclePhotoStore {
    private val dao = db.vehicleDao()

    @Volatile private var mainCache: Map<UUID, VehiclePhoto>? = null

    /**
     * Photos of a car: the main one first, the rest by time. A row without a file and without a
     * server copy is a tombstone (a phone change: the store came along, the excluded directory did
     * not); it is removed on read instead of showing a grid of grey tiles labelled «main».
     */
    override suspend fun photos(vehicleId: UUID): List<VehiclePhoto> {
        val rows = dao.photos(vehicleId)
        val dead = rows.filter { !files.exists(it.filename) && it.remoteURL == null }
        if (dead.isNotEmpty()) {
            db.withTransaction { for (row in dead) dao.deletePhoto(row.id) }
            // The remembered main photo may have been one of them.
            invalidateMainCache()
        }
        return rows.filter { it !in dead }.mapNotNull(::photo).sortedWith(mainFirst)
    }

    override suspend fun mainPhoto(vehicleId: UUID): VehiclePhoto? = photos(vehicleId).firstOrNull()

    /**
     * Main photos of the whole garage in ONE read, remembered: the first frame of the garage must
     * not draw silhouettes that are then replaced by photos. Every writer here drops the memory.
     */
    override suspend fun mainPhotos(): Map<UUID, VehiclePhoto> {
        mainCache?.let { return it }
        val fresh = LinkedHashMap<UUID, VehiclePhoto>()
        for (photo in dao.allPhotos().mapNotNull(::photo).sortedWith(mainFirst)) {
            if (photo.vehicleId !in fresh) fresh[photo.vehicleId] = photo
        }
        mainCache = fresh
        return fresh
    }

    override fun invalidateMainCache() {
        mainCache = null
    }

    /** `jpeg` is encoded at `VehiclePhotoFiles.JPEG_QUALITY`. The first photo becomes the main one. */
    override suspend fun add(jpeg: ByteArray, vehicleId: UUID, sourceTripId: UUID?): VehiclePhoto? {
        val filename = files.save(jpeg) ?: return null
        val isFirst = photos(vehicleId).isEmpty()
        val id = UUID.randomUUID()
        val stamp = clock()
        dao.upsertPhoto(
            VehiclePhotoEntity(
                id = id,
                vehicleId = vehicleId,
                filename = filename,
                // A car needs a face, and asking for one when there is no choice is a formality.
                isMain = isFirst,
                timestamp = stamp,
                lastModifiedAt = stamp,
                sourceTripId = sourceTripId,
            ),
        )
        invalidateMainCache()
        // Without this, car photos lived on the phone ONLY: the directory is excluded from backup.
        sync.enqueue(SyncRequest(SyncEntityType.vehiclePhoto, id, SyncAction.upload))
        return VehiclePhoto(id, vehicleId, filename, isFirst, stamp, sourceTripId)
    }

    /** The main photo is part of how others see the car, so the change has to reach the server. */
    override suspend fun makeMain(photoId: UUID, vehicleId: UUID) {
        var changed = false
        db.withTransaction {
            val stamp = clock()
            val rows = dao.photos(vehicleId)
            dao.upsertPhotos(rows.map { row ->
                val isTarget = row.id == photoId
                if (isTarget) changed = true
                row.copy(isMain = isTarget, lastModifiedAt = if (isTarget) stamp else row.lastModifiedAt)
            })
        }
        invalidateMainCache()
        if (changed) sync.enqueue(SyncRequest(SyncEntityType.vehiclePhoto, photoId, SyncAction.update))
    }

    /** Removes the row AND the file: nothing cascades to the file. The next photo becomes the main one. */
    override suspend fun delete(photoId: UUID, vehicleId: UUID) {
        val row = dao.photo(photoId) ?: return
        // Decided before the row goes: afterwards nobody can tell whether the server saw it.
        val hadServerCopy = row.remoteURL != null || row.thumbnailURL != null
        files.delete(row.filename)
        dao.deletePhoto(photoId)
        invalidateMainCache()
        if (hadServerCopy) sync.enqueue(SyncRequest(SyncEntityType.vehiclePhoto, photoId, SyncAction.delete))
        if (row.isMain) photos(vehicleId).firstOrNull()?.let { makeMain(it.id, vehicleId) }
    }

    /** For deleting a car and for deleting the account. */
    override suspend fun deleteAll(vehicleId: UUID) {
        for (photo in photos(vehicleId)) delete(photo.id, vehicleId)
    }

    /**
     * Remove a car's photos WITHOUT touching the server: the car's tombstone came from the OTHER
     * phone, which removed the server copies together with the car. Queuing a delete per photo
     * would hang as «not synced» forever. No new main photo: there is no car left to have a face.
     */
    override suspend fun deleteAllLocally(vehicleId: UUID) {
        val rows = dao.photos(vehicleId)
        if (rows.isEmpty()) return
        for (row in rows) files.delete(row.filename)
        db.withTransaction { for (row in rows) dao.deletePhoto(row.id) }
        invalidateMainCache()
    }

    private fun photo(e: VehiclePhotoEntity): VehiclePhoto? {
        val vehicleId = e.vehicleId ?: return null
        return VehiclePhoto(e.id, vehicleId, e.filename, e.isMain, e.timestamp, e.sourceTripId, e.remoteURL)
    }

    private companion object {
        val mainFirst = compareByDescending<VehiclePhoto> { it.isMain }.thenBy { it.timestamp }
    }
}
