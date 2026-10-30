// iOS: TripRepository.swift (CoreDataTripRepository fetch requests), TripEntity+TrackPoints.swift
package com.onezee.triptrack.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.onezee.triptrack.data.db.entity.TrackPointEntity
import com.onezee.triptrack.data.db.entity.TripCheckpointEntity
import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.data.db.entity.TripPhotoEntity
import java.time.Instant
import java.util.UUID

/** id, start and preview only: the light read places and the fog work from. */
data class TripPreviewRow(val id: UUID, val startDate: Instant, val previewPolyline: ByteArray?)

/** id and preview: what the revealed-layer store reads. */
data class TripPolylineRow(val id: UUID, val previewPolyline: ByteArray?)

/** What the odometer and reward sums need from a trip. */
data class TripDistanceRow(val vehicleId: UUID?, val distance: Double, val source: String?)

data class CoordinateRow(val latitude: Double, val longitude: Double)

data class TimedCoordinateRow(val latitude: Double, val longitude: Double, val timestamp: Instant)

/** A checkpoint together with its trip id, as `checkpointsWithoutPlace` hands them out. */
data class CheckpointWithTrip(val checkpointId: UUID, val tripId: UUID)

/**
 * The trip aggregate: trips and the three tables cascaded from them.
 *
 * Every «world» read goes through `WorldSql.COMPLETED`, never a hand-written copy of it.
 */
@Dao
interface TripDao {
    // MARK: Trips

    @Query("SELECT * FROM TripEntity WHERE id = :id LIMIT 1")
    suspend fun trip(id: UUID): TripEntity?

    @Query("SELECT * FROM TripEntity WHERE id IN (:ids)")
    suspend fun trips(ids: List<UUID>): List<TripEntity>

    @Upsert
    suspend fun upsertTrip(trip: TripEntity)

    @Query("DELETE FROM TripEntity WHERE id = :id")
    suspend fun deleteTrip(id: UUID)

    @Query("SELECT * FROM TripEntity WHERE ${WorldSql.COMPLETED} ORDER BY startDate DESC")
    suspend fun completedTrips(): List<TripEntity>

    @Query("SELECT * FROM TripEntity WHERE ${WorldSql.COMPLETED} ORDER BY startDate DESC LIMIT :limit OFFSET :offset")
    suspend fun completedTrips(limit: Int, offset: Int): List<TripEntity>

    @Query(
        "SELECT * FROM TripEntity WHERE endDate IS NOT NULL AND syncStatus != ${WorldSql.PENDING_DELETE} " +
            "AND confirmation = 'draft' ORDER BY startDate DESC",
    )
    suspend fun draftTrips(): List<TripEntity>

    @Query(
        "SELECT * FROM TripEntity WHERE ${WorldSql.COMPLETED} AND startDate >= :start AND startDate <= :end " +
            "ORDER BY startDate ASC",
    )
    suspend fun completedTripsStarting(start: Instant, end: Instant): List<TripEntity>

    @Query("SELECT * FROM TripEntity WHERE ${WorldSql.COMPLETED} AND startDate >= :start ORDER BY startDate ASC")
    suspend fun completedTripsStarting(start: Instant): List<TripEntity>

    @Query("SELECT COUNT(*) FROM TripEntity WHERE ${WorldSql.COMPLETED} AND isPrivate = 1")
    suspend fun countPrivateCompletedTrips(): Int

    @Query(
        "SELECT * FROM TripEntity WHERE lastModifiedAt > :date AND syncStatus != ${WorldSql.SYNCED} " +
            "ORDER BY lastModifiedAt ASC",
    )
    suspend fun tripsModifiedSince(date: Instant): List<TripEntity>

    @Query("SELECT COUNT(*) FROM TripEntity WHERE ${WorldSql.COMPLETED}")
    suspend fun countCompletedTrips(): Int

    @Query("SELECT startDate FROM TripEntity WHERE ${WorldSql.COMPLETED} ORDER BY startDate DESC LIMIT 1")
    suspend fun lastCompletedStartDate(): Instant?

    @Query("SELECT TOTAL(distance) FROM TripEntity WHERE ${WorldSql.COMPLETED}")
    suspend fun totalCompletedDistance(): Double

    @Query("SELECT * FROM TripEntity WHERE ${WorldSql.COMPLETED} AND vehicleId = :vehicleId ORDER BY startDate DESC")
    suspend fun completedTripsOfVehicle(vehicleId: UUID): List<TripEntity>

    @Query(
        "SELECT vehicleId, distance, source FROM TripEntity WHERE ${WorldSql.COMPLETED} " +
            "AND vehicleId = :vehicleId AND isTransfer = 0",
    )
    suspend fun odometerTrips(vehicleId: UUID): List<TripDistanceRow>

    @Query(
        "SELECT vehicleId, distance, source FROM TripEntity WHERE ${WorldSql.COMPLETED} " +
            "AND vehicleId IS NOT NULL AND isTransfer = 0",
    )
    suspend fun odometerTripsOfAllVehicles(): List<TripDistanceRow>

    @Query("SELECT * FROM TripEntity WHERE syncStatus = ${WorldSql.PENDING_DELETE}")
    suspend fun softDeletedTrips(): List<TripEntity>

    @Query("SELECT * FROM TripEntity WHERE companionsJSON IS NOT NULL")
    suspend fun tripsWithCompanions(): List<TripEntity>

    @Query("SELECT * FROM TripEntity WHERE isPrivate = 0")
    suspend fun publicTrips(): List<TripEntity>

    /** iOS: countLiveTrips. Library size, including `pendingDelete`; drafts never reach the server. */
    @Query("SELECT COUNT(*) FROM TripEntity WHERE endDate IS NOT NULL AND ${WorldSql.NOT_DRAFT}")
    suspend fun countLiveTrips(): Int

    @Query("SELECT id, startDate, previewPolyline FROM TripEntity WHERE ${WorldSql.COMPLETED} ORDER BY startDate DESC")
    suspend fun tripPreviews(): List<TripPreviewRow>

    @Query(
        "SELECT id, startDate, previewPolyline FROM TripEntity WHERE ${WorldSql.COMPLETED} " +
            "AND placesMatchedAt IS NULL ORDER BY startDate DESC",
    )
    suspend fun tripPreviewsNeedingPlaceMatch(): List<TripPreviewRow>

    @Query("UPDATE TripEntity SET placesMatchedAt = :now WHERE id IN (:ids)")
    suspend fun markPlacesMatched(ids: List<UUID>, now: Instant)

    /** iOS: `segmentsJSON CONTAINS[c] uuidString`; SQLite LIKE is case-insensitive for ASCII. */
    @Query("SELECT * FROM TripEntity WHERE segmentsJSON LIKE '%' || :segmentId || '%' LIMIT 1")
    suspend fun tripCarryingSegment(segmentId: String): TripEntity?

    /** iOS: RevealedLayerStore.previewRequest. Oldest first: who drove first owns the geometry. */
    @Query("SELECT id, previewPolyline FROM TripEntity WHERE ${WorldSql.COMPLETED} ORDER BY startDate ASC")
    suspend fun revealPreviews(): List<TripPolylineRow>

    @Query(
        "SELECT id, previewPolyline FROM TripEntity WHERE ${WorldSql.COMPLETED} AND endDate <= :endedBefore " +
            "ORDER BY startDate ASC",
    )
    suspend fun revealPreviews(endedBefore: Instant): List<TripPolylineRow>

    @Query("SELECT id, previewPolyline FROM TripEntity WHERE ${WorldSql.COMPLETED} AND id IN (:ids) ORDER BY startDate ASC")
    suspend fun revealPreviews(ids: List<UUID>): List<TripPolylineRow>

    /** iOS: TripManager.cleanupOrphanedTrips, a recording the app was killed in. */
    @Query("SELECT * FROM TripEntity WHERE endDate IS NULL")
    suspend fun unfinishedTrips(): List<TripEntity>

    @Query("UPDATE TripEntity SET syncStatus = ${WorldSql.PENDING_UPLOAD} WHERE userId = :userId")
    suspend fun markTripsPendingUpload(userId: UUID)

    @Query(
        "SELECT * FROM TripEntity WHERE (syncStatus = ${WorldSql.PENDING_UPLOAD} " +
            "OR syncStatus = ${WorldSql.PENDING_DELETE}) AND userId = :userId",
    )
    suspend fun pendingTrips(userId: UUID): List<TripEntity>

    // MARK: Track points

    /** iOS: TripEntity.orderedTrackPoints. Time, then id, so equal seconds never swap places. */
    @Query("SELECT * FROM TrackPointEntity WHERE tripId = :tripId ORDER BY timestamp ASC, id ASC")
    suspend fun orderedTrackPoints(tripId: UUID): List<TrackPointEntity>

    @Query("SELECT COUNT(*) FROM TrackPointEntity WHERE tripId = :tripId")
    suspend fun countTrackPoints(tripId: UUID): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackPoints(points: List<TrackPointEntity>)

    @Query("DELETE FROM TrackPointEntity WHERE tripId = :tripId")
    suspend fun deleteTrackPoints(tripId: UUID)

    @Query("DELETE FROM TrackPointEntity WHERE id IN (:ids)")
    suspend fun deleteTrackPoints(ids: List<UUID>)

    /** iOS: TerritoryManager.backfillIfNeeded. Only the draft gate: a point without a trip passes. */
    @Query(
        "SELECT p.latitude, p.longitude, p.timestamp FROM TrackPointEntity p " +
            "LEFT JOIN TripEntity t ON p.tripId = t.id WHERE ${WorldSql.TRIP_NOT_DRAFT}",
    )
    suspend fun territoryBackfillPoints(): List<TimedCoordinateRow>

    /** iOS: TerritoryManager.rebuildFromTrips. Points of completed, live, confirmed trips. */
    @Query(
        "SELECT p.latitude, p.longitude FROM TrackPointEntity p " +
            "JOIN TripEntity t ON p.tripId = t.id WHERE ${WorldSql.TRIP_COMPLETED}",
    )
    suspend fun territoryRebuildPoints(): List<CoordinateRow>

    // MARK: Photos

    @Query("SELECT * FROM TripPhotoEntity WHERE tripId = :tripId ORDER BY tripOrder ASC")
    suspend fun photos(tripId: UUID): List<TripPhotoEntity>

    @Query("SELECT * FROM TripPhotoEntity WHERE tripId IN (:tripIds) ORDER BY tripOrder ASC")
    suspend fun photos(tripIds: List<UUID>): List<TripPhotoEntity>

    @Query("SELECT * FROM TripPhotoEntity WHERE id = :id LIMIT 1")
    suspend fun photo(id: UUID): TripPhotoEntity?

    @Query("SELECT COALESCE(MAX(tripOrder) + 1, 0) FROM TripPhotoEntity WHERE tripId = :tripId")
    suspend fun nextPhotoOrder(tripId: UUID): Long

    @Query("SELECT COUNT(*) FROM TripPhotoEntity WHERE tripId = :tripId")
    suspend fun countPhotos(tripId: UUID): Int

    @Upsert
    suspend fun upsertPhoto(photo: TripPhotoEntity)

    @Query("DELETE FROM TripPhotoEntity WHERE id = :id")
    suspend fun deletePhoto(id: UUID)

    @Query("SELECT * FROM TripPhotoEntity WHERE tripId = :tripId AND remoteURL IS NULL")
    suspend fun photosWithoutOriginal(tripId: UUID): List<TripPhotoEntity>

    /** iOS: PhotoStorageService.pendingUploads. */
    @Query("SELECT * FROM TripPhotoEntity WHERE uploadStatus = 0 ORDER BY timestamp ASC")
    suspend fun localOnlyPhotos(): List<TripPhotoEntity>

    /** iOS: PhotoStorageService.pendingOriginalUploads. */
    @Query("SELECT id FROM TripPhotoEntity WHERE thumbnailURL IS NOT NULL AND remoteURL IS NULL")
    suspend fun photosAwaitingOriginal(): List<UUID>

    @Query(
        "UPDATE TripPhotoEntity SET syncStatus = ${WorldSql.PENDING_UPLOAD} " +
            "WHERE tripId IN (SELECT id FROM TripEntity WHERE userId = :userId)",
    )
    suspend fun markPhotosPendingUpload(userId: UUID)

    @Query(
        "SELECT p.* FROM TripPhotoEntity p JOIN TripEntity t ON p.tripId = t.id " +
            "WHERE p.syncStatus = ${WorldSql.PENDING_UPLOAD} AND t.userId = :userId",
    )
    suspend fun pendingPhotos(userId: UUID): List<TripPhotoEntity>

    // MARK: Checkpoints

    @Query("SELECT * FROM TripCheckpointEntity WHERE tripId = :tripId ORDER BY tripOrder ASC")
    suspend fun checkpoints(tripId: UUID): List<TripCheckpointEntity>

    @Query("SELECT * FROM TripCheckpointEntity WHERE tripId IN (:tripIds) ORDER BY tripOrder ASC")
    suspend fun checkpoints(tripIds: List<UUID>): List<TripCheckpointEntity>

    @Query("SELECT * FROM TripCheckpointEntity WHERE id = :id LIMIT 1")
    suspend fun checkpoint(id: UUID): TripCheckpointEntity?

    @Query("SELECT COALESCE(MAX(tripOrder) + 1, 0) FROM TripCheckpointEntity WHERE tripId = :tripId")
    suspend fun nextCheckpointOrder(tripId: UUID): Long

    @Upsert
    suspend fun upsertCheckpoint(checkpoint: TripCheckpointEntity)

    @Upsert
    suspend fun upsertCheckpoints(checkpoints: List<TripCheckpointEntity>)

    @Query("DELETE FROM TripCheckpointEntity WHERE id = :id")
    suspend fun deleteCheckpoint(id: UUID)

    @Query("DELETE FROM TripCheckpointEntity WHERE tripId = :tripId")
    suspend fun deleteCheckpoints(tripId: UUID)

    /** iOS: checkpointsWithoutPlace. A draft's checkpoint waits for «Mine»: entering the world makes its place. */
    @Query(
        "SELECT c.* FROM TripCheckpointEntity c JOIN TripEntity t ON c.tripId = t.id " +
            "WHERE c.placeId IS NULL AND ${WorldSql.TRIP_COMPLETED} ORDER BY c.timestamp ASC",
    )
    suspend fun checkpointsWithoutPlace(): List<TripCheckpointEntity>

    @Query("SELECT latitude, longitude FROM TripCheckpointEntity WHERE placeId = :placeId")
    suspend fun checkpointCoordinates(placeId: UUID): List<CoordinateRow>

    @Query("SELECT DISTINCT placeId FROM TripCheckpointEntity WHERE placeId IS NOT NULL")
    suspend fun checkpointPlaceIds(): List<UUID>

    /** iOS: `photoId == %@ OR photoIdsJSON CONTAINS[c] %@`. */
    @Query(
        "SELECT * FROM TripCheckpointEntity WHERE photoId = :photoId " +
            "OR photoIdsJSON LIKE '%' || :photoIdText || '%'",
    )
    suspend fun checkpointsReferencingPhoto(photoId: UUID, photoIdText: String): List<TripCheckpointEntity>
}
