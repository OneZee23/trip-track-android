// iOS: TripTrack v24.xcdatamodel, entity TripEntity
package com.onezee.triptrack.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * A trip row, attribute for attribute with Core Data v24.
 *
 * Optional scalar attributes (`usesScalarValueType="YES"`) are non-null here with the model's
 * default: Swift reads them as plain `Double`/`Bool`/`Int`, so NULL is unreachable on iOS too.
 * Everything Core Data keeps as an object (strings, dates, UUIDs, data) keeps its optionality.
 *
 * `syncStatus` defaults to 0, which is `pendingUpload`: a freshly inserted row is pending on iOS as
 * well, and `applyRemoteTrip` has to tell that default apart from a real unsent edit.
 *
 * The to-many relationships (`trackPoints`, `photos`, `checkpoints`) live on the child tables as
 * `tripId` foreign keys with `ON DELETE CASCADE`, the deletion rule of all three.
 */
@Entity(
    tableName = "TripEntity",
    indices = [
        Index(name = "TripEntity_byStartDate", value = ["startDate"], orders = [Index.Order.DESC]),
    ],
)
data class TripEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(defaultValue = "0.0") val averageSpeed: Double = 0.0,
    val badgesJSON: String? = null,
    @ColumnInfo(defaultValue = "0.0") val distance: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val elevation: Double = 0.0,
    val endDate: Instant? = null,
    @ColumnInfo(defaultValue = "0.0") val fuelUsed: Double = 0.0,
    // New trips are private by default; the model default is YES.
    @ColumnInfo(defaultValue = "1") val isPrivate: Boolean = true,
    @ColumnInfo(defaultValue = "0.0") val maxSpeed: Double = 0.0,
    val previewPolyline: ByteArray? = null,
    val region: String? = null,
    val startDate: Instant,
    @ColumnInfo(defaultValue = "0") val titleIsCustom: Boolean = false,
    val title: String? = null,
    val tripDescription: String? = null,
    val lastModifiedAt: Instant? = null,
    @ColumnInfo(defaultValue = "0") val syncStatus: Short = 0,
    @ColumnInfo(defaultValue = "0") val isTransfer: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isTrackProcessed: Boolean = false,
    val fuelCurrency: String? = null,
    val vehicleId: UUID? = null,
    @ColumnInfo(defaultValue = "0") val xpEarned: Int = 0,
    val placesMatchedAt: Instant? = null,
    val userId: UUID? = null,
    val serverCreatedAt: Instant? = null,
    @ColumnInfo(defaultValue = "0") val conflictVersion: Int = 0,
    val companionsJSON: String? = null,
    val recordingBreaksJSON: String? = null,
    val segmentsJSON: String? = null,
    // 0.8.0: recorded|manual. See TripOrigin.
    @ColumnInfo(defaultValue = "recorded") val source: String? = "recorded",
    // 0.8.1: confirmed|draft. An empty column reads as confirmed (every trip before 0.8.1).
    @ColumnInfo(defaultValue = "confirmed") val confirmation: String? = "confirmed",
    // 0.8.3: null is «Auto»; electric | fuel is the person's choice. See TripEnergyMode.stored.
    val energyMode: String? = null,
    // 0.8.1: unchecked|pending|done, the road gap filler's progress.
    @ColumnInfo(defaultValue = "unchecked") val roadFillState: String? = "unchecked",
)
