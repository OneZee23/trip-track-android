// iOS: TripTrack v24.xcdatamodel, entity UserSettingsEntity
package com.onezee.triptrack.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * The single settings row. `id` IS the local user id every entity is stamped with, so it only ever
 * moves when the fresher settings row says so (`applyRemoteSettings`).
 *
 * `lastModifiedAt` is set by every local save (0.6.7): it is what tells a pending edit from an old
 * copy, since `syncStatus` is 0 (`pendingUpload`) from birth. Progress (`profileXP`, `profileLevel`,
 * `bestStreak`) only ever goes up on a pull.
 *
 * The account wipe does NOT touch this row: theme, language and units are the app's preferences,
 * not the person's data.
 */
@Entity(tableName = "UserSettingsEntity")
data class UserSettingsEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(defaultValue = "🏎️") val avatarEmoji: String? = "🏎️",
    @ColumnInfo(defaultValue = "0") val bestStreak: Int = 0,
    @ColumnInfo(defaultValue = "0") val currentStreak: Int = 0,
    // A contract with the server and the second phone; "km" is the column default since v1.
    @ColumnInfo(defaultValue = "km") val distanceUnit: String? = "km",
    @ColumnInfo(defaultValue = "7.8") val fuelConsumption: Double = 7.8,
    @ColumnInfo(defaultValue = "€") val fuelCurrency: String? = "€",
    @ColumnInfo(defaultValue = "56.0") val fuelPrice: Double = 56.0,
    @ColumnInfo(defaultValue = "ru") val language: String? = "ru",
    val lastModifiedAt: Instant? = null,
    val lastTripDate: Instant? = null,
    @ColumnInfo(defaultValue = "1") val profileLevel: Int = 1,
    @ColumnInfo(defaultValue = "0") val profileXP: Long = 0,
    val selectedVehicleId: UUID? = null,
    @ColumnInfo(defaultValue = "0") val syncStatus: Short = 0,
    @ColumnInfo(defaultValue = "dark") val themeMode: String? = "dark",
    @ColumnInfo(defaultValue = "liters") val volumeUnit: String? = "liters",
    @ColumnInfo(defaultValue = "0") val conflictVersion: Int = 0,
    // 0.8.0: avatar frame and PRO badge; they travel through /auth/profile-update, not this row.
    val avatarFrame: String? = null,
    @ColumnInfo(defaultValue = "1") val showPlusBadge: Boolean = true,
)
