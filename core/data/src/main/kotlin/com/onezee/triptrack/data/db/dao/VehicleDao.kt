// iOS: SettingsManager.swift (vehicle fetches), VehiclePhotoStore.swift, SyncCoordinator.swift (recovery)
package com.onezee.triptrack.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.onezee.triptrack.data.db.entity.VehicleEntity
import com.onezee.triptrack.data.db.entity.VehiclePhotoEntity
import java.util.UUID

@Dao
interface VehicleDao {
    @Query("SELECT * FROM VehicleEntity WHERE id = :id LIMIT 1")
    suspend fun vehicle(id: UUID): VehicleEntity?

    /** The whole garage, sold cars included: old trips still name them. */
    @Query("SELECT * FROM VehicleEntity ORDER BY name ASC")
    suspend fun vehicles(): List<VehicleEntity>

    @Query("SELECT id FROM VehicleEntity")
    suspend fun vehicleIds(): List<UUID>

    @Upsert
    suspend fun upsertVehicle(vehicle: VehicleEntity)

    @Query("DELETE FROM VehicleEntity WHERE id = :id")
    suspend fun deleteVehicle(id: UUID)

    @Query("UPDATE VehicleEntity SET syncStatus = ${WorldSql.PENDING_UPLOAD} WHERE userId = :userId")
    suspend fun markVehiclesPendingUpload(userId: UUID)

    /** iOS: PersistenceController.migrateUserIdIfNeeded. */
    @Query("UPDATE VehicleEntity SET userId = :userId WHERE userId IS NULL")
    suspend fun stampMissingUserId(userId: UUID)

    @Query("SELECT * FROM VehicleEntity WHERE syncStatus = ${WorldSql.PENDING_UPLOAD} AND userId = :userId")
    suspend fun pendingVehicles(userId: UUID): List<VehicleEntity>

    // MARK: Vehicle photos (no relationship: `vehicleId` is a plain column)

    @Query("SELECT * FROM VehiclePhotoEntity WHERE vehicleId = :vehicleId")
    suspend fun photos(vehicleId: UUID): List<VehiclePhotoEntity>

    @Query("SELECT * FROM VehiclePhotoEntity")
    suspend fun allPhotos(): List<VehiclePhotoEntity>

    @Query("SELECT * FROM VehiclePhotoEntity WHERE id = :id LIMIT 1")
    suspend fun photo(id: UUID): VehiclePhotoEntity?

    @Upsert
    suspend fun upsertPhoto(photo: VehiclePhotoEntity)

    @Upsert
    suspend fun upsertPhotos(photos: List<VehiclePhotoEntity>)

    @Query("DELETE FROM VehiclePhotoEntity WHERE id = :id")
    suspend fun deletePhoto(id: UUID)

    /** iOS: SyncCoordinator.pendingVehiclePhotoOperations. Not fully on the server while either link is empty. */
    @Query("SELECT * FROM VehiclePhotoEntity WHERE remoteURL IS NULL OR thumbnailURL IS NULL")
    suspend fun photosNotFullyUploaded(): List<VehiclePhotoEntity>
}
