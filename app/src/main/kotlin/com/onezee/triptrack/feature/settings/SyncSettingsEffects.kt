// iOS: CloudSyncView.enableCloudSync, HomeManager.homePrivacyZoneChanged, AutoTripService settings publishers
package com.onezee.triptrack.feature.settings

import com.onezee.triptrack.data.SettingsStore
import com.onezee.triptrack.data.sync.*
import com.onezee.triptrack.model.*
import com.onezee.triptrack.sync.SyncEngine

// iOS: AutoTripService.swift. The recording owner applies settings to its current detectors.
fun interface RecordingSettings {
    suspend fun apply(mode: AutoRecordMode, timeout: Int, stereos: List<SavedBluetoothDevice>)
}

// iOS: CloudSyncView.swift / SyncCoordinator.swift. Gates, photo recovery and retries remain in core:sync.
class SyncSettingsEffects(
    private val syncEngine: SyncEngine,
    private val settingsStore: SettingsStore,
    private val recordingSettings: RecordingSettings,
    private val profilePrivacyService: ProfilePrivacyService,
) : SettingsEffects {
    override suspend fun cloudSyncChanged(enabled: Boolean) {
        if (!enabled) { syncEngine.clearAccountWork(); return }
        syncEngine.trips.markAllPendingUpload()
        for (trip in syncEngine.trips.fetchAllTrips()) syncEngine.requests.enqueue(SyncRequest(SyncEntityType.trip, trip.id, SyncAction.upload))
        for (vehicle in syncEngine.vehicles.vehicles()) syncEngine.requests.enqueue(SyncRequest(SyncEntityType.vehicle, vehicle.id, SyncAction.upload))
        val settings = settingsStore.loadOrCreate()
        syncEngine.requests.enqueue(SyncRequest(SyncEntityType.settings, settings.id, SyncAction.upload))
        // The coordinator recovers journeys and both photo queues using their existing gates.
        syncEngine.coordinator.runFullSync()
    }
    override suspend fun homeZoneChanged(before: HomeZone?, after: HomeZone?) { syncEngine.coordinator.privacyZoneChanged() }
    override suspend fun autoRecordChanged(mode: AutoRecordMode, timeout: Int, stereos: List<SavedBluetoothDevice>) {
        recordingSettings.apply(mode, timeout, stereos)
    }
    override suspend fun publicMapChanged(enabled: Boolean) {
        profilePrivacyService.save(ProfileUpdateRequest(showOnPublicMap = enabled))
    }
}
