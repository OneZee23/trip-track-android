// iOS: TripRepository.swift (CoreDataTripRepository), TripManager.swift (setConfirmation, discardDraft,
// saveGeocodeCache)
package com.onezee.triptrack.data

import androidx.room.withTransaction
import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.data.db.dao.TripPreviewRow
import com.onezee.triptrack.data.db.dao.WorldSql
import com.onezee.triptrack.data.db.entity.GeocodeCacheEntity
import com.onezee.triptrack.data.db.entity.JourneyEntity
import com.onezee.triptrack.data.db.entity.TrackPointEntity
import com.onezee.triptrack.data.db.entity.TripCheckpointEntity
import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.data.db.entity.TripPhotoEntity
import com.onezee.triptrack.data.db.entity.UserSettingsEntity
import com.onezee.triptrack.data.db.entity.VehicleEntity
import com.onezee.triptrack.data.files.TripPhotoFiles
import com.onezee.triptrack.data.sync.SyncAction
import com.onezee.triptrack.data.sync.SyncEntityType
import com.onezee.triptrack.data.sync.SyncRequest
import com.onezee.triptrack.data.sync.SyncRequestSink
import com.onezee.triptrack.logic.Drive2Link
import com.onezee.triptrack.logic.RecordingBreaks
import com.onezee.triptrack.logic.VehicleLevelSystem
import com.onezee.triptrack.logic.VehicleModTags
import com.onezee.triptrack.logic.contains
import com.onezee.triptrack.logic.earnsRewards
import com.onezee.triptrack.logic.parse
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.Journey
import com.onezee.triptrack.model.JourneySyncPayload
import com.onezee.triptrack.model.PhotoSyncPayload
import com.onezee.triptrack.model.PhotoUploadStatus
import com.onezee.triptrack.model.SettingsSyncPayload
import com.onezee.triptrack.model.SyncStatus
import com.onezee.triptrack.model.TrackPoint
import com.onezee.triptrack.model.TrackPointPayload
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.model.TripCheckpoint
import com.onezee.triptrack.model.TripCompanion
import com.onezee.triptrack.model.TripConfirmation
import com.onezee.triptrack.model.TripEnergyMode
import com.onezee.triptrack.model.TripOrigin
import com.onezee.triptrack.model.TripPhoto
import com.onezee.triptrack.model.TripSegment
import com.onezee.triptrack.model.TripSyncPayload
import com.onezee.triptrack.model.VehicleFinish
import com.onezee.triptrack.model.VehicleSyncPayload
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * iOS: CoreDataTripRepository, on Room.
 *
 * Writes that iOS performs on the view context and saves once are one transaction here, and the
 * sync requests they cause are handed to [sync] only after that transaction committed, the way iOS
 * enqueues from a `Task` after `save()`.
 */
class RoomTripRepository(
    private val db: TripTrackDatabase,
    private val environment: DataEnvironment,
    private val photoFiles: TripPhotoFiles,
    private val sync: SyncRequestSink = SyncRequestSink.None,
) : TripRepository {
    private val trips = db.tripDao()
    private val vehicles = db.vehicleDao()
    private val settings = db.settingsDao()
    private val journeys = db.journeyDao()
    private val places = db.placeDao()
    private val map = db.mapDao()

    private val pendingUpload = SyncStatus.pendingUpload.rawValue
    private val synced = SyncStatus.synced.rawValue
    private val pendingDelete = SyncStatus.pendingDelete.rawValue

    private fun now(): Instant = environment.clock()

    // MARK: - Reads

    override suspend fun fetchAllTrips(): List<Trip> = tripsFrom(trips.completedTrips()) { false }

    override suspend fun fetchDraftTrips(): List<Trip> = tripsFrom(trips.draftTrips()) { false }

    /**
     * The window is cut by the DATABASE, not a filter in memory: the whole library would carry every
     * trip's photos and checkpoints for the six inside one week. Same predicate as `trips(journey)`.
     */
    override suspend fun fetchTrips(from: Instant, to: Instant): List<Trip> =
        tripsFrom(trips.completedTripsStarting(from, to)) { false }

    override suspend fun hasAnyPrivateTrip(): Boolean = trips.countPrivateCompletedTrips() > 0

    override suspend fun fetchTrips(limit: Int, offset: Int): List<Trip> =
        tripsFrom(trips.completedTrips(limit, offset)) { false }

    /**
     * Track points stay behind: the map draws the preview polyline, and materialising every point of
     * every trip was a multi-second freeze on a large library. A trip saved before previews existed
     * has nothing else to draw from, so those, and only those, still pay for their points.
     */
    override suspend fun fetchTripsForMap(): List<Trip> =
        tripsFrom(trips.completedTrips()) { it.previewPolyline == null }

    override suspend fun fetchVehicleGarageTrips(vehicleId: UUID): List<Trip> =
        tripsFrom(trips.completedTripsOfVehicle(vehicleId)) { it.previewPolyline == null }.filter { !it.isTransfer }

    override suspend fun fetchTripsModifiedSince(date: Instant): List<Trip> =
        tripsFrom(trips.tripsModifiedSince(date)) { false }

    override suspend fun fetchTripDetail(id: UUID): Trip? {
        val entity = trips.trip(id) ?: return null
        return tripsFrom(listOf(entity)) { true }.firstOrNull()
    }

    override suspend fun fetchTripCount(): Int = trips.countCompletedTrips()

    override suspend fun fetchLastTripDate(): Instant? = trips.lastCompletedStartDate()

    override suspend fun fetchTripStats(): TripStats =
        TripStats(trips.countCompletedTrips(), trips.totalCompletedDistance())

    override suspend fun fetchTotalDistance(): Double = fetchTripStats().totalDistance

    override suspend fun fetchEntity(id: UUID): TripEntity? = trips.trip(id)

    override suspend fun photoRows(tripId: UUID): List<TripPhotoEntity> = trips.photos(tripId)

    // MARK: - Trip edits

    override suspend fun deleteTrip(id: UUID) {
        var hard = false
        var queued = false
        db.withTransaction {
            val entity = trips.trip(id) ?: return@withTransaction
            // Place passes have no relationship with the trip, like journeys: no cascade takes them,
            // and a place's history must not remember a trip the person no longer has.
            places.deletePassesOfTrip(id)
            // Never on the server: there is nothing for the server to delete. Soft-deleting it would
            // leave a private trip at Cloud Sync off hidden in `pendingDelete` forever, since the
            // privacy gate never lets its delete drain.
            if (entity.serverCreatedAt == null) {
                hard = true
                return@withTransaction
            }
            trips.upsertTrip(entity.copy(syncStatus = pendingDelete, lastModifiedAt = now()))
            // The kilometres leave the car's odometer when the trip leaves the feed, not later.
            entity.vehicleId?.let { recomputeOdometersInTransaction(listOf(it)) }
            queued = true
        }
        if (hard) deleteTripHard(id)
        if (queued) sync.enqueue(SyncRequest(SyncEntityType.trip, id, SyncAction.delete))
    }

    override suspend fun purgeSoftDeletedTrips() {
        val doomed = trips.softDeletedTrips()
        if (doomed.isEmpty()) return
        db.withTransaction { for (entity in doomed) trips.deleteTrip(entity.id) }
        for (entity in doomed) photoFiles.deleteAll(entity.id)
    }

    override suspend fun updateTitle(tripId: UUID, title: String) {
        editTrip(tripId) { entity ->
            // Everything that reaches this came out of the editor, so a non-empty title is the
            // person's choice, even when it equals the stamped date. Clearing makes it unnamed again.
            entity.copy(title = title.ifEmpty { null }, titleIsCustom = title.isNotEmpty()).touchedForUpload()
        }
    }

    override suspend fun updateNotes(tripId: UUID, notes: String) {
        editTrip(tripId) { it.copy(tripDescription = notes).touchedForUpload() }
    }

    override suspend fun updateTransfer(tripId: UUID, isTransfer: Boolean) {
        db.withTransaction {
            val entity = trips.trip(tripId) ?: return@withTransaction
            // A passenger's car went nowhere: keeping it on the trip would be a lie, and its
            // kilometres come off its odometer the same way a reassignment takes them off.
            val updated = entity.copy(
                isTransfer = isTransfer,
                vehicleId = if (isTransfer) null else entity.vehicleId,
            ).touchedForUpload()
            trips.upsertTrip(updated)
            recomputeOdometersInTransaction(listOfNotNull(entity.vehicleId))
        }
    }

    override suspend fun updateVehicle(tripId: UUID, vehicleId: UUID?) {
        db.withTransaction {
            val entity = trips.trip(tripId) ?: return@withTransaction
            // Both cars are recomputed: the odometer is derived from the trips pointing at a car, so
            // moving a trip takes its kilometres off the old car as well as onto the new one.
            trips.upsertTrip(entity.copy(vehicleId = vehicleId).touchedForUpload())
            recomputeOdometersInTransaction(listOfNotNull(entity.vehicleId, vehicleId))
        }
    }

    /**
     * Purely a cache write after `/companions/list`. A foreign trip has no row, so nothing is created
     * for it. `lastModifiedAt` is the sync clock and opening a trip screen is not an edit; the
     * payload carries no companions, so flagging the trip would upload nothing at a cost.
     */
    override suspend fun updateCompanions(tripId: UUID, companions: List<TripCompanion>) {
        db.withTransaction {
            val entity = trips.trip(tripId) ?: return@withTransaction
            val json = if (companions.isEmpty()) null else JsonColumns.encodeCompanions(companions)
            trips.upsertTrip(entity.copy(companionsJSON = json))
        }
    }

    /** Sign-out: local trips are per device and survive an account switch, their roster must not. */
    override suspend fun clearCompanionsCache() {
        db.withTransaction {
            for (entity in trips.tripsWithCompanions()) trips.upsertTrip(entity.copy(companionsJSON = null))
        }
    }

    override suspend fun updatePrivacy(tripId: UUID, isPrivate: Boolean) {
        var action: SyncAction? = null
        var enqueuePhotos = false
        var photoIds: List<UUID> = emptyList()
        var publicPhotoIds: List<UUID> = emptyList()
        db.withTransaction {
            val entity = trips.trip(tripId) ?: return@withTransaction
            val wasSynced = entity.serverCreatedAt != null
            val cloudOn = environment.cloudSyncEnabled()
            // Privacy first:
            //  going public           -> upload, or update if already on the server, plus photos;
            //  private, on server, off -> unpublish: delete the server copy, keep the local one;
            //  private, on server, on  -> update: the server keeps it and hides it from the feed;
            //  private, never uploaded -> only the local flip.
            // `pendingUpload` (not `synced`) on unpublish keeps a concurrent pull from flipping the
            // trip back through applyRemoteTrip's guard; markUnpublished resets it once it lands.
            val status: Short
            when {
                !isPrivate -> {
                    action = if (wasSynced) SyncAction.update else SyncAction.upload
                    status = pendingUpload
                    enqueuePhotos = true
                }
                wasSynced && !cloudOn -> {
                    action = SyncAction.unpublish
                    status = pendingUpload
                }
                wasSynced -> {
                    action = SyncAction.update
                    status = pendingUpload
                }
                else -> status = synced
            }
            trips.upsertTrip(entity.copy(isPrivate = isPrivate, lastModifiedAt = now(), syncStatus = status))
            if (action == SyncAction.unpublish) photoIds = trips.photos(tripId).map { it.id }
            if (enqueuePhotos) publicPhotoIds = trips.photosWithoutOriginal(tripId).map { it.id }
        }
        val op = action ?: return
        // Conflicting queued operations go first, in both directions: an unpublish must not let a
        // queued upload publish for a moment, and a re-publish must not let a queued unpublish run.
        sync.cancel(tripId, SyncEntityType.trip)
        if (op == SyncAction.unpublish) for (id in photoIds) sync.cancel(id, SyncEntityType.photo)
        sync.enqueue(SyncRequest(SyncEntityType.trip, tripId, op))
        // Photos taken while the trip was private were blocked at enqueue time; without this the
        // public card would show the trip without any of them.
        for (id in publicPhotoIds) sync.enqueue(SyncRequest(SyncEntityType.photo, id, SyncAction.upload))
    }

    override suspend fun serverOnlyPhotoIds(tripId: UUID): List<UUID> =
        trips.photos(tripId).filter { row ->
            (row.remoteURL != null || row.thumbnailURL != null) && !photoFiles.exists(row.filename)
        }.map { it.id }

    /** The rescued file is local now, and the server URLs it no longer has a claim to are cleared. */
    override suspend fun adoptRescuedPhoto(id: UUID, filename: String) {
        db.withTransaction {
            val row = trips.photo(id) ?: return@withTransaction
            trips.upsertPhoto(row.copy(filename = filename, remoteURL = null, thumbnailURL = null))
        }
    }

    /**
     * After a successful unpublish delete. `isPrivate` is NOT forced: the person may have made the
     * trip public again while the operation was in flight, and the queue then already holds an
     * upload. Clearing the server bookkeeping makes a re-publish an `upload`, not an `update`.
     */
    override suspend fun markUnpublished(tripId: UUID) {
        editTrip(tripId) { entity ->
            entity.copy(
                serverCreatedAt = null,
                conflictVersion = 0,
                syncStatus = if (entity.isPrivate) synced else entity.syncStatus,
                lastModifiedAt = now(),
            )
        }
    }

    /**
     * Privacy by default, once per install. No `pendingUpload` flip: a private trip at Cloud Sync off
     * would never drain. Trips already on the server come back so the caller can unpublish them.
     */
    override suspend fun migrateAllTripsToPrivate(): List<UUID> = db.withTransaction {
        val stamp = now()
        val serverSide = mutableListOf<UUID>()
        for (entity in trips.publicTrips()) {
            trips.upsertTrip(entity.copy(isPrivate = true, lastModifiedAt = stamp))
            if (entity.serverCreatedAt != null) serverSide.add(entity.id)
        }
        serverSide
    }

    override suspend fun saveBadgesJSON(tripId: UUID, badgeIds: List<String>) {
        if (badgeIds.isEmpty()) return
        var saved = false
        editTrip(tripId) { entity ->
            saved = true
            entity.copy(badgesJSON = JsonColumns.encodeStrings(badgeIds)).touchedForUpload()
        }
        // Unconditional: the gate filters per operation, harmless for a private trip at off.
        if (saved) sync.enqueue(SyncRequest(SyncEntityType.trip, tripId, SyncAction.update))
    }

    override suspend fun setConfirmation(confirmation: TripConfirmation, tripId: UUID): Boolean =
        db.withTransaction {
            // Entering the world twice is not allowed, and nothing turns back into a draft.
            val entity = trips.trip(tripId)
            if (confirmation != TripConfirmation.confirmed || entity == null ||
                entity.confirmation != TripConfirmation.draft.rawValue
            ) {
                return@withTransaction false
            }
            trips.upsertTrip(entity.copy(confirmation = confirmation.rawValue, lastModifiedAt = now()))
            true
        }

    /** A draft was never on the server (`serverCreatedAt == null`), so `deleteTrip` leaves no tombstone. */
    override suspend fun discardDraft(id: UUID): Boolean {
        if (trips.trip(id)?.confirmation != TripConfirmation.draft.rawValue) return false
        deleteTrip(id)
        return true
    }

    override suspend fun addPhoto(
        tripId: UUID,
        jpeg: ByteArray,
        caption: String?,
        capturedAt: Instant?,
        latitude: Double?,
        longitude: Double?,
    ): TripPhoto? {
        // The trip is checked before the file is written, so an unknown trip leaves no orphan file.
        if (trips.trip(tripId) == null) return null
        val filename = photoFiles.save(jpeg, tripId) ?: return null
        val photoId = UUID.randomUUID()
        val stamp = now()
        val inserted = db.withTransaction {
            val entity = trips.trip(tripId) ?: return@withTransaction false
            trips.upsertPhoto(
                TripPhotoEntity(
                    id = photoId,
                    filename = filename,
                    caption = caption,
                    timestamp = stamp,
                    capturedAt = capturedAt,
                    exifLatitude = latitude,
                    exifLongitude = longitude,
                    lastModifiedAt = stamp,
                    sortOrder = trips.countPhotos(tripId).toShort(),
                    tripId = tripId,
                    tripOrder = trips.nextPhotoOrder(tripId),
                ),
            )
            trips.upsertTrip(entity.copy(lastModifiedAt = stamp))
            true
        }
        if (!inserted) {
            photoFiles.delete(filename)
            return null
        }
        sync.enqueue(SyncRequest(SyncEntityType.photo, photoId, SyncAction.upload))
        sync.enqueue(SyncRequest(SyncEntityType.trip, tripId, SyncAction.update))
        return TripPhoto(
            id = photoId, filename = filename, caption = caption, timestamp = stamp,
            capturedAt = capturedAt, exifLatitude = latitude, exifLongitude = longitude,
        )
    }

    override suspend fun deletePhoto(id: UUID, tripId: UUID) {
        var hadServerCopy: Boolean? = null
        var filename: String? = null
        db.withTransaction {
            detachPhotoFromCheckpoints(id)
            val row = trips.photo(id) ?: return@withTransaction
            // Read BEFORE the row goes: the only moment anyone can still tell whether the server
            // holds a copy. A thumbnail counts: an upload that got that far left a blob to remove.
            hadServerCopy = row.remoteURL != null || row.thumbnailURL != null
            filename = row.filename
            row.tripId?.let { parent -> trips.trip(parent)?.let { trips.upsertTrip(it.copy(lastModifiedAt = now())) } }
            trips.deletePhoto(id)
        }
        val serverCopy = hadServerCopy ?: return
        filename?.let(photoFiles::delete)
        sync.enqueue(SyncRequest(SyncEntityType.photo, id, SyncAction.delete, hasServerCopy = serverCopy))
        sync.enqueue(SyncRequest(SyncEntityType.trip, tripId, SyncAction.update))
    }

    // MARK: - Checkpoints

    override suspend fun addCheckpoint(checkpoint: TripCheckpoint, tripId: UUID): TripCheckpoint? = db.withTransaction {
        val entity = trips.trip(tripId) ?: return@withTransaction null
        val stamp = now()
        trips.upsertCheckpoint(
            TripCheckpointEntity(
                id = checkpoint.id,
                timestamp = checkpoint.timestamp,
                latitude = checkpoint.latitude,
                longitude = checkpoint.longitude,
                distanceFromStart = checkpoint.distanceFromStart,
                elapsedFromStart = checkpoint.elapsedFromStart,
                name = checkpoint.name,
                photoId = checkpoint.photoId,
                photoIdsJSON = JsonColumns.encodeIds(checkpoint.photoIds),
                placeId = checkpoint.placeId,
                createdAt = stamp,
                lastModifiedAt = stamp,
                userId = environment.localUserId(),
                tripId = tripId,
                tripOrder = trips.nextCheckpointOrder(tripId),
            ),
        )
        trips.upsertTrip(entity.checkpointsChanged())
        checkpoint
    }

    /**
     * Writing how a plug-in hybrid drove is an edit of the trip, like a checkpoint: without the flag
     * the next pull would undo the choice made a second ago. «Auto» is stored as NULL.
     */
    override suspend fun setEnergyMode(mode: TripEnergyMode, tripId: UUID) {
        editTrip(tripId) { entity ->
            if (TripEnergyMode.parse(entity.energyMode) == mode) null
            else entity.copy(energyMode = mode.stored).checkpointsChanged()
        }
    }

    override suspend fun updateCheckpoint(id: UUID, name: String?, photoId: UUID?, photoIds: List<UUID>): UUID? =
        db.withTransaction {
            val row = trips.checkpoint(id) ?: return@withTransaction null
            trips.upsertCheckpoint(
                row.copy(name = name, photoId = photoId, photoIdsJSON = JsonColumns.encodeIds(photoIds), lastModifiedAt = now()),
            )
            val parent = row.tripId?.let { trips.trip(it) }
            parent?.let { trips.upsertTrip(it.checkpointsChanged()) }
            parent?.id
        }

    override suspend fun deleteCheckpoint(id: UUID): UUID? = db.withTransaction {
        val row = trips.checkpoint(id) ?: return@withTransaction null
        val parent = row.tripId?.let { trips.trip(it) }
        if (parent != null) {
            // Segments have no cascade: they are a JSON column of the trip and hold only the
            // checkpoint ids. Cleaned by hand BEFORE the delete, or a segment would hang on half a
            // pair; reading drops such garbage anyway, but it would travel to the server and back.
            val rest = JsonColumns.decodeSegments(parent.segmentsJSON).filter {
                it.fromCheckpointId != id && it.toCheckpointId != id
            }
            trips.upsertTrip(parent.copy(segmentsJSON = JsonColumns.encodeSegments(rest)).checkpointsChanged())
        }
        trips.deleteCheckpoint(id)
        parent?.id
    }

    // MARK: - Segments (0.6.8)

    override suspend fun segments(tripId: UUID): List<TripSegment> =
        JsonColumns.decodeSegments(trips.trip(tripId)?.segmentsJSON)

    override suspend fun addSegment(tripId: UUID, fromCheckpointId: UUID, toCheckpointId: UUID): TripSegment? =
        db.withTransaction {
            if (fromCheckpointId == toCheckpointId) return@withTransaction null
            val entity = trips.trip(tripId) ?: return@withTransaction null
            val all = trips.checkpoints(tripId)
            val a = all.firstOrNull { it.id == fromCheckpointId } ?: return@withTransaction null
            val b = all.firstOrNull { it.id == toCheckpointId } ?: return@withTransaction null
            val segments = JsonColumns.decodeSegments(entity.segmentsJSON)
            // The same pair in either direction is the same segment: the road sets the direction,
            // not the order of taps, and a second one would be a duplicate in the moments feed.
            val pair = setOf(fromCheckpointId, toCheckpointId)
            segments.firstOrNull { setOf(it.fromCheckpointId, it.toCheckpointId) == pair }?.let { return@withTransaction it }
            // The server's MAX_SEGMENTS: it silently drops the rest, so one created beyond it would
            // vanish on the first pull. The duplicate check above runs first: it creates nothing.
            if (segments.size >= TripSegment.maxPerTrip) return@withTransaction null
            // Ordered by the road: «Segment to…» may point back along the track.
            val reversed = b.elapsedFromStart < a.elapsedFromStart
            val segment = TripSegment(
                fromCheckpointId = if (reversed) toCheckpointId else fromCheckpointId,
                toCheckpointId = if (reversed) fromCheckpointId else toCheckpointId,
            )
            trips.upsertTrip(entity.copy(segmentsJSON = JsonColumns.encodeSegments(segments + segment)).checkpointsChanged())
            segment
        }

    override suspend fun updateSegment(id: UUID, name: String?): UUID? = db.withTransaction {
        val entity = tripCarryingSegment(id) ?: return@withTransaction null
        val segments = JsonColumns.decodeSegments(entity.segmentsJSON).toMutableList()
        val index = segments.indexOfFirst { it.id == id }
        if (index < 0) return@withTransaction null
        // A blank name is NULL, not an empty string: «A → B» is built from NULL at display time.
        val trimmed = name?.trim()
        segments[index] = segments[index].copy(name = if (trimmed.isNullOrEmpty()) null else trimmed)
        trips.upsertTrip(entity.copy(segmentsJSON = JsonColumns.encodeSegments(segments)).checkpointsChanged())
        entity.id
    }

    override suspend fun deleteSegment(id: UUID): UUID? = db.withTransaction {
        val entity = tripCarryingSegment(id) ?: return@withTransaction null
        val segments = JsonColumns.decodeSegments(entity.segmentsJSON)
        if (segments.none { it.id == id }) return@withTransaction null
        trips.upsertTrip(
            entity.copy(segmentsJSON = JsonColumns.encodeSegments(segments.filter { it.id != id })).checkpointsChanged(),
        )
        entity.id
    }

    /** A segment has no row of its own: the trip is found by its id inside the JSON column. */
    private suspend fun tripCarryingSegment(id: UUID): TripEntity? = trips.tripCarryingSegment(id.toString())

    /**
     * The photo was a cover or attached to checkpoints: they stay, the reference goes, or the card
     * would draw a placeholder for a photo that no longer exists.
     */
    private suspend fun detachPhotoFromCheckpoints(photoId: UUID) {
        val stamp = now()
        for (row in trips.checkpointsReferencingPhoto(photoId, photoId.toString())) {
            val rest = JsonColumns.decodeIds(row.photoIdsJSON).filter { it != photoId }
            trips.upsertCheckpoint(
                row.copy(
                    photoId = if (row.photoId == photoId) null else row.photoId,
                    photoIdsJSON = JsonColumns.encodeIds(rest),
                    lastModifiedAt = stamp,
                ),
            )
        }
    }

    // MARK: - Places (0.6.8)

    override suspend fun setPlaceId(checkpointId: UUID, placeId: UUID) {
        db.withTransaction {
            val row = trips.checkpoint(checkpointId) ?: return@withTransaction
            if (row.placeId == placeId) return@withTransaction
            // Deliberately without checkpointsChanged: the place is derived from the checkpoint and
            // the track, both synced already; flagging would send the whole library after a backfill.
            trips.upsertCheckpoint(row.copy(placeId = placeId))
        }
    }

    override suspend fun checkpointsWithoutPlace(): List<CheckpointRef> =
        trips.checkpointsWithoutPlace().mapNotNull { row ->
            val tripId = row.tripId ?: return@mapNotNull null
            CheckpointRef(checkpoint(row).copy(placeId = null), tripId)
        }

    override suspend fun checkpointCoordinates(placeId: UUID): List<GeoPoint> =
        trips.checkpointCoordinates(placeId).map { GeoPoint(it.latitude, it.longitude) }

    override suspend fun checkpointPlaceIds(): Set<UUID> = trips.checkpointPlaceIds().toSet()

    override suspend fun tripPreviews(needingPlaceMatch: Boolean): List<TripPreviewRef> =
        (if (needingPlaceMatch) trips.tripPreviewsNeedingPlaceMatch() else trips.tripPreviews()).map(::previewRef)

    /** One statement per chunk: saving per trip would be a thousand disk writes on the first pass. */
    override suspend fun markPlacesMatched(tripIds: List<UUID>) {
        if (tripIds.isEmpty()) return
        val stamp = now()
        db.withTransaction { for (chunk in tripIds.chunked(CHUNK)) trips.markPlacesMatched(chunk, stamp) }
    }

    // MARK: - Geocoder cache

    override suspend fun cachedGeocode(geohash5: String): GeocodeCacheResult? {
        val row = map.geocode(geohash5) ?: return null
        if (Duration.between(row.cachedAt, now()) > GeocodeCacheResult.ttl) {
            map.deleteGeocode(row.pk)
            return null
        }
        return GeocodeCacheResult(row.locality, row.region)
    }

    override suspend fun cachedLocalities(geohash5: Set<String>): Map<String, String> {
        if (geohash5.isEmpty()) return emptyMap()
        val stamp = now()
        val names = mutableMapOf<String, String>()
        for (chunk in geohash5.toList().chunked(CHUNK)) {
            for (row in map.geocodes(chunk)) {
                val locality = row.locality?.takeIf { it.isNotEmpty() } ?: continue
                if (Duration.between(row.cachedAt, stamp) > GeocodeCacheResult.ttl) continue
                names[row.geohash5] = locality
            }
        }
        return names
    }

    override suspend fun saveGeocode(geohash5: String, locality: String?, region: String?) {
        db.withTransaction {
            val existing = map.geocode(geohash5)
            map.upsertGeocode(
                (existing ?: GeocodeCacheEntity(cachedAt = now(), geohash5 = geohash5))
                    .copy(locality = locality, region = region, cachedAt = now()),
            )
        }
    }

    // MARK: - Sync

    override suspend fun markSynced(tripId: UUID, conflictVersion: Int) {
        editTrip(tripId) { it.copy(syncStatus = synced, conflictVersion = conflictVersion) }
    }

    override suspend fun markSynced(tripId: UUID, conflictVersion: Int, serverCreatedAt: Instant) {
        editTrip(tripId) {
            it.copy(syncStatus = synced, conflictVersion = conflictVersion, serverCreatedAt = serverCreatedAt)
        }
    }

    /**
     * Scoped to THIS install's user id, so signing in as B never re-uploads what A left on the
     * device. Photos have no user column and are scoped through their trip.
     */
    override suspend fun markAllPendingUpload() {
        val userId = environment.localUserId()
        db.withTransaction {
            trips.markTripsPendingUpload(userId)
            vehicles.markVehiclesPendingUpload(userId)
            trips.markPhotosPendingUpload(userId)
            settings.markPendingUpload(userId)
        }
    }

    override suspend fun <R> batch(block: suspend () -> R): R = db.withTransaction { block() }

    override suspend fun applyRemoteTrip(payload: TripSyncPayload) {
        db.withTransaction { applyRemoteTripInTransaction(payload) }
    }

    private suspend fun applyRemoteTripInTransaction(p: TripSyncPayload) {
        val existing = trips.trip(p.id)
        // `pendingDelete` belongs in this guard as much as `pendingUpload`: on a full pull a
        // soft-deleted trip is still a live server row, and it would come back as `synced`.
        if (existing != null && (existing.syncStatus == pendingUpload || existing.syncStatus == pendingDelete) &&
            existing.conflictVersion >= p.conflictVersion
        ) {
            return
        }
        // A freshly inserted row is `pendingUpload` too, but carries no local edit: its FIRST pull
        // must still adopt source=manual, or a web-made route would earn recorded-trip rewards.
        val hasLocalEdits = existing != null && existing.syncStatus == pendingUpload
        val base = existing ?: TripEntity(id = p.id, startDate = p.startDate)

        // Old servers omit the breaks: an older peer knowing only points must not erase a pause.
        val remoteBreaks = p.recordingBreaks
        val breaks = if (!hasLocalEdits && remoteBreaks != null) JsonColumns.encodeBreaks(remoteBreaks) else base.recordingBreaksJSON
        // Source (0.8.0) and energy mode (0.8.3): absent key or an unsent edit leaves the local
        // value. A known mode is the other phone's answer, «Auto» included (stored as NULL); an
        // unknown one is silence.
        val remoteSource = p.source
        val source = if (!hasLocalEdits && remoteSource != null) remoteSource.rawValue else base.source
        val remoteMode = TripEnergyMode.fromRawValue(p.energyMode ?: "")
        val energyMode = if (!hasLocalEdits && remoteMode != null) remoteMode.stored else base.energyMode
        // The server's preview is the ECHO of ours, and under the home privacy zone (0.8.2) it comes
        // back trimmed. Writing it over the local one would cut the person's own yard out of their
        // own map with no way back. A trip from the other phone has no preview here and needs it.
        val preview = if (base.previewPolyline == null || !environment.homeZoneActive()) {
            p.previewPolyline?.let(::decodeBase64)
        } else {
            base.previewPolyline
        }
        // Segments replace the list whole when the key came, an empty list clearing them (deleted on
        // the other phone); an absent key keeps ours. Dangling ones are dropped on read.
        val remoteSegments = p.segments
        val segmentsJSON = if (remoteSegments != null) {
            JsonColumns.encodeSegments(remoteSegments.map { TripSegment(it.id, it.fromCheckpointId, it.toCheckpointId, it.name) })
        } else {
            base.segmentsJSON
        }

        trips.upsertTrip(
            base.copy(
                title = p.title,
                tripDescription = p.description,
                startDate = p.startDate,
                endDate = p.endDate,
                distance = p.distance,
                maxSpeed = p.maxSpeed,
                averageSpeed = p.averageSpeed,
                fuelUsed = p.fuelUsed,
                elevation = p.elevation,
                region = p.region,
                isPrivate = p.isPrivate,
                vehicleId = p.vehicleId,
                // Only when the key came: reading absence as «not a transfer» undid the passenger
                // mark on every pull from an old backend, with the car already taken off.
                isTransfer = p.isTransfer ?: base.isTransfer,
                source = source,
                energyMode = energyMode,
                fuelCurrency = p.fuelCurrency,
                previewPolyline = preview,
                badgesJSON = p.badgesJson,
                xpEarned = p.xpEarned ?: 0,
                conflictVersion = p.conflictVersion,
                lastModifiedAt = p.lastModifiedAt,
                // «This row mirrors a server row», what the tombstone guard reads. Set once, never
                // overwritten: markUnpublished clears it on purpose, and a pull must not re-assert it.
                serverCreatedAt = base.serverCreatedAt ?: (p.serverCreatedAt ?: p.lastModifiedAt),
                syncStatus = synced,
                recordingBreaksJSON = breaks,
                segmentsJSON = segmentsJSON,
            ),
        )

        // Points only when the server sent them (detail/push); a pull delta omits them. An explicit
        // empty list is «the other phone deleted the track», not silence.
        p.trackPoints?.let { points ->
            trips.deleteTrackPoints(p.id)
            if (points.isNotEmpty()) trips.insertTrackPoints(points.map { it.toEntity(p.id) })
        }

        // Photos: the iOS asymmetry is kept on purpose (see docs/api-contract.md): a missing list is
        // an empty server set, so uploaded rows not named here go, and the pull's own photo section
        // restores them. A local-only photo is never touched. Capture time and place are taken from
        // the server only when this phone has none: the local values came from the picker.
        val serverPhotos = p.photos.orEmpty().associateBy { it.id }
        for (row in trips.photos(p.id)) {
            if (row.uploadStatus == PhotoUploadStatus.localOnly.rawValue) continue
            val remote = serverPhotos[row.id]
            if (remote == null) {
                trips.deletePhoto(row.id)
                continue
            }
            var merged = row
            if (merged.capturedAt == null && remote.capturedAt != null) merged = merged.copy(capturedAt = remote.capturedAt)
            if (merged.exifLatitude == null && remote.exifLatitude != null && remote.exifLongitude != null) {
                merged = merged.copy(exifLatitude = remote.exifLatitude, exifLongitude = remote.exifLongitude)
            }
            if (merged != row) trips.upsertPhoto(merged)
        }

        // Checkpoints: a list from the server is the truth; an absent key (old server) keeps ours.
        p.checkpoints?.let { incoming ->
            val local = trips.checkpoints(p.id)
            // The place is derived locally and almost never comes back: a pull must not orphan the
            // checkpoint, or reconciliation would register it again and resurrect a deleted place.
            val previousPlace = local.mapNotNull { row -> row.placeId?.let { row.id to it } }.toMap()
            // Our own coordinate, by the same rule (0.8.3): inside the privacy zone the checkpoint
            // travels without a point, so NULL from the server means «the server says nothing».
            val previousPoint = local.filter { it.latitude != 0.0 || it.longitude != 0.0 }
                .associate { it.id to GeoPoint(it.latitude, it.longitude) }
            trips.deleteCheckpoints(p.id)
            val stamp = now()
            val userId = environment.localUserId()
            val rows = incoming.sortedBy { it.sortOrder }.mapIndexed { index, c ->
                // No key and no own copy (reinstall, second phone): an honest zero, which
                // `hasCoordinate` recognises; neither the map nor places touch such a checkpoint.
                val lat = c.latitude
                val lon = c.longitude
                val point = if (lat != null && lon != null) GeoPoint(lat, lon) else previousPoint[c.id]
                TripCheckpointEntity(
                    id = c.id,
                    timestamp = c.timestamp,
                    latitude = point?.latitude ?: 0.0,
                    longitude = point?.longitude ?: 0.0,
                    distanceFromStart = c.distanceFromStart,
                    elapsedFromStart = c.elapsedFromStart,
                    name = c.name,
                    photoId = c.photoId,
                    photoIdsJSON = JsonColumns.encodeIds(c.photoIds.orEmpty()),
                    placeId = c.placeId ?: previousPlace[c.id],
                    createdAt = stamp,
                    lastModifiedAt = p.lastModifiedAt,
                    userId = userId,
                    tripId = p.id,
                    tripOrder = index.toLong(),
                )
            }
            if (rows.isNotEmpty()) trips.upsertCheckpoints(rows)
        }
    }

    /**
     * A server summary exists here but its manual route was never downloaded. The snapshot guards
     * the late network answer against an edit, a deletion and a newer pull.
     */
    override suspend fun missingManualTrack(id: UUID): ManualTrackSnapshot? {
        val entity = trips.trip(id) ?: return null
        if (!entity.isUnloadedManualSummary() || trips.countTrackPoints(id) != 0) return null
        return ManualTrackSnapshot(id, entity.conflictVersion, entity.lastModifiedAt)
    }

    /**
     * Only points are adopted; applyRemoteTrip here would also replace photos, checkpoints and
     * metadata from a possibly older response. The guards run in the same transaction as the write,
     * so nothing can slip between the check and the commit.
     */
    override suspend fun applyMissingManualTrack(points: List<TrackPointPayload>, expected: ManualTrackSnapshot): Boolean =
        db.withTransaction {
            val entity = trips.trip(expected.id)
            if (entity == null || !entity.isUnloadedManualSummary() || trips.countTrackPoints(expected.id) != 0 ||
                entity.conflictVersion != expected.conflictVersion || entity.lastModifiedAt != expected.lastModifiedAt
            ) {
                return@withTransaction false
            }
            if (points.isNotEmpty()) trips.insertTrackPoints(points.map { it.toEntity(expected.id) })
            // A constructed manual route must never run through GPS reconstruction.
            trips.upsertTrip(entity.copy(isTrackProcessed = true, placesMatchedAt = null))
            true
        }

    private fun TripEntity.isUnloadedManualSummary(): Boolean =
        source == TripOrigin.manual.rawValue && syncStatus == synced && serverCreatedAt != null && endDate != null

    override suspend fun applyRemoteVehicle(payload: VehicleSyncPayload) {
        db.withTransaction { applyRemoteVehicleInTransaction(payload) }
    }

    private suspend fun applyRemoteVehicleInTransaction(p: VehicleSyncPayload) {
        val existing = vehicles.vehicle(p.id)
        // Edits on this device that have not left yet. Only for an existing row: a new one is
        // `pendingUpload` by default, and without this no arriving car would ever apply.
        // A guard against «the background pull overtook the queue», not a lock: a lost conflict
        // clears the flag first (pullAndOverwriteVehicle), and then the server copy lands whole.
        val hasLocalEdits = existing?.syncStatus == pendingUpload
        var e = existing ?: VehicleEntity(id = p.id)
        e = e.copy(name = p.name, avatarEmoji = p.avatarEmoji)
        // Absent is not «car»: it is a server without an opinion. And not over an unsent edit: the
        // exact car picker sets body and silhouette together.
        if (!hasLocalEdits && p.avatarStyle != null) e = e.copy(avatarStyle = p.avatarStyle)
        e = e.copy(odometerKm = p.odometerKm)
        // The key came: a value OR an explicit null, which is a clear made on the other device.
        if (p.manualOdometerKnown) e = e.copy(manualOdometerKm = p.manualOdometerKm)
        e = e.copy(vehicleLevel = p.level, stickersJSON = p.stickersJson)
        // Fuel figures are the numbers every trip's cost is computed from: an unsent edit wins.
        if (!hasLocalEdits) {
            e = e.copy(cityConsumption = p.cityConsumption, highwayConsumption = p.highwayConsumption, fuelPrice = p.fuelPrice)
        }
        // A server that predates a column sends nothing, and nothing is not «reset to default».
        p.vehicleType?.let { e = e.copy(vehicleType = it) }
        p.plate?.let { e = e.copy(plate = it) }
        // The four visibility axes: a returned name is a nuisance, a returned visibility is a leak.
        if (!hasLocalEdits) p.plateVisible?.let { e = e.copy(plateVisible = it) }
        if (!hasLocalEdits) p.visibleToOthers?.let { e = e.copy(visibleToOthers = it) }
        p.fuelCurrency?.let { e = e.copy(fuelCurrency = it) }
        // A returned dashboard unit is a WRONG NUMBER in the database: 142 000 typed from a km panel
        // parsed as miles. Unknown values arrive as null and leave the local choice.
        if (!hasLocalEdits) p.dashboardUnits?.let { e = e.copy(dashboardUnits = it.rawValue) }
        if (!hasLocalEdits) {
            p.drive2Url?.let { e = e.copy(drive2URL = Drive2Link.normalized(it) ?: "") }
            p.modTags?.let { e = e.copy(modTagsJSON = VehicleModTags.encodeJSON(it)) }
            p.paintFinish?.let { raw ->
                val finish = if (raw.isEmpty()) VehicleFinish.gloss else VehicleFinish.parse(raw)
                finish?.let { e = e.copy(paintFinish = it.rawValue) }
            }
            p.mods?.let { e = e.copy(modsJSON = JsonColumns.encodeMods(it)) }
            p.catalogModelId?.let { e = e.copy(catalogModelId = it) }
            p.catalogGenerationId?.let { e = e.copy(catalogGenerationId = it) }
            p.catalogVariantId?.let { e = e.copy(catalogVariantId = it) }
            p.cardStyle?.let { e = e.copy(cardStyle = it) }
            // Electric and hybrid (0.8.3): a returned powertrain is another split of every trip.
            p.powertrain?.let { e = e.copy(powertrain = it.rawValue) }
            p.electricConsumption?.let { e = e.copy(electricConsumption = it) }
            p.electricityPrice?.let { e = e.copy(electricityPrice = it) }
            p.electricRangeKm?.let { e = e.copy(electricRangeKm = it) }
            // The passport and the three catalog ids are ONE edit (0.9.1).
            p.about?.let { e = e.copy(about = it) }
            p.make?.let { e = e.copy(make = it) }
            p.model?.let { e = e.copy(model = it) }
            p.year?.let { e = e.copy(year = it) }
            p.bodyType?.let { e = e.copy(bodyType = it) }
            p.mapVisible?.let { e = e.copy(mapVisible = it) }
            p.photosVisible?.let { e = e.copy(photosVisible = it) }
        }
        p.isArchived?.let { e = e.copy(isArchived = it) }
        // `soldAt` is the exception where NULL means something: «the sale was undone». Told apart by
        // the key, not the value: an old server says nothing, a new one sends null.
        if (p.soldAtKnown) e = e.copy(soldAt = p.soldAt)
        e = e.copy(conflictVersion = p.conflictVersion)
        // An unsent edit keeps its flag AND its time, or the second pull undoes what the first kept.
        if (!hasLocalEdits) e = e.copy(lastModifiedAt = p.lastModifiedAt, syncStatus = synced)
        vehicles.upsertVehicle(e)
    }

    override suspend fun applyRemotePhoto(payload: PhotoSyncPayload) {
        db.withTransaction {
            val existing = trips.photo(payload.id)
            val parent = trips.trip(payload.tripId)
            var tripId = existing?.tripId
            var order = existing?.tripOrder ?: 0
            // Joining a trip appends to the end of its photos, as the ordered relationship does.
            if (parent != null && existing?.tripId != parent.id) {
                tripId = parent.id
                order = trips.nextPhotoOrder(parent.id)
            }
            val base = existing ?: TripPhotoEntity(id = payload.id, timestamp = payload.timestamp)
            trips.upsertPhoto(
                base.copy(
                    tripId = tripId,
                    tripOrder = order,
                    filename = payload.filename,
                    caption = payload.caption,
                    timestamp = payload.timestamp,
                    remoteURL = payload.remoteUrl,
                    thumbnailURL = payload.thumbnailUrl,
                    sortOrder = payload.sortOrder.toShort(),
                    uploadStatus = payload.uploadStatus,
                    lastModifiedAt = payload.lastModifiedAt,
                    syncStatus = synced,
                ),
            )
        }
    }

    /**
     * The server is authoritative for preferences, NOT for progress. A full pull always carries the
     * settings row, and the local row is routinely ahead with no pending marker, so XP, level and
     * best streak only ever go up, and preferences follow `lastModifiedAt`, which every local save
     * stamps. `id` lives with the preferences: rewriting it from a STALE row re-points the identity
     * every entity is stamped with.
     */
    override suspend fun applyRemoteSettings(payload: SettingsSyncPayload) {
        db.withTransaction {
            val existing = settings.settings()
            var e = existing ?: UserSettingsEntity(id = payload.id)
            e = e.copy(
                profileXP = maxOf(e.profileXP, payload.profileXp.toLong()),
                profileLevel = maxOf(e.profileLevel, payload.profileLevel),
                bestStreak = maxOf(e.bestStreak, payload.bestStreak),
            )
            val localStamp = e.lastModifiedAt ?: Instant.MIN
            if (existing == null || payload.lastModifiedAt >= localStamp) {
                // Avatar frame and the PRO badge are NOT here: they live on the account and arrive
                // through /auth/me. One writer per field.
                e = e.copy(
                    id = payload.id,
                    avatarEmoji = payload.avatarEmoji,
                    themeMode = payload.themeMode,
                    language = payload.language,
                    distanceUnit = payload.distanceUnit,
                    volumeUnit = payload.volumeUnit,
                    fuelConsumption = payload.fuelConsumption,
                    fuelPrice = payload.fuelPrice,
                    fuelCurrency = payload.fuelCurrency,
                    selectedVehicleId = payload.selectedVehicleId,
                    currentStreak = payload.currentStreak,
                    lastTripDate = payload.lastTripDate,
                    lastModifiedAt = payload.lastModifiedAt,
                )
            }
            e = e.copy(conflictVersion = payload.conflictVersion, syncStatus = synced)
            if (existing != null && existing.id != e.id) settings.delete(existing.id)
            settings.upsert(e)
        }
    }

    override suspend fun deleteTripHard(id: UUID) {
        val deleted = db.withTransaction {
            val entity = trips.trip(id) ?: return@withTransaction false
            // Three callers (own deletion of an unsent trip, the transport on tripNotFound and after
            // a confirmed delete) all pass here; relationless passes are not taken by the cascade.
            places.deletePassesOfTrip(id)
            trips.deleteTrip(id)
            entity.vehicleId?.let { recomputeOdometersInTransaction(listOf(it)) }
            true
        }
        // The cascade takes the photo rows, never the files.
        if (deleted) photoFiles.deleteAll(id)
    }

    /**
     * A tombstone deletes only a trip this phone MIRRORS from the server. `serverCreatedAt == null`
     * is a trip that lived only here, or one the person unpublished: «gone from the server, kept
     * here» is exactly what unpublishing means.
     */
    override suspend fun deleteTripHardIfMirrored(id: UUID): Boolean {
        val deleted = db.withTransaction {
            val entity = trips.trip(id) ?: return@withTransaction false
            if (entity.serverCreatedAt == null) return@withTransaction false
            places.deletePassesOfTrip(id)
            trips.deleteTrip(id)
            true
        }
        if (deleted) photoFiles.deleteAll(id)
        return deleted
    }

    override suspend fun tripId(forPhoto: UUID): UUID? = trips.photo(forPhoto)?.tripId

    override suspend fun countLiveTrips(): Int = trips.countLiveTrips()

    override suspend fun recomputeOdometers(vehicleIds: Collection<UUID>) {
        db.withTransaction { recomputeOdometersInTransaction(vehicleIds) }
    }

    /**
     * The odometer is DERIVED from the trips on the car, not accumulated: moving, deleting and
     * restoring trips are then correct by construction. Transfers do not count (`VehicleOdometer`).
     * Odometer and level come from DIFFERENT sums: a manual trip is driven distance, the level is a
     * reward for what the app saw.
     */
    private suspend fun recomputeOdometersInTransaction(vehicleIds: Collection<UUID>) {
        for (vehicleId in vehicleIds.toSet()) {
            val rows = trips.odometerTrips(vehicleId)
            val km = rows.sumOf { it.distance } / 1000
            val rewardKm = rows.sumOf { if (tripOrigin(it.source).earnsRewards) it.distance else 0.0 } / 1000
            val vehicle = vehicles.vehicle(vehicleId) ?: continue
            vehicles.upsertVehicle(vehicle.copy(odometerKm = km, vehicleLevel = VehicleLevelSystem.level(rewardKm)))
        }
    }

    override suspend fun rewardKmByVehicle(): Map<UUID, Double> {
        val metres = mutableMapOf<UUID, Double>()
        for (row in trips.odometerTripsOfAllVehicles()) {
            val id = row.vehicleId ?: continue
            if (!tripOrigin(row.source).earnsRewards) continue
            metres[id] = (metres[id] ?: 0.0) + row.distance
        }
        return metres.mapValues { it.value / 1000 }
    }

    /** By subtraction, not recount: an odometer may hold more than trips, and only the unrewarded part goes. */
    override suspend fun unrewardedKm(vehicleId: UUID): Double =
        trips.odometerTrips(vehicleId).sumOf { if (tripOrigin(it.source).earnsRewards) 0.0 else it.distance } / 1000

    override suspend fun recordableVehicleId(id: UUID?): UUID? {
        val vehicleId = id ?: return null
        val entity = vehicles.vehicle(vehicleId) ?: return null
        return if (!entity.isArchived && entity.soldAt == null) vehicleId else null
    }

    /**
     * Refuses an empty library: with no trips the odometers are not evidence of drift but the only
     * surviving record of the mileage, and zeroing them would repeat the mistake this was written for.
     */
    override suspend fun recomputeAllVehicleOdometers() {
        if (countLiveTrips() <= 0) return
        db.withTransaction { recomputeOdometersInTransaction(vehicles.vehicleIds()) }
    }

    override suspend fun deleteVehicleHard(id: UUID) {
        vehicles.deleteVehicle(id)
    }

    /** «Sent and confirmed». On a CONFLICT this is not enough: applyRemoteVehicle follows it. */
    override suspend fun markVehicleSynced(id: UUID, conflictVersion: Int) {
        db.withTransaction {
            val entity = vehicles.vehicle(id) ?: return@withTransaction
            vehicles.upsertVehicle(entity.copy(syncStatus = synced, conflictVersion = conflictVersion))
        }
    }

    override suspend fun deletePhotoHard(id: UUID) {
        db.withTransaction {
            detachPhotoFromCheckpoints(id)
            trips.deletePhoto(id)
        }
    }

    override suspend fun markPhotoUploaded(photoId: UUID, remoteURL: String?, thumbnailURL: String, uploadStatus: PhotoUploadStatus) {
        db.withTransaction {
            val row = trips.photo(photoId) ?: return@withTransaction
            trips.upsertPhoto(
                row.copy(
                    thumbnailURL = thumbnailURL,
                    remoteURL = remoteURL ?: row.remoteURL,
                    uploadStatus = uploadStatus.rawValue,
                    lastModifiedAt = now(),
                ),
            )
        }
    }

    override suspend fun localOnlyPhotos(): List<Pair<UUID, String>> = trips.localOnlyPhotos().map { it.id to it.filename }

    override suspend fun photosAwaitingOriginal(): List<UUID> = trips.photosAwaitingOriginal()

    override suspend fun markPhotoOriginalUploaded(photoId: UUID, remoteURL: String) {
        db.withTransaction {
            val row = trips.photo(photoId) ?: return@withTransaction
            trips.upsertPhoto(row.copy(uploadStatus = PhotoUploadStatus.uploaded.rawValue, remoteURL = remoteURL))
        }
    }

    // MARK: - Journeys (0.6.6)

    private fun journey(e: JourneyEntity): Journey = Journey(
        id = e.id,
        userId = e.userId,
        title = e.title,
        startDate = e.startDate,
        endDate = e.endDate,
        excludedTripIds = JsonColumns.decodeIds(e.excludedTripIdsJSON),
        coverPhotoId = e.coverPhotoId,
        isPrivate = e.isPrivate,
        conflictVersion = e.conflictVersion,
        lastModifiedAt = e.lastModifiedAt ?: now(),
        serverCreatedAt = e.serverCreatedAt,
    )

    override suspend fun fetchJourneys(): List<Journey> = journeys.liveJourneys().map(::journey)

    override suspend fun fetchJourney(id: UUID): Journey? = journeys.journey(id)?.let(::journey)

    /** Personal data like a car: it leaves only with Cloud Sync, but the flag is always set. */
    override suspend fun saveJourney(journey: Journey): Journey = db.withTransaction {
        val stamp = now()
        val base = journeys.journey(journey.id)
            ?: JourneyEntity(id = journey.id, startDate = journey.startDate, createdAt = stamp, userId = environment.localUserId())
        val saved = base.copy(
            title = journey.title,
            startDate = journey.startDate,
            endDate = journey.endDate,
            excludedTripIdsJSON = JsonColumns.encodeIds(journey.excludedTripIds),
            coverPhotoId = journey.coverPhotoId,
            isPrivate = journey.isPrivate,
            lastModifiedAt = stamp,
            syncStatus = pendingUpload,
        )
        journeys.upsert(saved)
        journey(saved)
    }

    override suspend fun markJourneyDeleted(id: UUID) {
        db.withTransaction {
            val e = journeys.journey(id) ?: return@withTransaction
            journeys.upsert(e.copy(syncStatus = pendingDelete, lastModifiedAt = now()))
        }
    }

    override suspend fun deleteJourneyHard(id: UUID) {
        journeys.delete(id)
    }

    override suspend fun journeySyncStatus(id: UUID): Short? = journeys.journey(id)?.syncStatus

    /** The start date is all the window needs; the full trip with its track is not read. */
    override suspend fun journeyContaining(tripId: UUID): Journey? {
        val start = trips.trip(tripId)?.startDate ?: return null
        return fetchJourneys().firstOrNull { it.contains(tripId, start) }
    }

    override suspend fun journeyOverlapping(start: Instant, end: Instant?, excluding: UUID?): Journey? =
        fetchJourneys().firstOrNull { j ->
            j.id != excluding && j.startDate <= (end ?: Instant.MAX) && start <= (j.endDate ?: Instant.MAX)
        }

    /**
     * Legs: own trips inside the window, by start. Transfers count: the person travels, not the car.
     * The window is cut by the database; an open window has no upper bound at all.
     */
    override suspend fun trips(journey: Journey): List<Trip> {
        val rows = journey.endDate?.let { trips.completedTripsStarting(journey.startDate, it) }
            ?: trips.completedTripsStarting(journey.startDate)
        return tripsFrom(rows) { false }.filter { journey.contains(it) }
    }

    override suspend fun applyRemoteJourney(payload: JourneySyncPayload) {
        db.withTransaction {
            val existing = journeys.journey(payload.id)
            // An unsent local edit is newer than the server copy: a guard against «the pull overtook
            // the queue», not a lock (a lost conflict clears the flag first). And a deletion the
            // server has not confirmed must not come back as `synced` from the same exchange.
            if (existing?.syncStatus == pendingUpload || existing?.syncStatus == pendingDelete) return@withTransaction
            val stamp = now()
            val base = existing ?: JourneyEntity(id = payload.id, startDate = payload.startDate, createdAt = stamp)
            journeys.upsert(
                base.copy(
                    userId = environment.localUserId(),
                    title = payload.title,
                    startDate = payload.startDate,
                    endDate = payload.endDate,
                    excludedTripIdsJSON = JsonColumns.encodeIds(payload.excludedTripIds),
                    coverPhotoId = payload.coverPhotoId,
                    isPrivate = payload.isPrivate,
                    conflictVersion = payload.conflictVersion,
                    lastModifiedAt = payload.lastModifiedAt,
                    serverCreatedAt = payload.serverCreatedAt ?: base.serverCreatedAt ?: stamp,
                    syncStatus = synced,
                ),
            )
        }
    }

    override suspend fun markJourneySynced(id: UUID, conflictVersion: Int) {
        db.withTransaction {
            val e = journeys.journey(id) ?: return@withTransaction
            journeys.upsert(
                e.copy(syncStatus = synced, conflictVersion = conflictVersion, serverCreatedAt = e.serverCreatedAt ?: now()),
            )
        }
    }

    // MARK: - Mapping

    private suspend fun tripsFrom(entities: List<TripEntity>, includeTrackPoints: (TripEntity) -> Boolean): List<Trip> {
        if (entities.isEmpty()) return emptyList()
        val ids = entities.map { it.id }
        // Two reads for the whole page instead of two per trip.
        val photos = ids.chunked(CHUNK).flatMap { trips.photos(it) }.groupBy { it.tripId }
        val checkpoints = ids.chunked(CHUNK).flatMap { trips.checkpoints(it) }.groupBy { it.tripId }
        return entities.map { e ->
            val points = if (includeTrackPoints(e)) trips.orderedTrackPoints(e.id) else emptyList()
            trip(e, points, photos[e.id].orEmpty(), checkpoints[e.id].orEmpty())
        }
    }

    private fun trip(
        e: TripEntity,
        points: List<TrackPointEntity>,
        photoRows: List<TripPhotoEntity>,
        checkpointRows: List<TripCheckpointEntity>,
    ): Trip {
        // Stable sort: equal elapsed times keep the relationship order, as on iOS.
        val checkpoints = checkpointRows.map(::checkpoint).sortedBy { it.elapsedFromStart }
        // A segment without one of its checkpoints is dropped silently: the JSON column has no
        // cascade and a pull replaces checkpoints whole. Reading is the one place that catches it
        // for every path at once (own deletion, pull, a database from the other phone).
        val checkpointIds = checkpoints.mapTo(HashSet()) { it.id }
        val segments = JsonColumns.decodeSegments(e.segmentsJSON).filter {
            it.fromCheckpointId in checkpointIds && it.toCheckpointId in checkpointIds
        }
        return Trip(
            id = e.id,
            startDate = e.startDate,
            endDate = e.endDate,
            distance = e.distance,
            maxSpeed = e.maxSpeed,
            averageSpeed = e.averageSpeed,
            trackPoints = RecordingBreaks.annotate(points.map(::trackPoint), JsonColumns.decodeBreaks(e.recordingBreaksJSON)),
            photos = photoRows.map(::photo),
            checkpoints = checkpoints,
            segments = segments,
            title = e.title,
            titleIsCustom = e.titleIsCustom,
            tripDescription = e.tripDescription,
            fuelUsed = e.fuelUsed,
            elevation = e.elevation,
            region = e.region,
            isPrivate = e.isPrivate,
            isTransfer = e.isTransfer,
            vehicleId = e.vehicleId,
            fuelCurrency = e.fuelCurrency,
            previewPolyline = e.previewPolyline,
            earnedBadgeIds = JsonColumns.decodeStrings(e.badgesJSON),
            xpEarned = e.xpEarned,
            companions = JsonColumns.decodeCompanions(e.companionsJSON),
            isOnServer = e.serverCreatedAt != null,
            source = tripOrigin(e.source),
            confirmation = tripConfirmation(e.confirmation),
            roadFillState = roadFillState(e.roadFillState),
            energyMode = TripEnergyMode.parse(e.energyMode),
            recordingBreaks = JsonColumns.decodeBreaks(e.recordingBreaksJSON),
        )
    }

    private fun trackPoint(row: TrackPointEntity) = TrackPoint(
        id = row.id,
        latitude = row.latitude,
        longitude = row.longitude,
        altitude = row.altitude,
        speed = row.speed,
        course = row.course,
        horizontalAccuracy = row.horizontalAccuracy,
        timestamp = row.timestamp,
        isInterpolated = row.isInterpolated,
    )

    private fun photo(row: TripPhotoEntity) = TripPhoto(
        id = row.id,
        filename = row.filename,
        caption = row.caption,
        timestamp = row.timestamp,
        capturedAt = row.capturedAt,
        exifLatitude = row.exifLatitude,
        exifLongitude = row.exifLongitude,
    )

    private fun checkpoint(row: TripCheckpointEntity) = TripCheckpoint(
        id = row.id,
        timestamp = row.timestamp,
        latitude = row.latitude,
        longitude = row.longitude,
        distanceFromStart = row.distanceFromStart,
        elapsedFromStart = row.elapsedFromStart,
        name = row.name,
        photoId = row.photoId,
        photoIds = JsonColumns.decodeIds(row.photoIdsJSON),
        placeId = row.placeId,
    )

    private fun previewRef(row: TripPreviewRow) = TripPreviewRef(row.id, row.startDate, row.previewPolyline)

    // MARK: - Edit helpers

    /** Reads, transforms and writes one trip in a transaction; a null transform writes nothing. */
    private suspend fun editTrip(id: UUID, transform: (TripEntity) -> TripEntity?) {
        db.withTransaction {
            val entity = trips.trip(id) ?: return@withTransaction
            transform(entity)?.let { trips.upsertTrip(it) }
        }
    }

    /**
     * iOS: the metadata-edit block. `lastModifiedAt` always; `pendingUpload` only when the edit can
     * drain, or the status sheet would show a phantom «1 pending» forever.
     */
    private fun TripEntity.touchedForUpload(): TripEntity {
        val flips = TripRepository.flipsPendingUpload(isPrivate, environment.cloudSyncEnabled())
        return copy(lastModifiedAt = now(), syncStatus = if (flips) pendingUpload else syncStatus)
    }

    /**
     * iOS: markCheckpointsChanged. Checkpoints, segments and the energy mode travel INSIDE the trip,
     * so their edit is an edit of the trip: without the flag the next pull replaces the local list
     * with the server's and erases what the person just set (review of 8 September 2026).
     */
    private fun TripEntity.checkpointsChanged(): TripEntity = touchedForUpload()

    private fun TrackPointPayload.toEntity(tripId: UUID) = TrackPointEntity(
        id = id,
        latitude = latitude,
        longitude = longitude,
        altitude = altitude,
        speed = speed,
        course = course,
        horizontalAccuracy = horizontalAccuracy,
        timestamp = timestamp,
        isInterpolated = isInterpolated,
        tripId = tripId,
    )

    private fun decodeBase64(text: String): ByteArray? = try {
        Base64.getDecoder().decode(text)
    } catch (_: IllegalArgumentException) {
        null
    }

    private companion object {
        /** Bound parameters per statement: far below SQLite's limit on every Android version. */
        const val CHUNK = 500

        init {
            // The SQL constants stand for these raw values; a renumbered enum must fail loudly.
            check(WorldSql.PENDING_UPLOAD == SyncStatus.pendingUpload.rawValue.toInt())
            check(WorldSql.SYNCED == SyncStatus.synced.rawValue.toInt())
            check(WorldSql.PENDING_DELETE == SyncStatus.pendingDelete.rawValue.toInt())
        }
    }
}
