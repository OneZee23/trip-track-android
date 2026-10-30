// iOS: SettingsManager.shared.cloudSyncEnabled / localUserId, HomeSettings.load().activeZone,
// FuelCurrency.current, Date()
package com.onezee.triptrack.data

import java.time.Instant
import java.util.UUID

/**
 * What the iOS repository reads from singletons, handed in instead.
 *
 * Every lambda is read FRESH on each call, the way iOS reads `SettingsManager.shared`: Cloud Sync
 * can be switched while a screen is open, and a value captured at construction would decide the
 * `pendingUpload` flag by yesterday's switch.
 */
class DataEnvironment(
    /** Cloud Sync switch. With it off, a private trip's edit cannot drain, so it is not flagged. */
    val cloudSyncEnabled: () -> Boolean = { false },
    /** The per-install identity every row is stamped with (`UserSettingsEntity.id`). */
    val localUserId: () -> UUID,
    /** True while the home privacy zone is on: the server's preview echo is trimmed then. */
    val homeZoneActive: () -> Boolean = { false },
    /** The currency a car without its own symbol shows; € until the settings are loaded. */
    val fuelCurrency: () -> String = { "€" },
    val clock: () -> Instant = Instant::now,
)
