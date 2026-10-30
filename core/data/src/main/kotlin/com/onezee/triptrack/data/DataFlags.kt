// iOS: UserDefaults latches (territory_backfill_done, didMigrateUserId, archiveRemovedV1, ...)
package com.onezee.triptrack.data

import android.content.Context
import android.content.SharedPreferences

/**
 * One-shot latches of the data layer. They live outside the database on iOS too (UserDefaults),
 * and the rule they all follow is the one `BackfillLatchTests` pins: a latch records work that was
 * DONE, never «found nothing to do». A latch set on an empty store would make the accident permanent.
 */
interface DataFlags {
    fun get(key: String): Boolean
    fun set(key: String, value: Boolean)

    /** For tests and previews. */
    class InMemory : DataFlags {
        private val values = mutableMapOf<String, Boolean>()
        override fun get(key: String): Boolean = synchronized(values) { values[key] ?: false }
        override fun set(key: String, value: Boolean) {
            synchronized(values) { values[key] = value }
        }
    }
}

/** The app's latches, in their own preferences file. */
class PreferencesDataFlags(private val preferences: SharedPreferences) : DataFlags {
    constructor(context: Context) : this(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE))

    override fun get(key: String): Boolean = preferences.getBoolean(key, false)

    override fun set(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }

    companion object {
        const val FILE_NAME = "triptrack.data.flags"
    }
}
