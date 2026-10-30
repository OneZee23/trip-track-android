// iOS: TripTrack v24.xcdatamodel, entities VisitedGeohashEntity, GeocodeCacheEntity, RoadEntity, RevealedCellEntity
package com.onezee.triptrack.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

// Four relationless entities that have no id attribute of their own. Core Data identifies such rows
// by its hidden Z_PK; Room needs that key spelled out, so each gets an auto-generated `pk`.

/** iOS: VisitedGeohashEntity. A geohash-6 tile the person has driven through (territory). */
@Entity(
    tableName = "VisitedGeohashEntity",
    indices = [Index(name = "VisitedGeohashEntity_byHash6", value = ["hash6"])],
)
data class VisitedGeohashEntity(
    @PrimaryKey(autoGenerate = true) val pk: Long = 0,
    val firstVisited: Instant? = null,
    val hash6: String? = null,
    val lastVisited: Instant? = null,
    @ColumnInfo(defaultValue = "0") val visitCount: Int = 0,
)

/** iOS: GeocodeCacheEntity. Reverse geocoder answer per geohash-5 cell, 90-day TTL. */
@Entity(
    tableName = "GeocodeCacheEntity",
    indices = [Index(name = "GeocodeCacheEntity_byGeohash5", value = ["geohash5"])],
)
data class GeocodeCacheEntity(
    @PrimaryKey(autoGenerate = true) val pk: Long = 0,
    val cachedAt: Instant,
    @ColumnInfo(defaultValue = "") val geohash5: String = "",
    val locality: String? = null,
    val region: String? = null,
)

/** iOS: RoadEntity. A road of the collection; never restored by sync. Kilometres are metric. */
@Entity(tableName = "RoadEntity")
data class RoadEntity(
    @PrimaryKey(autoGenerate = true) val pk: Long = 0,
    @ColumnInfo(defaultValue = "0.0") val distanceKm: Double = 0.0,
    val endGeohash: String? = null,
    val firstDriven: Instant? = null,
    // Comma-separated geohash-5 cells in driving order.
    val geohashSequence: String? = null,
    val id: UUID? = null,
    val lastDriven: Instant? = null,
    @ColumnInfo(defaultValue = "1") val level: Int = 1,
    val name: String? = null,
    @ColumnInfo(defaultValue = "common") val rarity: String? = "common",
    val startGeohash: String? = null,
    @ColumnInfo(defaultValue = "1") val timesDriven: Int = 1,
)

/**
 * iOS: RevealedCellEntity (v16). One geohash-5 tile of the opened world: 75 m cells delta-coded in
 * `cells`, the claimed corridors in `geometry`. The model has a fetch index AND a uniqueness
 * constraint on `key`; in SQLite one unique index is both. NULL keys stay possible, as in Core Data.
 */
@Entity(
    tableName = "RevealedCellEntity",
    indices = [Index(name = "RevealedCellEntity_byKey", value = ["key"], unique = true)],
)
data class RevealedCellEntity(
    @PrimaryKey(autoGenerate = true) val pk: Long = 0,
    @ColumnInfo(defaultValue = "0") val cellCount: Int = 0,
    val cells: ByteArray? = null,
    val geometry: ByteArray? = null,
    val key: String? = null,
    val updatedAt: Instant? = null,
    @ColumnInfo(defaultValue = "1") val version: Short = 1,
)
