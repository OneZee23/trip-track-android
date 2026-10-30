// iOS: TripTrack v24.xcdatamodel, entity VehicleEntity
package com.onezee.triptrack.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * A car in the garage. Storage is metric always: kilometres, litres per 100 km, price per litre.
 *
 * The seven v24 garage columns (`drive2URL` … `catalogVariantId`) are nullable on purpose: NULL is
 * «never said» (a row from before v24, an old server), an empty string or `[]` is an explicit
 * clear. The upload reads them through `VehicleSyncPayload.withGarageFields`, never through the
 * convenient non-null `Vehicle` snapshot.
 *
 * `vehicleLevel` is kept for the sync payload only; the shown level is derived from reward
 * kilometres. `manualOdometerKm` is non-scalar on iOS: «not entered» differs from zero.
 */
@Entity(tableName = "VehicleEntity")
data class VehicleEntity(
    @PrimaryKey val id: UUID,
    val drive2URL: String? = null,
    val modTagsJSON: String? = null,
    val paintFinish: String? = null,
    val modsJSON: String? = null,
    val catalogModelId: String? = null,
    val catalogGenerationId: String? = null,
    val catalogVariantId: String? = null,
    val about: String? = null,
    val make: String? = null,
    val model: String? = null,
    @ColumnInfo(defaultValue = "0") val year: Int = 0,
    val bodyType: String? = null,
    @ColumnInfo(defaultValue = "0") val photosVisible: Boolean = false,
    @ColumnInfo(defaultValue = "1") val mapVisible: Boolean = true,
    @ColumnInfo(defaultValue = "0") val isArchived: Boolean = false,
    val soldAt: Instant? = null,
    @ColumnInfo(defaultValue = "🏎️") val avatarEmoji: String? = "🏎️",
    @ColumnInfo(defaultValue = "car") val avatarStyle: String? = "car",
    @ColumnInfo(defaultValue = "10.0") val cityConsumption: Double = 10.0,
    val createdAt: Instant? = null,
    @ColumnInfo(defaultValue = "56.0") val fuelPrice: Double = 56.0,
    @ColumnInfo(defaultValue = "6.0") val highwayConsumption: Double = 6.0,
    val lastModifiedAt: Instant? = null,
    @ColumnInfo(defaultValue = "") val name: String? = "",
    val fuelCurrency: String? = null,
    @ColumnInfo(defaultValue = "app") val dashboardUnits: String? = "app",
    @ColumnInfo(defaultValue = "fuel") val powertrain: String? = "fuel",
    // Zero is «not set», never a real value: nothing is invented for a hybrid.
    @ColumnInfo(defaultValue = "0.0") val electricConsumption: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val electricityPrice: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val electricRangeKm: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val odometerKm: Double = 0.0,
    val manualOdometerKm: Double? = null,
    val plate: String? = null,
    @ColumnInfo(defaultValue = "0") val plateVisible: Boolean = false,
    val stickersJSON: String? = null,
    @ColumnInfo(defaultValue = "0") val syncStatus: Short = 0,
    @ColumnInfo(defaultValue = "1") val vehicleLevel: Int = 1,
    @ColumnInfo(defaultValue = "car") val vehicleType: String? = "car",
    @ColumnInfo(defaultValue = "1") val visibleToOthers: Boolean = true,
    val userId: UUID? = null,
    @ColumnInfo(defaultValue = "0") val conflictVersion: Int = 0,
    // 0.8.0: the garage card background, a PRO cosmetic. null is «no background».
    val cardStyle: String? = null,
)
