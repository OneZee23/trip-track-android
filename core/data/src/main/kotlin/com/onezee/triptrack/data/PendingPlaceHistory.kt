// iOS: PlaceManager.swift (pendingHistoryIds, places.pendingHistory in UserDefaults)
package com.onezee.triptrack.data

import android.annotation.SuppressLint
import android.content.SharedPreferences
import java.util.Locale
import java.util.UUID
import org.json.JSONArray

interface PendingPlaceHistory {
    var ids: List<UUID>
}

/** Ordered IDs, committed on IO before the recording checkpoint returns: process death loses none. */
class PreferencesPendingPlaceHistory(private val preferences: SharedPreferences) : PendingPlaceHistory {
    override var ids: List<UUID>
        get() {
            val array = JSONArray(preferences.getString(KEY, null) ?: "[]")
            return (0 until array.length()).mapNotNull { index ->
                runCatching { UUID.fromString(array.getString(index)) }.getOrNull()
            }
        }
        @SuppressLint("ApplySharedPref")
        set(value) { check(preferences.edit().putString(KEY, JSONArray(value.map { it.toString().uppercase(Locale.ROOT) }).toString()).commit()) }

    companion object { const val KEY = "places.pendingHistory" }
}
