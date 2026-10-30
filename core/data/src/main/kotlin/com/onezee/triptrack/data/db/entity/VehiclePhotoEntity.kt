// iOS: TripTrack v24.xcdatamodel, entity VehiclePhotoEntity
package com.onezee.triptrack.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * A photo of a car. Deliberately WITHOUT a relationship to the car: `vehicleId` is a plain column,
 * so nothing cascades. Deleting a car (`VehiclePhotoStore.deleteAll`) and the account wipe name
 * these rows and their files explicitly, or the photos outlive the car forever.
 */
@Entity(tableName = "VehiclePhotoEntity")
data class VehiclePhotoEntity(
    @PrimaryKey val id: UUID,
    val vehicleId: UUID? = null,
    @ColumnInfo(defaultValue = "") val filename: String = "",
    @ColumnInfo(defaultValue = "0") val isMain: Boolean = false,
    @ColumnInfo(defaultValue = "0") val sortOrder: Short = 0,
    val timestamp: Instant,
    val sourceTripId: UUID? = null,
    val remoteURL: String? = null,
    val thumbnailURL: String? = null,
    @ColumnInfo(defaultValue = "0") val uploadStatus: Short = 0,
    @ColumnInfo(defaultValue = "0") val syncStatus: Short = 0,
    val lastModifiedAt: Instant? = null,
    val serverCreatedAt: Instant? = null,
)
