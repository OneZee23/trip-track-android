// iOS: DiscoveryStore.swift
package com.onezee.triptrack.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.onezee.triptrack.data.db.entity.DiscoveryEntity
import java.util.UUID

@Dao
interface DiscoveryDao {
    @Query("SELECT * FROM DiscoveryEntity WHERE id = :id LIMIT 1")
    suspend fun discovery(id: UUID): DiscoveryEntity?

    @Query("SELECT * FROM DiscoveryEntity WHERE id IN (:ids)")
    suspend fun discoveries(ids: List<UUID>): List<DiscoveryEntity>

    @Query("SELECT * FROM DiscoveryEntity ORDER BY foundAt DESC")
    suspend fun all(): List<DiscoveryEntity>

    @Query("SELECT * FROM DiscoveryEntity WHERE kind = :kind ORDER BY foundAt DESC")
    suspend fun ofKind(kind: String): List<DiscoveryEntity>

    /** The first find wins: a row that is already there is never replaced by a later one. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(discoveries: List<DiscoveryEntity>): List<Long>

    @Update
    suspend fun update(discovery: DiscoveryEntity)

    @Update
    suspend fun update(discoveries: List<DiscoveryEntity>)

    @Query("DELETE FROM DiscoveryEntity")
    suspend fun deleteAll()
}
