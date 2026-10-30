// iOS: TripTrack v24.xcdatamodel, entity JourneyEntity
package com.onezee.triptrack.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * A journey (0.6.6): a date window over one's own trips, not a container. No relationships at all:
 * membership is the window minus `excludedTripIdsJSON`, so a trip deletion cascades nowhere and the
 * account wipe names this table explicitly.
 *
 * `conflictVersion` defaults to 1 here, unlike every other entity.
 */
@Entity(
    tableName = "JourneyEntity",
    indices = [Index(name = "JourneyEntity_byStartDate", value = ["startDate"], orders = [Index.Order.DESC])],
)
data class JourneyEntity(
    @PrimaryKey val id: UUID,
    val userId: UUID? = null,
    val title: String? = null,
    val startDate: Instant,
    val endDate: Instant? = null,
    val excludedTripIdsJSON: String? = null,
    val coverPhotoId: UUID? = null,
    @ColumnInfo(defaultValue = "1") val isPrivate: Boolean = true,
    @ColumnInfo(defaultValue = "0") val syncStatus: Short = 0,
    @ColumnInfo(defaultValue = "1") val conflictVersion: Int = 1,
    val lastModifiedAt: Instant? = null,
    val serverCreatedAt: Instant? = null,
    val createdAt: Instant? = null,
)
