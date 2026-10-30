package com.onezee.triptrack.sync

import androidx.room.withTransaction
import com.onezee.triptrack.data.*
import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.data.files.TripPhotoFiles
import com.onezee.triptrack.data.files.VehiclePhotoFiles
import com.onezee.triptrack.data.sync.SyncAction
import com.onezee.triptrack.data.sync.SyncEntityType
import com.onezee.triptrack.model.*
import com.onezee.triptrack.network.*
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException

// iOS: APISyncTransport.swift. Each dependency names its owning store/service.
class APISyncTransport(
    private val client: ApiClient,
    private val db: TripTrackDatabase,
    private val repository: TripRepository,
    private val vehicleStore: VehicleStore,
    private val tripPhotoFiles: TripPhotoFiles,
    private val vehiclePhotoFiles: VehiclePhotoFiles,
    private val cloudSyncEnabled: () -> Boolean,
    private val zone: () -> PrivacyZoneSpec?,
    private val manualTrackLoader: ManualTripTrackLoader,
    private val archiveDiscussion: suspend (UUID) -> Unit,
    private val retryDiscovery: suspend (UUID) -> Unit,
    private val photoEncoder: PhotoEncoder = RasterPhotoEncoder(),
    private val clock: () -> Instant = Instant::now,
    private val photos: R2PhotoStorage = R2PhotoStorage(client),
) : SyncTransport {

    override suspend fun execute(operation: SyncOperation) {
        val id = operation.entityId
        val action = operation.action
        when (operation.entityType) {
            SyncEntityType.trip -> when (action) {
                SyncAction.upload, SyncAction.update -> uploadTrip(id)
                SyncAction.delete -> deleteTrip(id)
                SyncAction.unpublish -> unpublishTrip(id)
            }
            SyncEntityType.journey -> when (action) {
                SyncAction.upload, SyncAction.update, SyncAction.unpublish -> uploadJourney(id, action == SyncAction.unpublish)
                SyncAction.delete -> deleteJourney(id)
            }
            SyncEntityType.vehicle -> if (cloudSyncEnabled()) when (action) {
                SyncAction.upload, SyncAction.update -> uploadVehicle(id)
                SyncAction.delete -> deleteVehicle(id)
                SyncAction.unpublish -> Unit
            }
            SyncEntityType.photo -> when (action) {
                SyncAction.upload, SyncAction.update -> uploadPhoto(id)
                SyncAction.delete -> client.post<PhotoDeleteRequest, EmptyResponse>(ApiEndpoint.photoDelete, PhotoDeleteRequest(id)).let { }
                SyncAction.unpublish -> Unit
            }
            SyncEntityType.vehiclePhoto -> if (cloudSyncEnabled()) when (action) {
                SyncAction.upload, SyncAction.update -> uploadVehiclePhoto(id)
                SyncAction.delete -> client.post<PhotoDeleteRequest, EmptyResponse>(ApiEndpoint.vehiclePhotoDelete, PhotoDeleteRequest(id)).let { }
                SyncAction.unpublish -> Unit
            }
            SyncEntityType.settings -> if (cloudSyncEnabled() && (action == SyncAction.upload || action == SyncAction.update)) uploadSettings()
            SyncEntityType.discovery -> if (cloudSyncEnabled() && (action == SyncAction.upload || action == SyncAction.update)) retryDiscovery(id)
        }
    }

    private suspend fun payload(id: UUID): TripSyncPayload? = repository.batch {
        val entity = repository.fetchEntity(id) ?: return@batch null
        if (entity.confirmation == TripConfirmation.draft.rawValue) return@batch null
        val trip = repository.fetchTripDetail(id) ?: return@batch null
        TripSyncPayloadMapper.map(trip, entity, repository.photoRows(id), zone(), clock())
    }

    private suspend fun uploadTrip(id: UUID) {
        val entity = repository.fetchEntity(id) ?: return
        if (entity.confirmation == TripConfirmation.draft.rawValue) return
        if (!cloudSyncEnabled() && entity.isPrivate) {
            if (entity.serverCreatedAt != null) unpublishTrip(id)
            else db.tripDao().upsertTrip(entity.copy(syncStatus = SyncStatus.synced.rawValue))
            return
        }
        manualTrackLoader.prepareUpload(id, zone() != null)
        val payload = payload(id) ?: return
        try {
            val response: TripUpsertResponse = client.post(ApiEndpoint.tripUpsert, payload)
            acknowledgeTrip(payload, response.conflictVersion, response.serverCreatedAt)
        } catch (error: ApiError) {
            when (error) {
                is ApiError.ConflictDetected -> pullAndOverwriteTrip(id)
                ApiError.TripNotFound -> repository.deleteTripHard(id)
                else -> throw error
            }
        }
    }

    override suspend fun uploadTripsBatch(operations: List<SyncOperation>, onChunkSynced: (Int) -> Unit): Set<UUID> {
        val handled = mutableSetOf<UUID>()
        var current = mutableListOf<TripSyncPayload>()
        var bytes = 0L
        suspend fun flush() {
            if (current.isEmpty()) return
            val chunk = current
            current = mutableListOf()
            bytes = 0
            try {
                val response: SyncPushResponse = client.post(ApiEndpoint.syncPush, SyncPushRequest(chunk))
                var synced = 0
                for (result in response.trips.orEmpty()) {
                    val sent = chunk.firstOrNull { it.id == result.id } ?: continue
                    if (result.id in handled) continue
                    when (result.status) {
                        "created", "updated" -> acknowledgeTrip(sent, result.conflictVersion, result.serverCreatedAt)
                        "conflict" -> try { pullAndOverwriteTrip(result.id) } catch (error: ApiError) { continue }
                        else -> continue
                    }
                    handled.add(result.id)
                    synced++
                }
                if (synced > 0) onChunkSynced(synced)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: ApiError) { /* The queue keeps every unacknowledged operation for per-trip fallback. */ }
        }
        for (operation in operations) {
            val entity = repository.fetchEntity(operation.entityId)
            if (entity == null) { handled.add(operation.entityId); onChunkSynced(1); continue }
            if (entity.confirmation == TripConfirmation.draft.rawValue || (!cloudSyncEnabled() && entity.isPrivate)) continue
            try { manualTrackLoader.prepareUpload(entity.id, zone() != null) } catch (_: ApiError) { continue }
            val next = payload(entity.id) ?: continue
            if (SyncChunkBudget.shouldFlush(current.size, bytes, next.estimatedWireBytes)) flush()
            current.add(next)
            bytes += next.estimatedWireBytes
        }
        flush()
        return handled
    }

    private suspend fun acknowledgeTrip(sent: TripSyncPayload, version: Int, serverCreatedAt: Instant?) = db.withTransaction {
        val current = db.tripDao().trip(sent.id) ?: return@withTransaction
        db.tripDao().upsertTrip(current.copy(
            conflictVersion = version, serverCreatedAt = serverCreatedAt ?: current.serverCreatedAt,
            syncStatus = if (current.lastModifiedAt == sent.lastModifiedAt && current.syncStatus != SyncStatus.pendingDelete.rawValue)
                SyncStatus.synced.rawValue else current.syncStatus,
        ))
    }

    private suspend fun pullAndOverwriteTrip(id: UUID) {
        val fresh: TripSyncPayload = client.post(ApiEndpoint.tripDetail, TripDetailRequest(id, true))
        repository.applyRemoteTrip(fresh)
    }

    private suspend fun deleteTrip(id: UUID) {
        val entity = repository.fetchEntity(id) ?: return
        try { client.post<TripDeleteRequest, EmptyResponse>(ApiEndpoint.tripDelete, TripDeleteRequest(id, entity.conflictVersion)) }
        catch (error: ApiError) { if (error != ApiError.TripNotFound) throw error }
        repository.deleteTripHard(id)
    }

    private suspend fun unpublishTrip(id: UUID) {
        val entity = repository.fetchEntity(id) ?: return
        if (entity.serverCreatedAt == null) { repository.markUnpublished(id); return }
        // Server delete cascades the gallery. Rescue the last copy before allowing deletion.
        for (photoId in repository.serverOnlyPhotoIds(id)) {
            val bytes = try { photos.download(photoId, PhotoType.original) }
            catch (error: ApiError) { photos.download(photoId, PhotoType.thumbnail) }
            val jpeg = photoEncoder.jpeg(bytes, Int.MAX_VALUE, TripPhotoFiles.JPEG_QUALITY)
                ?: throw ApiError.Transport("Photo rescue failed")
            val filename = tripPhotoFiles.save(jpeg, id) ?: throw ApiError.Transport("Photo rescue failed")
            repository.adoptRescuedPhoto(photoId, filename)
        }
        try { archiveDiscussion(id) } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Discussion is best effort; it cannot indefinitely delay erasure. */ }
        try { client.post<TripDeleteRequest, EmptyResponse>(ApiEndpoint.tripDelete, TripDeleteRequest(id, entity.conflictVersion)) }
        catch (error: ApiError) { if (error != ApiError.TripNotFound) throw error }
        repository.markUnpublished(id)
    }

    private suspend fun uploadJourney(id: UUID, unpublish: Boolean) {
        val journey = repository.fetchJourney(id) ?: return
        if (!cloudSyncEnabled() && journey.isPrivate && !unpublish) return
        val payload = JourneySyncPayload(journey.id, journey.title, journey.startDate, journey.endDate, journey.excludedTripIds,
            journey.coverPhotoId, journey.isPrivate, journey.conflictVersion, journey.lastModifiedAt, journey.serverCreatedAt)
        try {
            val response: JourneyUpsertResponse = client.post(ApiEndpoint.journeyUpsert, payload)
            db.withTransaction {
                val current = db.journeyDao().journey(id) ?: return@withTransaction
                if (current.lastModifiedAt == payload.lastModifiedAt && current.syncStatus != SyncStatus.pendingDelete.rawValue) {
                    repository.markJourneySynced(id, response.conflictVersion)
                } else db.journeyDao().upsert(current.copy(conflictVersion = response.conflictVersion))
            }
        } catch (error: ApiError) {
            if (error !is ApiError.ConflictDetected) throw error
            val response: JourneyListResponse = client.post(ApiEndpoint.journeyList, EmptyRequest())
            val fresh = response.journeys.firstOrNull { it.id == id } ?: return
            repository.batch {
                repository.markJourneySynced(id, fresh.conflictVersion)
                repository.applyRemoteJourney(fresh)
            }
        }
    }

    private suspend fun deleteJourney(id: UUID) {
        try { client.post<JourneyDeleteRequest, EmptyResponse>(ApiEndpoint.journeyDelete, JourneyDeleteRequest(id)) }
        catch (error: ApiError) { if (error != ApiError.JourneyNotFound) throw error }
        repository.deleteJourneyHard(id)
    }

    private suspend fun uploadVehicle(id: UUID) {
        val vehicle = vehicleStore.vehicle(id) ?: return
        val entity = vehicleStore.entity(id) ?: return
        val payload = VehicleSyncPayload(
            id = vehicle.id, name = vehicle.name, avatarEmoji = vehicle.avatarEmoji, odometerKm = vehicle.odometerKm,
            manualOdometerKm = vehicle.manualOdometerKm, level = vehicle.level, stickersJson = entity.stickersJSON,
            cityConsumption = vehicle.cityConsumption, highwayConsumption = vehicle.highwayConsumption, fuelPrice = vehicle.fuelPrice,
            conflictVersion = entity.conflictVersion, lastModifiedAt = entity.lastModifiedAt ?: clock(),
            vehicleType = vehicle.type.rawValue, avatarStyle = vehicle.avatarStyle, plate = vehicle.plate, plateVisible = vehicle.plateVisible,
            visibleToOthers = vehicle.visibleToOthers, fuelCurrency = vehicle.fuelCurrency, dashboardUnits = vehicle.dashboardUnits,
            cardStyle = vehicle.cardStyle, powertrain = vehicle.powertrain, electricConsumption = vehicle.electricConsumption,
            electricityPrice = vehicle.electricityPrice, electricRangeKm = vehicle.electricRangeKm, about = vehicle.about,
            make = vehicle.make, model = vehicle.model, year = vehicle.year, bodyType = vehicle.bodyType, mapVisible = vehicle.mapVisible,
            photosVisible = vehicle.photosVisible, isArchived = vehicle.isArchived, soldAt = vehicle.soldAt,
        ).withGarageFields(entity)
        try {
            val response: VehicleUpsertResponse = client.post(ApiEndpoint.vehicleUpsert, payload)
            vehicleStore.markVehicleUploaded(id, response.conflictVersion, payload.lastModifiedAt)
        } catch (error: ApiError) {
            if (error !is ApiError.ConflictDetected) throw error
            val response: VehicleListResponse = client.post(ApiEndpoint.vehicleList, EmptyRequest())
            val fresh = response.vehicles.firstOrNull { it.id == id } ?: return
            repository.batch {
                repository.markVehicleSynced(id, fresh.conflictVersion)
                repository.applyRemoteVehicle(fresh)
            }
        }
    }

    private suspend fun deleteVehicle(id: UUID) {
        try { client.post<VehicleDeleteRequest, EmptyResponse>(ApiEndpoint.vehicleDelete, VehicleDeleteRequest(id)) }
        catch (error: ApiError) { if (error != ApiError.VehicleNotFound) throw error }
        repository.deleteVehicleHard(id)
    }

    private suspend fun uploadSettings() {
        val entity = db.settingsDao().settings() ?: return
        val payload = SettingsSyncPayload(entity.id, entity.avatarEmoji ?: "🏎️", entity.themeMode ?: "dark", entity.language ?: "ru",
            entity.distanceUnit ?: "km", entity.volumeUnit ?: "liters", entity.fuelConsumption, entity.fuelPrice, entity.fuelCurrency ?: "€",
            entity.selectedVehicleId, entity.profileLevel, entity.profileXP.toInt(), entity.currentStreak, entity.bestStreak, entity.lastTripDate,
            entity.conflictVersion, entity.lastModifiedAt ?: clock())
        try {
            val response: SettingsUpsertResponse = client.post(ApiEndpoint.settingsUpsert, payload)
            db.withTransaction {
                val current = db.settingsDao().settings() ?: return@withTransaction
                if (current.id != entity.id) return@withTransaction
                db.settingsDao().upsert(current.copy(conflictVersion = response.conflictVersion,
                    syncStatus = if (current.lastModifiedAt == entity.lastModifiedAt) SyncStatus.synced.rawValue else current.syncStatus))
            }
        } catch (error: ApiError) { if (error !is ApiError.ConflictDetected) throw error }
    }

    private suspend fun uploadPhoto(id: UUID) {
        val row = db.tripDao().photo(id) ?: return
        val trip = row.tripId?.let { db.tripDao().trip(it) } ?: return
        if (trip.confirmation == TripConfirmation.draft.rawValue) return
        if (!cloudSyncEnabled() && trip.isPrivate) {
            db.tripDao().upsertPhoto(row.copy(syncStatus = SyncStatus.synced.rawValue)); return
        }
        val source = tripPhotoFiles.read(row.filename)
        if (source == null) { db.tripDao().upsertPhoto(row.copy(uploadStatus = PhotoUploadStatus.failed.rawValue)); return }
        if (row.thumbnailURL == null) {
            val bytes = photoEncoder.jpeg(source, 200, 50)
            if (bytes == null) { db.tripDao().upsertPhoto(row.copy(uploadStatus = PhotoUploadStatus.failed.rawValue)); return }
            val response = photos.uploadPhotoPart(trip.id, id, PhotoType.thumbnail, bytes, row.caption, row.timestamp)
            db.withTransaction {
                db.tripDao().photo(id)?.let { db.tripDao().upsertPhoto(it.copy(thumbnailURL = response.url)) }
            }
        }
        if (row.remoteURL == null) {
            val bytes = photoEncoder.jpeg(source, 1440, 70)
            if (bytes != null) try {
                val response = photos.uploadPhotoPart(trip.id, id, PhotoType.original, bytes, row.caption, row.timestamp)
                repository.markPhotoOriginalUploaded(id, response.url)
            } catch (_: ApiError) { /* A later sync polls thumbnail-without-original debt, even on Wi-Fi at launch. */ }
        }
        db.withTransaction {
            db.tripDao().photo(id)?.let { current -> db.tripDao().upsertPhoto(current.copy(
                uploadStatus = if (current.thumbnailURL != null) PhotoUploadStatus.uploaded.rawValue else PhotoUploadStatus.uploading.rawValue,
                syncStatus = SyncStatus.synced.rawValue, lastModifiedAt = clock(),
            )) }
        }
    }

    private suspend fun uploadVehiclePhoto(id: UUID) {
        val row = db.vehicleDao().photo(id) ?: return
        val vehicleId = row.vehicleId ?: return
        val source = vehiclePhotoFiles.read(row.filename)
        if (source == null) { db.vehicleDao().upsertPhoto(row.copy(uploadStatus = PhotoUploadStatus.failed.rawValue)); return }
        if (row.thumbnailURL != null && row.remoteURL != null) {
            if (row.isMain) client.post<VehiclePhotoMainRequest, EmptyResponse>(ApiEndpoint.vehiclePhotoMain, VehiclePhotoMainRequest(vehicleId, id))
            return
        }
        for (type in PhotoType.entries) {
            if (type == PhotoType.thumbnail && row.thumbnailURL != null || type == PhotoType.original && row.remoteURL != null) continue
            val bytes = photoEncoder.jpeg(source, if (type == PhotoType.thumbnail) 200 else 1440, if (type == PhotoType.thumbnail) 50 else 70)
            if (bytes == null) {
                db.vehicleDao().photo(id)?.let { db.vehicleDao().upsertPhoto(it.copy(uploadStatus = PhotoUploadStatus.failed.rawValue)) }
                return
            }
            val response = photos.uploadVehiclePhotoPart(vehicleId, id, type, bytes, row.isMain, row.timestamp)
            db.withTransaction {
                db.vehicleDao().photo(id)?.let { current -> db.vehicleDao().upsertPhoto(
                    if (type == PhotoType.thumbnail) current.copy(thumbnailURL = response.url, uploadStatus = PhotoUploadStatus.uploading.rawValue)
                    else current.copy(remoteURL = response.url, uploadStatus = PhotoUploadStatus.uploaded.rawValue, syncStatus = SyncStatus.synced.rawValue),
                ) }
            }
        }
    }
}
