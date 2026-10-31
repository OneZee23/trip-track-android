// iOS: NotificationCenter publishers, UserDefaults in the screen view models.
package com.onezee.triptrack.feature.settings

import android.content.SharedPreferences
import androidx.room.InvalidationTracker
import com.onezee.triptrack.data.db.TripTrackDatabase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// iOS: Notification.Name (tripRecordingEnded, syncPullCompleted, placesChanged, homeChanged).
enum class WorldChange { trips, places, territory, revealed, discoveries, sync, home, settings, garage }

// iOS: NotificationCenter. Recording/sync owners supply their events without a service dependency.
interface WorldChanges { val events: Flow<WorldChange> }

// iOS: managed-object/manager change notifications. No global singleton is needed by a screen.
class RoomWorldChanges(private val database: TripTrackDatabase) : WorldChanges {
    override val events: Flow<WorldChange> = callbackFlow {
        val observer = object : InvalidationTracker.Observer(
            "TripEntity", "PlaceEntity", "PlacePassEntity", "VisitedGeohashEntity", "RevealedCellEntity",
            "DiscoveryEntity", "VehicleEntity", "UserSettingsEntity", "JourneyEntity",
        ) {
            override fun onInvalidated(tables: Set<String>) {
                tables.mapNotNull { table -> when (table) {
                    "TripEntity", "JourneyEntity" -> WorldChange.trips
                    "PlaceEntity", "PlacePassEntity" -> WorldChange.places
                    "VisitedGeohashEntity" -> WorldChange.territory
                    "RevealedCellEntity" -> WorldChange.revealed
                    "DiscoveryEntity" -> WorldChange.discoveries
                    "VehicleEntity" -> WorldChange.garage
                    "UserSettingsEntity" -> WorldChange.settings
                    else -> null
                } }.distinct().forEach { trySend(it) }
            }
        }
        database.invalidationTracker.addObserver(observer)
        awaitClose { database.invalidationTracker.removeObserver(observer) }
    }
}

// iOS: UserDefaults. Raw keys remain the iOS storage contract.
interface PresentationPreferences {
    fun string(key: String): String?
    fun set(key: String, value: String?)
}

// iOS: UserDefaults.standard.
class AndroidPresentationPreferences(private val preferences: SharedPreferences) : PresentationPreferences {
    override fun string(key: String) = preferences.getString(key, null)
    override fun set(key: String, value: String?) { preferences.edit().putString(key, value).apply() }
}
