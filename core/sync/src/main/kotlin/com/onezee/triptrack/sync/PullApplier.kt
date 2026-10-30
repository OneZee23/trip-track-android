package com.onezee.triptrack.sync

import com.onezee.triptrack.data.DiscoveryStore
import com.onezee.triptrack.data.TripRepository
import com.onezee.triptrack.data.VehiclePhotoStore
import com.onezee.triptrack.logic.GeohashEncoder
import com.onezee.triptrack.model.*
import java.util.UUID

// iOS: PullApplier.swift (SyncPullNotification). Empty IDs are known-empty, never unknown.
data class SyncPullCompleted(val appliedTripIds: List<UUID>)

// iOS: PullApplier.swift. Repositories own the per-field pending-edit and absent-key rules.
class PullApplier(
    private val repository: TripRepository,
    private val discoveries: DiscoveryStore,
    private val vehiclePhotos: VehiclePhotoStore,
    private val purgeVehiclePreferences: suspend (UUID) -> Unit,
    private val invalidateTripSnapshots: () -> Unit = {},
    private val reloadSettings: suspend () -> Unit = {},
) {
    suspend fun apply(response: SyncPullResponse): List<UUID> {
        val previousManualVehicles = response.trips.upserted.mapNotNull { payload ->
            repository.fetchEntity(payload.id)?.takeIf { it.source == TripOrigin.manual.rawValue }?.vehicleId
        }
        repository.batch {
            for (trip in response.trips.upserted) repository.applyRemoteTrip(trip)
            val kept = response.trips.deleted.filterNot { repository.deleteTripHardIfMirrored(it) }.toSet()
            for (vehicle in response.vehicles.upserted) repository.applyRemoteVehicle(vehicle)
            for (id in response.vehicles.deleted) {
                vehiclePhotos.deleteAllLocally(id)
                purgeVehiclePreferences(id)
                repository.deleteVehicleHard(id)
            }
            for (photo in response.photos.upserted) repository.applyRemotePhoto(photo)
            for (id in response.photos.deleted) {
                if (repository.tripId(id) !in kept) repository.deletePhotoHard(id)
            }
            response.settings?.let { repository.applyRemoteSettings(it) }
            response.journeys?.let { section ->
                for (journey in section.upserted) repository.applyRemoteJourney(journey)
                for (id in section.deleted) repository.deleteJourneyHard(id)
            }
            response.discoveries?.let { section -> discoveries.applyRemote(section.upserted.mapNotNull(::remote)) }
            val manualVehicles = response.trips.upserted.filter { it.source == TripOrigin.manual }.mapNotNull { it.vehicleId } + previousManualVehicles
            if (manualVehicles.isNotEmpty()) repository.recomputeOdometers(manualVehicles)
        }
        if (response.trips.upserted.isNotEmpty() || response.trips.deleted.isNotEmpty() ||
            response.photos.upserted.isNotEmpty() || response.photos.deleted.isNotEmpty()) invalidateTripSnapshots()
        if (response.settings != null || response.vehicles.upserted.isNotEmpty() || response.vehicles.deleted.isNotEmpty() ||
            previousManualVehicles.isNotEmpty() || response.trips.upserted.any { it.source == TripOrigin.manual && it.vehicleId != null }) reloadSettings()
        // iOS reports incoming upsert IDs, including those protected by a repository edit guard.
        return response.trips.upserted.map { it.id }
    }

    companion object {
        fun remote(payload: DiscoverySyncPayload): DiscoveryStore.Remote? {
            val kind = DiscoveryKind.fromRawValue(payload.kind) ?: return null
            val latitude = payload.latitude
            val longitude = payload.longitude
            val coordinate = if (payload.verified == true && latitude != null && longitude != null &&
                latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
                (latitude != 0.0 || longitude != 0.0)) GeoPoint(latitude, longitude)
            else if (kind == DiscoveryKind.riddle) {
                val parts = payload.secretId.split(':')
                if (parts.size == 2 && parts[1].length >= 7) GeohashEncoder.centerCoordinate(parts[1]) else null
            } else null
            return DiscoveryStore.Remote(Discovery.id(kind, payload.secretId), kind, payload.secretId, payload.tripId,
                coordinate, payload.foundAt, payload.symbol?.let(SealSymbol::fromRawValue) ?: SealSymbol.generic,
                payload.title, payload.story, payload.verified ?: false)
        }
    }
}
