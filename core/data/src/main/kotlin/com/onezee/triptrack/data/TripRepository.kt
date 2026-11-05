// iOS: TripRepository.swift (protocol TripRepository)
package com.onezee.triptrack.data

import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.data.db.entity.TripPhotoEntity
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.Journey
import com.onezee.triptrack.model.JourneySyncPayload
import com.onezee.triptrack.model.PhotoSyncPayload
import com.onezee.triptrack.model.PhotoUploadStatus
import com.onezee.triptrack.model.SettingsSyncPayload
import com.onezee.triptrack.model.TrackPointPayload
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.model.TripCheckpoint
import com.onezee.triptrack.model.TripCompanion
import com.onezee.triptrack.model.TripConfirmation
import com.onezee.triptrack.model.TripEnergyMode
import com.onezee.triptrack.model.TripHiddenParts
import com.onezee.triptrack.model.TripPhoto
import com.onezee.triptrack.model.TripSegment
import com.onezee.triptrack.model.TripSyncPayload
import com.onezee.triptrack.model.VehicleSyncPayload
import java.time.Instant
import java.util.UUID

/**
 * Trips and everything the iOS protocol keeps beside them: photos, checkpoints, segments, the
 * vehicle and settings pulls, journeys, the places bookkeeping and the geocoder cache.
 *
 * Every method suspends: the iOS repository runs on the main-thread view context, which Android
 * forbids for database work. Every write commits on return, so `flushPendingApplies` has no
 * counterpart; a whole pull is made one transaction with `batch {}` instead.
 *
 * Rules every implementation keeps (see `RoomTripRepository` for the reasons):
 * - «world» reads never see a draft or a soft-deleted trip;
 * - a local edit stamps `lastModifiedAt` and flips `pendingUpload` only when it can drain
 *   (`flipsPendingUpload`);
 * - a pull never overwrites an unsent edit or resurrects an unconfirmed deletion;
 * - relationless rows (place passes, journeys) are deleted explicitly, never left to a cascade.
 */
interface TripRepository {
    // MARK: Reads

    suspend fun fetchTrips(limit: Int, offset: Int): List<Trip>
    suspend fun fetchAllTrips(): List<Trip>
    /** Drafts, newest first, for the «Drafts» card and list (0.8.2). */
    suspend fun fetchDraftTrips(): List<Trip>
    /** Own trips that STARTED inside the window, both ends inclusive; the database cuts the window. */
    suspend fun fetchTrips(from: Instant, to: Instant): List<Trip>
    suspend fun hasAnyPrivateTrip(): Boolean
    /** Every completed trip with its preview polyline but no points, unless it has no preview. */
    suspend fun fetchTripsForMap(): List<Trip>
    /** The garage page of one car: completed trips on it, transfers excluded. */
    suspend fun fetchVehicleGarageTrips(vehicleId: UUID): List<Trip>
    suspend fun fetchTripsModifiedSince(date: Instant): List<Trip>
    /** The trip screen: drafts included, with points. */
    suspend fun fetchTripDetail(id: UUID): Trip?
    suspend fun fetchTripCount(): Int
    suspend fun fetchLastTripDate(): Instant?
    suspend fun fetchTripStats(): TripStats
    suspend fun fetchTotalDistance(): Double
    /** The raw row, for sync bookkeeping (`syncStatus`, `conflictVersion`, `serverCreatedAt`). */
    suspend fun fetchEntity(id: UUID): TripEntity?
    /** Photo rows of a trip in relationship order, with their upload bookkeeping. */
    suspend fun photoRows(tripId: UUID): List<TripPhotoEntity>
    suspend fun fetchPhotoRow(id: UUID): TripPhotoEntity?

    // MARK: Trip edits

    suspend fun deleteTrip(id: UUID)
    suspend fun purgeSoftDeletedTrips()
    suspend fun updateTitle(tripId: UUID, title: String)
    suspend fun updateNotes(tripId: UUID, notes: String)
    suspend fun updatePrivacy(tripId: UUID, isPrivate: Boolean)
    suspend fun updateHiddenParts(tripId: UUID, hiddenParts: TripHiddenParts?)
    /** The person rode as a passenger: the car comes off the trip and off its odometer. */
    suspend fun updateTransfer(tripId: UUID, isTransfer: Boolean)
    suspend fun updateVehicle(tripId: UUID, vehicleId: UUID?)
    /** A cache write after `/companions/list`; no `lastModifiedAt`, no upload. */
    suspend fun updateCompanions(tripId: UUID, companions: List<TripCompanion>)
    suspend fun clearCompanionsCache()
    /** Photos whose only copy is the server's, asked right before the server copy is removed. */
    suspend fun serverOnlyPhotoIds(tripId: UUID): List<UUID>
    suspend fun adoptRescuedPhoto(id: UUID, filename: String)
    suspend fun markUnpublished(tripId: UUID)
    suspend fun migrateAllTripsToPrivate(): List<UUID>
    suspend fun saveBadgesJSON(tripId: UUID, badgeIds: List<String>)
    /** A draft becomes confirmed exactly once (iOS `TripManager.setConfirmation`). */
    suspend fun setConfirmation(confirmation: TripConfirmation, tripId: UUID): Boolean
    /** Deletes a draft without a trace; a confirmed trip is left alone (iOS `TripManager.discardDraft`). */
    suspend fun discardDraft(id: UUID): Boolean
    /** `jpeg` is already encoded at `TripPhotoFiles.JPEG_QUALITY`; capture metadata comes from the picker. */
    suspend fun addPhoto(
        tripId: UUID,
        jpeg: ByteArray,
        caption: String?,
        capturedAt: Instant? = null,
        latitude: Double? = null,
        longitude: Double? = null,
    ): TripPhoto?
    suspend fun deletePhoto(id: UUID, tripId: UUID)

    // MARK: Checkpoints

    suspend fun addCheckpoint(checkpoint: TripCheckpoint, tripId: UUID): TripCheckpoint?
    /** Returns the trip id so the caller can queue the trip; null when the checkpoint is unknown. */
    suspend fun updateCheckpoint(id: UUID, name: String?, photoId: UUID?, photoIds: List<UUID>): UUID?
    suspend fun setEnergyMode(mode: TripEnergyMode, tripId: UUID)
    suspend fun deleteCheckpoint(id: UUID): UUID?

    // MARK: Segments

    /** Order normalized by the road; null for an unknown trip/checkpoint, a self-pair or the cap. */
    suspend fun addSegment(tripId: UUID, fromCheckpointId: UUID, toCheckpointId: UUID): TripSegment?
    suspend fun segments(tripId: UUID): List<TripSegment>
    suspend fun updateSegment(id: UUID, name: String?): UUID?
    suspend fun deleteSegment(id: UUID): UUID?

    // MARK: Sync

    suspend fun markSynced(tripId: UUID, conflictVersion: Int)
    suspend fun markSynced(tripId: UUID, conflictVersion: Int, serverCreatedAt: Instant)
    suspend fun markAllPendingUpload()
    suspend fun applyRemoteTrip(payload: TripSyncPayload)
    suspend fun applyRemoteVehicle(payload: VehicleSyncPayload)
    suspend fun applyRemotePhoto(payload: PhotoSyncPayload)
    suspend fun applyRemoteSettings(payload: SettingsSyncPayload)
    /** One transaction around many applies: a pull of fifty trips commits once. */
    suspend fun <R> batch(block: suspend () -> R): R
    suspend fun deleteTripHard(id: UUID)
    /** Applies a server tombstone only to a trip this phone mirrors; true when deleted. */
    suspend fun deleteTripHardIfMirrored(id: UUID): Boolean
    suspend fun tripId(forPhoto: UUID): UUID?
    /** Library size, not sync progress: `pendingDelete` counts, drafts do not. */
    suspend fun countLiveTrips(): Int
    suspend fun recomputeOdometers(vehicleIds: Collection<UUID>)
    suspend fun recomputeAllVehicleOdometers()
    /** Kilometres per car from RECORDED trips only (manual trips earn no level). */
    suspend fun rewardKmByVehicle(): Map<UUID, Double>
    suspend fun unrewardedKm(vehicleId: UUID): Double
    /** The last check before a NEW trip is stamped with a car: sold or archived cars take none. */
    suspend fun recordableVehicleId(id: UUID?): UUID?
    suspend fun deleteVehicleHard(id: UUID)
    suspend fun markVehicleSynced(id: UUID, conflictVersion: Int)
    suspend fun deletePhotoHard(id: UUID)
    suspend fun markPhotoUploaded(photoId: UUID, remoteURL: String?, thumbnailURL: String, uploadStatus: PhotoUploadStatus)
    /** iOS: PhotoStorageService.pendingUploads. Photos never uploaded at all, oldest first: (id, filename). */
    suspend fun localOnlyPhotos(): List<Pair<UUID, String>>
    /** iOS: PhotoStorageService.pendingOriginalUploads. A thumbnail is up, the original is not. */
    suspend fun photosAwaitingOriginal(): List<UUID>
    /** iOS: PhotoStorageService.markUploaded. The original landed; no sync stamp, it is bookkeeping. */
    suspend fun markPhotoOriginalUploaded(photoId: UUID, remoteURL: String)
    suspend fun missingManualTrack(id: UUID): ManualTrackSnapshot?
    suspend fun applyMissingManualTrack(points: List<TrackPointPayload>, expected: ManualTrackSnapshot): Boolean

    // MARK: Journeys (0.6.6)

    suspend fun fetchJourneys(): List<Journey>
    suspend fun fetchJourney(id: UUID): Journey?
    /** Upsert; always flips `pendingUpload`, the queue decides whether it leaves. */
    suspend fun saveJourney(journey: Journey): Journey
    suspend fun markJourneyDeleted(id: UUID)
    suspend fun deleteJourneyHard(id: UUID)
    suspend fun journeyContaining(tripId: UUID): Journey?
    suspend fun journeyOverlapping(start: Instant, end: Instant?, excluding: UUID?): Journey?
    suspend fun trips(journey: Journey): List<Trip>
    suspend fun journeySyncStatus(id: UUID): Short?
    suspend fun applyRemoteJourney(payload: JourneySyncPayload)
    suspend fun markJourneySynced(id: UUID, conflictVersion: Int)

    // MARK: Places (0.6.8)

    /** A derived value: does NOT flip `pendingUpload`; it travels with the next real edit. */
    suspend fun setPlaceId(checkpointId: UUID, placeId: UUID)
    suspend fun checkpointsWithoutPlace(): List<CheckpointRef>
    suspend fun checkpointCoordinates(placeId: UUID): List<GeoPoint>
    /** Every place id ever stamped on a checkpoint, live places and tombstones alike. */
    suspend fun checkpointPlaceIds(): Set<UUID>
    suspend fun tripPreviews(needingPlaceMatch: Boolean): List<TripPreviewRef>
    suspend fun markPlacesMatched(tripIds: List<UUID>)

    // MARK: Geocoder cache

    /** Keyed by the geohash-5 of the coordinate; an expired row is deleted and answers null. */
    suspend fun cachedGeocode(geohash5: String): GeocodeCacheResult?
    suspend fun cachedLocality(geohash5: String): String? =
        cachedGeocode(geohash5)?.locality?.takeIf { it.isNotEmpty() }
    /**
     * iOS: TripManager.cachedLocalities. Many cells in one read, keyed by geohash-5. An expired row
     * is skipped, not deleted: cleaning is `cachedGeocode`'s job.
     */
    suspend fun cachedLocalities(geohash5: Set<String>): Map<String, String>
    /** iOS: TripManager.saveGeocodeCache. One row per cell, refreshed in place. */
    suspend fun saveGeocode(geohash5: String, locality: String?, region: String?)

    companion object {
        /**
         * iOS: CoreDataTripRepository.flipsPendingUpload. A private trip with Cloud Sync off cannot
         * drain, so flagging it would leave a phantom pending operation forever; switching Cloud Sync
         * on later flips everything through `markAllPendingUpload`.
         */
        fun flipsPendingUpload(isPrivate: Boolean, cloudSyncEnabled: Boolean): Boolean = cloudSyncEnabled || !isPrivate
    }
}
