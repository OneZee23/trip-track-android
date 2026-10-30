// iOS: TripTrack v24.xcdatamodel, entities PlaceEntity, PlacePassEntity
package com.onezee.triptrack.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * iOS: PlaceEntity (0.6.8). A point the app recognises on every pass; phone-only, never synced.
 * `id` is UUID v5 of the geohash-7 `cell` (`Place.id(forCell:)`), so two phones agree without sync.
 */
@Entity(
    tableName = "PlaceEntity",
    indices = [Index(name = "PlaceEntity_byCell", value = ["cell"])],
)
data class PlaceEntity(
    @PrimaryKey val id: UUID,
    val cell: String,
    @ColumnInfo(defaultValue = "0.0") val latitude: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val longitude: Double = 0.0,
    val name: String? = null,
    val createdAt: Instant,
)

/**
 * iOS: PlacePassEntity (0.6.8). One pass of a trip through a place. Relationless, like
 * `JourneyEntity`: deleting a trip does not cascade here, so all three trip deletion paths call
 * `PlaceStore.deletePasses(tripId)` themselves.
 */
@Entity(
    tableName = "PlacePassEntity",
    indices = [
        Index(name = "PlacePassEntity_byPlace", value = ["placeId"]),
        Index(name = "PlacePassEntity_byTrip", value = ["tripId"]),
    ],
)
data class PlacePassEntity(
    @PrimaryKey val id: UUID,
    val placeId: UUID,
    val tripId: UUID,
    val timestamp: Instant,
    @ColumnInfo(defaultValue = "0.0") val elapsedFromStart: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val distanceFromStart: Double = 0.0,
    // Degrees; -1 is unknown (PlacePass.unknownCourse).
    @ColumnInfo(defaultValue = "-1.0") val course: Double = -1.0,
)
