// iOS: TripTrack v24.xcdatamodel, entity TrackPointEntity
package com.onezee.triptrack.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * One recorded fix. Since v23 the relationship to the trip is UNORDERED: order is time, then id,
 * and it is restored on read (`TripDao.orderedTrackPoints`, iOS `orderedTrackPoints`).
 *
 * `tripId` is the Core Data relationship `trip`: optional, cascade-deleted with the trip.
 * `byTripTimestamp` is the v23 index that serves exactly that ordered read.
 */
@Entity(
    tableName = "TrackPointEntity",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(name = "TrackPointEntity_byTripTimestamp", value = ["tripId", "timestamp"])],
)
data class TrackPointEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(defaultValue = "0.0") val altitude: Double = 0.0,
    // Degrees; -1 means unknown, as in CoreLocation.
    @ColumnInfo(defaultValue = "-1.0") val course: Double = -1.0,
    // Since 0.8.1 the RAW GPS accuracy, not the filter's estimate: the odometer gate reads it.
    @ColumnInfo(defaultValue = "0.0") val horizontalAccuracy: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val isInterpolated: Boolean = false,
    @ColumnInfo(defaultValue = "0.0") val latitude: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val longitude: Double = 0.0,
    // Metres per second; negative means a filled gap without a measured speed.
    @ColumnInfo(defaultValue = "0.0") val speed: Double = 0.0,
    val timestamp: Instant,
    val tripId: UUID? = null,
)
