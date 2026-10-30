package com.onezee.triptrack.sync

import android.content.SharedPreferences
import androidx.room.withTransaction
import com.onezee.triptrack.data.db.TripTrackDatabase
import java.io.IOException
import java.time.Instant
import java.util.UUID

// iOS: LastSyncedAtStore.swift. Reading never adopts a different store's identity.
class LastSyncedAtStore(private val preferences: SharedPreferences, private val apiScope: String) {
    private fun key(account: UUID) = "com.triptrack.sync.lastSyncedAt.$apiScope.$account"
    fun get(account: UUID, storeIdentity: String?): Instant? {
        if (storeIdentity == null || preferences.getString(key(account) + ".store", null) != storeIdentity) return null
        return preferences.getString(key(account), null)?.let { runCatching { Instant.parse(it) }.getOrNull() }
    }
    fun set(date: Instant, account: UUID, storeIdentity: String?) {
        if (storeIdentity == null) return
        if (!preferences.edit().putString(key(account) + ".store", storeIdentity).putString(key(account), date.toString()).commit()) {
            throw IOException("Could not persist sync cursor")
        }
    }
    fun reset(account: UUID) {
        if (!preferences.edit().remove(key(account)).remove(key(account) + ".store").commit()) throw IOException("Could not reset sync cursor")
    }
    fun healedStore(account: UUID): String? = preferences.getString(key(account) + ".healed", null)
    fun markHealed(account: UUID, storeIdentity: String) {
        if (!preferences.edit().putString(key(account) + ".healed", storeIdentity).commit()) throw IOException("Could not persist heal latch")
    }
}

// iOS: PersistenceController.storeIdentity (NSStoreUUIDKey). Born inside SQLite, never copied from preferences.
object SyncStoreIdentity {
    suspend fun get(db: TripTrackDatabase): String = db.withTransaction {
        // Room's identity hash identifies a SCHEMA, not this database. A separate metadata table
        // gives even an empty/replaced store its own identity without changing domain entities.
        val sqlite = db.openHelper.writableDatabase
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS SyncStoreIdentity (id INTEGER PRIMARY KEY CHECK(id = 1), identity TEXT NOT NULL)")
        sqlite.execSQL("INSERT OR IGNORE INTO SyncStoreIdentity (id, identity) VALUES (1, ?)", arrayOf(UUID.randomUUID().toString()))
        sqlite.query("SELECT identity FROM SyncStoreIdentity WHERE id = 1").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(0)
        }
    }
}
