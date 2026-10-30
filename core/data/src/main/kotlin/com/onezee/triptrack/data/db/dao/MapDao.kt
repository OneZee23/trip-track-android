// iOS: TerritoryManager.swift, TripRepository.cachedGeocode + TripManager.saveGeocodeCache,
// RoadCollectionManager.swift, RevealedLayerStore.swift
package com.onezee.triptrack.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.onezee.triptrack.data.db.entity.GeocodeCacheEntity
import com.onezee.triptrack.data.db.entity.RevealedCellEntity
import com.onezee.triptrack.data.db.entity.RoadEntity
import com.onezee.triptrack.data.db.entity.VisitedGeohashEntity

/** The four relationless map tables: territory, geocoder cache, road collection, opened world. */
@Dao
interface MapDao {
    // MARK: Territory

    @Query("SELECT hash6 FROM VisitedGeohashEntity WHERE hash6 IS NOT NULL")
    suspend fun visitedHashes(): List<String>

    @Insert
    suspend fun insertVisited(rows: List<VisitedGeohashEntity>)

    @Query("DELETE FROM VisitedGeohashEntity")
    suspend fun deleteAllVisited()

    // MARK: Geocoder cache

    @Query("SELECT * FROM GeocodeCacheEntity WHERE geohash5 = :geohash5 LIMIT 1")
    suspend fun geocode(geohash5: String): GeocodeCacheEntity?

    @Query("SELECT * FROM GeocodeCacheEntity WHERE geohash5 IN (:geohash5)")
    suspend fun geocodes(geohash5: List<String>): List<GeocodeCacheEntity>

    @Upsert
    suspend fun upsertGeocode(row: GeocodeCacheEntity)

    @Query("DELETE FROM GeocodeCacheEntity WHERE pk = :pk")
    suspend fun deleteGeocode(pk: Long)

    // MARK: Roads

    @Query("SELECT * FROM RoadEntity ORDER BY lastDriven DESC")
    suspend fun roads(): List<RoadEntity>

    /** iOS: findMatchingRoad, filtered at the database before the similarity check. */
    @Query("SELECT * FROM RoadEntity WHERE startGeohash IN (:hashes) OR endGeohash IN (:hashes)")
    suspend fun roadsTouching(hashes: List<String>): List<RoadEntity>

    @Insert
    suspend fun insertRoad(road: RoadEntity): Long

    @Update
    suspend fun updateRoad(road: RoadEntity)

    // MARK: Opened world

    @Query("SELECT * FROM RevealedCellEntity")
    suspend fun revealedTiles(): List<RevealedCellEntity>

    @Query("SELECT * FROM RevealedCellEntity WHERE `key` = :key LIMIT 1")
    suspend fun revealedTile(key: String): RevealedCellEntity?

    @Query("SELECT `key` FROM RevealedCellEntity WHERE `key` IS NOT NULL")
    suspend fun revealedKeys(): List<String>

    @Upsert
    suspend fun upsertRevealedTiles(rows: List<RevealedCellEntity>)

    @Query("SELECT COUNT(*) FROM RevealedCellEntity")
    suspend fun countRevealedTiles(): Int

    @Query("DELETE FROM RevealedCellEntity")
    suspend fun deleteAllRevealed()
}
