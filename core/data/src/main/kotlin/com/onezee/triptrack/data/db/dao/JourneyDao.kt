// iOS: TripRepository.swift (Journeys, 0.6.6), SyncCoordinator.pendingJourneyOperations
package com.onezee.triptrack.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.onezee.triptrack.data.db.entity.JourneyEntity
import java.util.UUID

@Dao
interface JourneyDao {
    @Query("SELECT * FROM JourneyEntity WHERE id = :id LIMIT 1")
    suspend fun journey(id: UUID): JourneyEntity?

    /** A soft-deleted journey is hidden at once, before the server confirms. */
    @Query("SELECT * FROM JourneyEntity WHERE syncStatus != ${WorldSql.PENDING_DELETE} ORDER BY startDate DESC")
    suspend fun liveJourneys(): List<JourneyEntity>

    @Upsert
    suspend fun upsert(journey: JourneyEntity)

    @Query("DELETE FROM JourneyEntity WHERE id = :id")
    suspend fun delete(id: UUID)

    @Query(
        "SELECT * FROM JourneyEntity WHERE (syncStatus = ${WorldSql.PENDING_UPLOAD} " +
            "OR syncStatus = ${WorldSql.PENDING_DELETE}) AND userId = :userId",
    )
    suspend fun pendingJourneys(userId: UUID): List<JourneyEntity>
}
