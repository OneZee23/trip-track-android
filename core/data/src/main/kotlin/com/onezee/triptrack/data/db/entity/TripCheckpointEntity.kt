// iOS: TripTrack v24.xcdatamodel, entity TripCheckpointEntity
package com.onezee.triptrack.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * A checkpoint on the route (0.6.5). It travels to the server inside its trip, so every edit is an
 * edit of the trip and flips the trip's `pendingUpload` (`markCheckpointsChanged`).
 *
 * `photoIdsJSON` holds attached photos as a JSON array of uuid strings, not a relationship: a
 * photo may arrive from the other phone later than the checkpoint. `placeId` is derived locally
 * (0.6.8) and is never taken away by a pull. Exact zeros in `latitude`/`longitude` mean «no
 * coordinate» (a checkpoint set inside the home privacy zone, 0.8.3).
 *
 * Ordered relationship position: `tripOrder`, as for photos.
 */
@Entity(
    tableName = "TripCheckpointEntity",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(name = "TripCheckpointEntity_trip", value = ["tripId"])],
)
data class TripCheckpointEntity(
    @PrimaryKey val id: UUID,
    val createdAt: Instant? = null,
    @ColumnInfo(defaultValue = "0.0") val distanceFromStart: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val elapsedFromStart: Double = 0.0,
    val lastModifiedAt: Instant? = null,
    @ColumnInfo(defaultValue = "0.0") val latitude: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val longitude: Double = 0.0,
    val name: String? = null,
    val photoId: UUID? = null,
    val photoIdsJSON: String? = null,
    val placeId: UUID? = null,
    @ColumnInfo(defaultValue = "0") val syncStatus: Short = 0,
    val timestamp: Instant,
    val userId: UUID? = null,
    val tripId: UUID? = null,
    @ColumnInfo(defaultValue = "0") val tripOrder: Long = 0,
)
