// iOS: SettingsManager.swift (loadSettings, saveSettings), PersistenceController.migrateUserIdIfNeeded
package com.onezee.triptrack.data

import androidx.room.withTransaction
import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.data.db.entity.UserSettingsEntity
import java.time.Instant
import java.util.UUID

/** iOS: the settings half of SettingsManager. The row's id is the local user id. */
interface SettingsStore {
    suspend fun settings(): UserSettingsEntity?
    /** The first launch creates the row with a fresh identity. */
    suspend fun loadOrCreate(): UserSettingsEntity
    /** Writes and stamps `lastModifiedAt`; `syncStatus` is left alone. Null when there is no row. */
    suspend fun save(transform: (UserSettingsEntity) -> UserSettingsEntity): UserSettingsEntity?
    /** iOS: seedUnitsAtLaunch/seedDistanceUnitFromRegion. A guess is not a person's edit. */
    suspend fun seedUnits(distance: String?, volume: String?): UserSettingsEntity
    suspend fun migrateUserIdIfNeeded(userId: UUID, flags: DataFlags)

    companion object {
        const val USER_ID_MIGRATION_KEY = "didMigrateUserId"
    }
}

/**
 * The settings row. Its id is the local user id every entity is stamped with.
 *
 * [save] stamps `lastModifiedAt` and leaves `syncStatus` alone, exactly as iOS: the row is
 * `pendingUpload` from birth, so the stamp, not the flag, is what lets `applyRemoteSettings` tell a
 * fresh local edit from its own old copy. Before 0.6.7 nothing stamped the row, every local edit
 * looked a year old, and every pull wiped it. Scheduling the upload (five seconds later on iOS) is
 * the caller's.
 */
class RoomSettingsStore(
    private val db: TripTrackDatabase,
    private val clock: () -> Instant = Instant::now,
) : SettingsStore {
    private val dao = db.settingsDao()

    override suspend fun settings(): UserSettingsEntity? = dao.settings()

    /** iOS: loadSettings. The first launch creates the row with a fresh identity. */
    override suspend fun loadOrCreate(): UserSettingsEntity = db.withTransaction {
        dao.settings() ?: UserSettingsEntity(id = UUID.randomUUID()).also { dao.upsert(it) }
    }

    override suspend fun seedUnits(distance: String?, volume: String?): UserSettingsEntity = db.withTransaction {
        val current = loadOrCreate()
        current.copy(distanceUnit = distance ?: current.distanceUnit, volumeUnit = volume ?: current.volumeUnit)
            .also { dao.upsert(it) }
    }

    /** iOS: saveSettings. Every path to saving carries the stamp, including ones nobody routed here. */
    override suspend fun save(transform: (UserSettingsEntity) -> UserSettingsEntity): UserSettingsEntity? = db.withTransaction {
        val current = dao.settings() ?: return@withTransaction null
        val saved = transform(current).copy(id = current.id, lastModifiedAt = clock())
        dao.upsert(saved)
        saved
    }

    /**
     * iOS: PersistenceController.migrateUserIdIfNeeded. Trips and cars from before the identity
     * existed get it once. Cars created later stay without it on iOS as well, which is why the
     * vehicle photo recovery does not filter by user.
     */
    override suspend fun migrateUserIdIfNeeded(userId: UUID, flags: DataFlags) {
        if (flags.get(SettingsStore.USER_ID_MIGRATION_KEY)) return
        db.withTransaction {
            db.tripDao().stampMissingUserId(userId)
            db.vehicleDao().stampMissingUserId(userId)
        }
        flags.set(SettingsStore.USER_ID_MIGRATION_KEY, true)
    }
}
