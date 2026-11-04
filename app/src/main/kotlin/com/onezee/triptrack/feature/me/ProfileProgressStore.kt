// iOS: SettingsManager.applyRemoteProfileProgress / profileProgressForSync
package com.onezee.triptrack.feature.me

import androidx.room.withTransaction
import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.logic.LevelSystem
import com.onezee.triptrack.logic.GamificationManager
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.data.db.entity.UserSettingsEntity
import com.onezee.triptrack.model.ProfileProgress

// iOS: ProfileProgress.init(entity:). An unknown XP stays absent instead of becoming zero on the wire.
internal fun profileProgress(row: UserSettingsEntity): ProfileProgress {
    val xp = row.profileXP.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    val level = maxOf(row.profileLevel, LevelSystem.level(xp))
    return ProfileProgress(level.takeIf { xp > 0 || level > 1 }, xp.takeIf { it > 0 },
        row.currentStreak.takeIf { row.lastTripDate != null }, row.bestStreak.takeIf { it > 0 })
}

// iOS: SettingsManager.swift. Reconciliation is earned progress, never a stamped user edit.
class ProfileProgressStore(private val database: TripTrackDatabase) {
    suspend fun backfillIfNeeded(trips: List<Trip>) = database.withTransaction {
        val current = database.settingsDao().settings() ?: return@withTransaction
        if (current.profileXP != 0L) return@withTransaction
        val xp = GamificationManager.backfillXP(trips).toLong()
        if (xp > 0) database.settingsDao().upsert(current.copy(profileXP = xp, profileLevel = maxOf(current.profileLevel, LevelSystem.level(xp.toInt()))))
    }

    suspend fun merge(level: Int?, xp: Int?, canApply: () -> Boolean = { true }) = database.withTransaction {
        val current = database.settingsDao().settings() ?: return@withTransaction
        if (!canApply()) return@withTransaction
        val mergedXp = maxOf(current.profileXP, (xp ?: 0).toLong())
        val mergedLevel = maxOf(current.profileLevel, level ?: 1, LevelSystem.level(mergedXp.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()))
        if (mergedXp != current.profileXP || mergedLevel != current.profileLevel)
            database.settingsDao().upsert(current.copy(profileXP = mergedXp, profileLevel = mergedLevel))
    }
}
