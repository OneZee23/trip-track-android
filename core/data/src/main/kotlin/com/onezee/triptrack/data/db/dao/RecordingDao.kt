// iOS: TripManager.swift (cleanupOrphanedTrips, retryGeocodingForUntitledTrips),
// PostTripTrackProcessor.swift (processUnprocessedTrips)
package com.onezee.triptrack.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import java.time.Instant
import java.util.UUID

/** The few reads the recorder needs beyond `TripDao`: an orphan's last fix and the launch sweeps. */
@Dao
interface RecordingDao {
    /** The last recorded moment of a trip; null for a trip without points. */
    @Query("SELECT MAX(timestamp) FROM TrackPointEntity WHERE tripId = :tripId")
    suspend fun lastPointTime(tripId: UUID): Instant?

    /** iOS: processUnprocessedTrips. Finished trips the post-processor has not reached yet. */
    @Query("SELECT id FROM TripEntity WHERE endDate IS NOT NULL AND isTrackProcessed = 0")
    suspend fun unprocessedTripIds(): List<UUID>

    /** iOS: retryGeocodingForUntitledTrips, `title == nil AND trackPoints.@count > 0`. */
    @Query(
        "SELECT t.id FROM TripEntity t WHERE t.title IS NULL AND t.endDate IS NOT NULL " +
            "AND EXISTS (SELECT 1 FROM TrackPointEntity p WHERE p.tripId = t.id)",
    )
    suspend fun untitledTripIds(): List<UUID>
}
