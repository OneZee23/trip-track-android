package com.onezee.triptrack.sync

import androidx.room.withTransaction
import com.onezee.triptrack.data.*
import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.data.sync.*
import com.onezee.triptrack.model.*
import com.onezee.triptrack.model.serialization.IsoInstantSerializer
import com.onezee.triptrack.network.*
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.JsonPrimitive

// iOS: SyncCoordinator.swift. UI/profile/cache owners are supplied by AppGraph.
class SyncCoordinator(
    private val client: ApiClient,
    private val db: TripTrackDatabase,
    private val repository: TripRepository,
    private val queue: SyncQueue,
    private val enqueuer: SyncEnqueuer,
    private val pullApplier: PullApplier,
    private val cursorStore: LastSyncedAtStore,
    private val storeIdentity: suspend () -> String?,
    private val accountId: () -> UUID?,
    private val isAuthorized: () -> Boolean,
    private val isOffline: () -> Boolean,
    private val cloudSyncEnabled: () -> Boolean,
    private val localUserId: () -> UUID,
    private val isRecording: () -> Boolean,
    private val restoreVehiclePhotos: suspend () -> Unit,
    private val syncAccountProfile: suspend () -> Unit,
    private val clock: () -> Instant = Instant::now,
) {
    private val syncLock = Mutex()
    private val mutableCompleted = MutableSharedFlow<SyncPullCompleted>(extraBufferCapacity = 16)
    val completed = mutableCompleted.asSharedFlow()
    private val recovery = PendingSyncRecovery(db)

    suspend fun recoverPendingEntities() {
        for (request in recovery.pendingOperations(localUserId())) {
            if (request.action == SyncAction.delete) {
                if (!queue.contains(request.entityId, request.entityType, SyncAction.delete)) {
                    queue.cancelOperations(request.entityId, request.entityType)
                    enqueuer.enqueueIfAllowed(request)
                }
            } else if (!queue.containsEntity(request.entityId, request.entityType)) enqueuer.enqueueIfAllowed(request)
        }
    }

    /** False asks WorkManager to retry; capped failed rows remain visible without an endless job. */
    suspend fun runFullSync(): Boolean {
        if (!isAuthorized()) return true
        if (isOffline()) return false
        if (!syncLock.tryLock()) return false
        try {
            recoverPendingEntities()
            if (isRecording()) {
                queue.processQueue()
                syncAccountProfile()
                return !hasUnsettledWork()
            }
            var succeeded = true
            if (cloudSyncEnabled()) {
                succeeded = try { runPull() } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { false }
                try { restoreVehiclePhotos() } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { succeeded = false }
            }
            for (id in repository.photosAwaitingOriginal()) {
                if (!queue.containsEntity(id, SyncEntityType.photo)) enqueuer.enqueueIfAllowed(SyncRequest(SyncEntityType.photo, id, SyncAction.upload))
            }
            queue.processQueue()
            queue.retryFailed()
            syncAccountProfile()
            return succeeded && !hasUnsettledWork()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return false }
        finally { syncLock.unlock() }
    }

    private fun hasUnsettledWork() = queue.state.value.let { state ->
        state.isSyncing || state.pending.isNotEmpty() || state.failed.any { it.retryCount < SyncQueue.maxRetries }
    }

    private suspend fun runPull(): Boolean {
        val owner = accountId() ?: return true
        val identity = storeIdentity()
        val response: SyncPullResponse = client.post(ApiEndpoint.syncPull, SyncPullRequest(cursorStore.get(owner, identity)))
        if (accountId() != owner || !isAuthorized() || !cloudSyncEnabled() || isRecording()) return false
        val applied = pullApplier.apply(response)
        parseTime(response.serverTime)?.let { cursorStore.set(it, owner, identity) }
        var succeeded = true
        response.ownedCounts?.let { counts ->
            try { reconcileAfterPull(counts) } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { succeeded = false }
            if (shouldHeal(counts.trips, repository.countLiveTrips(), cursorStore.healedStore(owner), identity)) {
                try {
                    cursorStore.markHealed(owner, requireNotNull(identity))
                    cursorStore.reset(owner)
                    // Advancing a global cursor past an excluded library type would strand it.
                    val healed: SyncPullResponse = client.post(ApiEndpoint.syncPull, SyncPullRequest(null,
                        listOf("trip", "vehicle", "photo", "journey", "discovery")))
                    if (accountId() != owner || !isAuthorized() || !cloudSyncEnabled() || isRecording()) return false
                    val healedIds = pullApplier.apply(healed)
                    parseTime(healed.serverTime)?.let { cursorStore.set(it, owner, identity) }
                    repository.recomputeAllVehicleOdometers()
                    mutableCompleted.emit(SyncPullCompleted(healedIds))
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { succeeded = false }
            }
        }
        mutableCompleted.emit(SyncPullCompleted(applied))
        return succeeded
    }

    private suspend fun reconcileAfterPull(counts: SyncPullResponse.OwnedCounts) {
        val trips = db.tripDao().syncedTrips()
        val vehicles = db.vehicleDao().vehicles().filter { it.syncStatus == SyncStatus.synced.rawValue }
        val photos = db.tripDao().syncedPhotos()
        val tripsLost = trips.size > counts.trips
        val vehiclesLost = vehicles.size > counts.vehicles
        val photosLost = photos.size > counts.photos
        if (!tripsLost && !vehiclesLost && !photosLost) return
        val manifest: SyncManifestResponse = client.post(ApiEndpoint.syncManifest, EmptyRequest())
        if (manifest.truncated == true) return
        val work = mutableListOf<SyncRequest>()
        db.withTransaction {
            if (tripsLost) for (row in trips.filter { it.id !in manifest.trips }) {
                val current = db.tripDao().trip(row.id) ?: continue
                if (current.syncStatus != SyncStatus.synced.rawValue) continue
                db.tripDao().upsertTrip(current.copy(syncStatus = SyncStatus.pendingUpload.rawValue))
                work.add(SyncRequest(SyncEntityType.trip, row.id, SyncAction.upload))
            }
            if (vehiclesLost) for (row in vehicles.filter { it.id !in manifest.vehicles }) {
                val current = db.vehicleDao().vehicle(row.id) ?: continue
                if (current.syncStatus != SyncStatus.synced.rawValue) continue
                db.vehicleDao().upsertVehicle(current.copy(syncStatus = SyncStatus.pendingUpload.rawValue))
                work.add(SyncRequest(SyncEntityType.vehicle, row.id, SyncAction.upload))
            }
            if (photosLost) for (row in photos.filter { it.id !in manifest.photos }) {
                val current = db.tripDao().photo(row.id) ?: continue
                if (current.syncStatus != SyncStatus.synced.rawValue) continue
                db.tripDao().upsertPhoto(current.copy(syncStatus = SyncStatus.pendingUpload.rawValue))
                work.add(SyncRequest(SyncEntityType.photo, row.id, SyncAction.upload))
            }
        }
        for (request in work) enqueuer.enqueueIfAllowed(request)
    }

    /** Sign-in/Cloud Sync enable uses this path; the next pass starts with a full pull. */
    suspend fun requestFullPull(markLocalPending: Boolean = false) {
        accountId()?.let { cursorStore.reset(it) }
        if (markLocalPending) repository.markAllPendingUpload()
        recoverPendingEntities()
    }

    /** A zone change republishes public trips, preserving their full local geometry. */
    suspend fun privacyZoneChanged() {
        val ids = db.withTransaction {
            db.tripDao().publicTrips().filter { it.serverCreatedAt != null && it.confirmation != TripConfirmation.draft.rawValue }.map { row ->
                db.tripDao().upsertTrip(row.copy(lastModifiedAt = clock(), syncStatus = SyncStatus.pendingUpload.rawValue))
                row.id
            }
        }
        for (id in ids) enqueuer.enqueueIfAllowed(SyncRequest(SyncEntityType.trip, id, SyncAction.update))
    }

    companion object {
        fun shouldHeal(serverTrips: Int, localTrips: Int, latchedStoreIdentity: String?, currentStoreIdentity: String?): Boolean =
            currentStoreIdentity != null && serverTrips > localTrips && latchedStoreIdentity != currentStoreIdentity
        private fun parseTime(value: String) = runCatching { NetworkJson.wire.decodeFromJsonElement(IsoInstantSerializer, JsonPrimitive(value)) }.getOrNull()
    }
}
