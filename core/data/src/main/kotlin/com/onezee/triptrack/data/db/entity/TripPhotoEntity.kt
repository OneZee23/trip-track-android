// iOS: TripTrack v24.xcdatamodel, entity TripPhotoEntity
package com.onezee.triptrack.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * A photo attached to a trip; the JPEG itself lives on disk (`TripPhotoFiles`).
 *
 * `capturedAt` and `exifLatitude`/`exifLongitude` are the capture metadata read from the picker
 * (0.6.5). They are more precise than the server's copy and a pull never overwrites them.
 *
 * `TripEntity.photos` is an ORDERED relationship in Core Data, which keeps the position in a hidden
 * column. Room has no ordered relationships, so the position is the explicit `tripOrder`, assigned
 * when the photo joins the trip (see `docs/divergences.md`).
 */
@Entity(
    tableName = "TripPhotoEntity",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // Core Data indexes every to-one foreign key column on its own.
    indices = [Index(name = "TripPhotoEntity_trip", value = ["tripId"])],
)
data class TripPhotoEntity(
    @PrimaryKey val id: UUID,
    val caption: String? = null,
    val capturedAt: Instant? = null,
    val exifLatitude: Double? = null,
    val exifLongitude: Double? = null,
    @ColumnInfo(defaultValue = "") val filename: String = "",
    val lastModifiedAt: Instant? = null,
    @ColumnInfo(defaultValue = "0") val sortOrder: Short = 0,
    @ColumnInfo(defaultValue = "0") val syncStatus: Short = 0,
    val timestamp: Instant,
    val remoteURL: String? = null,
    // PhotoUploadStatus raw value; 0 is localOnly.
    @ColumnInfo(defaultValue = "0") val uploadStatus: Short = 0,
    val thumbnailURL: String? = null,
    val serverCreatedAt: Instant? = null,
    val tripId: UUID? = null,
    @ColumnInfo(defaultValue = "0") val tripOrder: Long = 0,
)
