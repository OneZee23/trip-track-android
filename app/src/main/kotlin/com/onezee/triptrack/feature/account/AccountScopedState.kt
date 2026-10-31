// iOS: AuthService.swift (purgeAccountScopedState, demotePendingPublicTripsToPrivate,
// demotePendingPublicJourneysToPrivate)
package com.onezee.triptrack.feature.account

import androidx.room.withTransaction
import com.onezee.triptrack.data.TripRepository
import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.feature.settings.PresentationPreferences
import com.onezee.triptrack.model.SyncStatus
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The settings an account owns on this phone, as the iOS keys store them: the Cloud Sync switch
 * and its first-time consent marker, and the website globe opt-in. [onChange] lets the settings
 * screen re-read them.
 */
class AccountSettings(private val preferences: PresentationPreferences, private val onChange: () -> Unit = {}) {
    val cloudSyncEnabled: Boolean get() = preferences.string(CLOUD_KEY)?.toBooleanStrictOrNull() ?: false

    /** Cloud Sync OFF; the consent once given stays (iOS: «clear server data»). */
    fun switchCloudSyncOff() {
        preferences.set(CLOUD_KEY, "false")
        onChange()
    }

    /** Cloud Sync OFF and the GDPR marker cleared, so the next account makes its own choice. */
    fun resetCloudSync() {
        preferences.set(CLOUD_KEY, "false")
        preferences.set(FIRST_TOGGLE_KEY, "false")
        onChange()
    }

    fun setShowOnPublicMap(enabled: Boolean) {
        preferences.set(PUBLIC_MAP_KEY, enabled.toString())
        onChange()
    }

    companion object {
        // The same raw keys SettingsManager and CloudSyncView read: iOS UserDefaults keys.
        const val CLOUD_KEY = "com.triptrack.settings.cloudSyncEnabled"
        const val PUBLIC_MAP_KEY = "com.triptrack.settings.showOnPublicMap"
        const val FIRST_TOGGLE_KEY = "com.triptrack.sync.firstToggleShown"
    }
}

/**
 * iOS: AuthService.purgeAccountScopedState. Everything on this phone that belongs to the ACCOUNT
 * rather than the phone. Runs on every hard sign-out and, from `prepareForIdentity`, when a
 * DIFFERENT account signs in after a soft expiry, which deliberately preserved all of this for the
 * same person. The trip library is the phone's and stays.
 *
 * The sync queue is cleared by the caller before this runs, so nothing of the old account drains
 * while it is being taken apart. Screens that keep account caches of their own (feed, inbox,
 * companions, profile journal and photos) add their step with [register].
 */
class AccountScopedState(
    private val db: TripTrackDatabase,
    private val trips: TripRepository,
    private val settings: AccountSettings,
    private val latch: ProfileSyncLatch,
) {
    private val steps = CopyOnWriteArrayList<suspend () -> Unit>()

    /** A feature's own account-scoped cleanup, run after the shared steps. */
    fun register(step: suspend () -> Unit) { steps += step }

    suspend fun purge() {
        // The next account has pushed nothing yet; a stale «confirmed» would swallow its first push.
        latch.reset()
        // The globe opt-in mirror: account A's switch must not show on account B before B's login
        // read-back seeds the real value.
        settings.setShowOnPublicMap(false)
        // Journeys before trips, as on iOS.
        demotePendingPublicJourneysToPrivate()
        demotePendingPublicTripsToPrivate()
        // The companions roster cached on this phone's trips is per account.
        trips.clearCompanionsCache()
        settings.resetCloudSync()
        for (step in steps) step()
    }

    /**
     * A trip made public here but never uploaded goes back to private: otherwise the next account's
     * pending recovery would upload it under the wrong person. Trips already on the server are left
     * to the sign-out dialog («Hide public and sign out»).
     */
    suspend fun demotePendingPublicTripsToPrivate() = db.withTransaction {
        for (row in db.tripDao().publicTrips()) {
            if (row.serverCreatedAt != null || row.syncStatus != SyncStatus.pendingUpload.rawValue) continue
            db.tripDao().upsertTrip(row.copy(isPrivate = true, syncStatus = SyncStatus.synced.rawValue))
        }
    }

    /** The journey mirror of [demotePendingPublicTripsToPrivate]. */
    suspend fun demotePendingPublicJourneysToPrivate() = db.withTransaction {
        for (row in db.journeyDao().liveJourneys()) {
            if (row.isPrivate || row.serverCreatedAt != null || row.syncStatus != SyncStatus.pendingUpload.rawValue) continue
            db.journeyDao().upsert(row.copy(isPrivate = true, syncStatus = SyncStatus.synced.rawValue))
        }
    }
}
