// iOS: PlaceStore.swift (CoreDataPlaceStore)
package com.onezee.triptrack.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.onezee.triptrack.data.db.entity.PlaceEntity
import com.onezee.triptrack.data.db.entity.PlacePassEntity
import java.util.UUID

@Dao
interface PlaceDao {
    @Query("SELECT * FROM PlaceEntity WHERE id = :id LIMIT 1")
    suspend fun place(id: UUID): PlaceEntity?

    @Query("SELECT * FROM PlaceEntity ORDER BY createdAt DESC")
    suspend fun places(): List<PlaceEntity>

    @Upsert
    suspend fun upsertPlace(place: PlaceEntity)

    @Query("DELETE FROM PlaceEntity WHERE id = :id")
    suspend fun deletePlace(id: UUID)

    @Query("SELECT * FROM PlacePassEntity WHERE placeId = :placeId ORDER BY timestamp DESC")
    suspend fun passesOfPlace(placeId: UUID): List<PlacePassEntity>

    @Query("SELECT * FROM PlacePassEntity WHERE placeId IN (:placeIds) ORDER BY timestamp DESC")
    suspend fun passesOfPlaces(placeIds: List<UUID>): List<PlacePassEntity>

    @Query("SELECT * FROM PlacePassEntity WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun passesOfTrip(tripId: UUID): List<PlacePassEntity>

    @Query("SELECT COUNT(*) FROM PlacePassEntity WHERE placeId = :placeId")
    suspend fun countPasses(placeId: UUID): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPasses(passes: List<PlacePassEntity>)

    @Query("DELETE FROM PlacePassEntity WHERE placeId = :placeId AND tripId = :tripId")
    suspend fun deletePasses(placeId: UUID, tripId: UUID)

    @Query("DELETE FROM PlacePassEntity WHERE tripId = :tripId")
    suspend fun deletePassesOfTrip(tripId: UUID): Int

    @Query("DELETE FROM PlacePassEntity WHERE placeId = :placeId")
    suspend fun deletePassesOfPlace(placeId: UUID)
}
