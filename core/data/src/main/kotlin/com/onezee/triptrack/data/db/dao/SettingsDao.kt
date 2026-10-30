// iOS: SettingsManager.swift (UserSettingsEntity fetches with fetchLimit = 1)
package com.onezee.triptrack.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.onezee.triptrack.data.db.entity.UserSettingsEntity
import java.util.UUID

@Dao
interface SettingsDao {
    /** iOS reads the settings row with `fetchLimit = 1` and no sort; there is one row. */
    @Query("SELECT * FROM UserSettingsEntity LIMIT 1")
    suspend fun settings(): UserSettingsEntity?

    @Upsert
    suspend fun upsert(settings: UserSettingsEntity)

    @Query("DELETE FROM UserSettingsEntity WHERE id = :id")
    suspend fun delete(id: UUID)

    @Query("UPDATE UserSettingsEntity SET syncStatus = ${WorldSql.PENDING_UPLOAD} WHERE id = :userId")
    suspend fun markPendingUpload(userId: UUID)

    @Query("SELECT * FROM UserSettingsEntity WHERE syncStatus = ${WorldSql.PENDING_UPLOAD} LIMIT 1")
    suspend fun pendingSettings(): UserSettingsEntity?
}
