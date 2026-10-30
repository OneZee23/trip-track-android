// iOS: TripTrack v24.xcdatamodel, entity DiscoveryEntity
package com.onezee.triptrack.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * A find (0.7.0, history fields v18). `id` is UUID v5 of `"<kind>:<key>"`, unique, so two phones that
 * parse the same track stamp one seal. The model's `byId` fetch index and uniqueness constraint are
 * both the primary key here. Relationless: the account wipe names it.
 *
 * `finders` is non-scalar on iOS: nil is «we did not ask», not «nobody found it».
 */
@Entity(tableName = "DiscoveryEntity")
data class DiscoveryEntity(
    @PrimaryKey val id: UUID,
    val finders: Int? = null,
    val firstFinderAt: Instant? = null,
    val firstFinderName: String? = null,
    val foundAt: Instant,
    val key: String,
    val kind: String,
    @ColumnInfo(defaultValue = "0.0") val latitude: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val longitude: Double = 0.0,
    val rarity: String? = null,
    val story: String? = null,
    val symbol: String,
    val title: String? = null,
    val tripId: UUID,
    @ColumnInfo(defaultValue = "0") val verified: Boolean = false,
)
