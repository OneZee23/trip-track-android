package com.onezee.triptrack.sync

import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.data.sync.*
import com.onezee.triptrack.model.TripConfirmation
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

// iOS: SyncEnqueuer.swift. Repository callbacks are serialized, including cancel-before-delete.
class SyncEnqueuer(
    private val db: TripTrackDatabase,
    private val queue: SyncQueue,
    private val isAuthorized: () -> Boolean,
    private val cloudSyncEnabled: () -> Boolean,
    private val scope: CoroutineScope,
    private val onLocalChange: () -> Unit,
) : SyncRequestSink {
    private sealed interface Command {
        data class Enqueue(val request: SyncRequest) : Command
        data class Cancel(val id: UUID, val type: SyncEntityType) : Command
    }
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val job = scope.launch {
        for (command in commands) when (command) {
            is Command.Cancel -> queue.cancelOperations(command.id, command.type)
            is Command.Enqueue -> if (enqueueIfAllowed(command.request)) {
                onLocalChange()
                scope.launch { queue.processQueue() }
            }
        }
    }

    override fun enqueue(request: SyncRequest) { check(commands.trySend(Command.Enqueue(request)).isSuccess) }
    override fun cancel(entityId: UUID, entityType: SyncEntityType) {
        check(commands.trySend(Command.Cancel(entityId, entityType)).isSuccess)
    }
    fun close() { commands.close(); job.cancel() }

    suspend fun enqueueIfAllowed(request: SyncRequest): Boolean {
        if (!isAuthorized() || isDraftTrip(request) || !shouldEnqueue(request)) return false
        queue.enqueue(SyncOperation(request.entityType, request.entityId, request.action))
        return true
    }

    suspend fun isDraftTrip(request: SyncRequest): Boolean {
        if (request.action == SyncAction.delete) return false
        val tripId = when (request.entityType) {
            SyncEntityType.trip -> request.entityId
            SyncEntityType.photo -> db.tripDao().photo(request.entityId)?.tripId
            else -> null
        } ?: return false
        return db.tripDao().trip(tripId)?.confirmation == TripConfirmation.draft.rawValue
    }

    suspend fun shouldEnqueue(request: SyncRequest): Boolean {
        if (cloudSyncEnabled()) return true
        val (type, id, action) = request
        return when (type) {
            SyncEntityType.trip -> when (action) {
                SyncAction.unpublish -> true
                SyncAction.delete -> db.tripDao().trip(id)?.serverCreatedAt != null
                else -> db.tripDao().trip(id)?.isPrivate == false
            }
            SyncEntityType.journey -> when (action) {
                SyncAction.unpublish -> true
                SyncAction.delete -> db.journeyDao().journey(id)?.serverCreatedAt != null
                else -> db.journeyDao().journey(id)?.isPrivate == false
            }
            SyncEntityType.photo -> {
                val photo = db.tripDao().photo(id)
                if (action == SyncAction.delete) allowsPhotoDelete(request.hasServerCopy, false, photo?.let { it.remoteURL != null })
                else photo?.tripId?.let { db.tripDao().trip(it)?.isPrivate == false } ?: false
            }
            SyncEntityType.vehicle, SyncEntityType.vehiclePhoto, SyncEntityType.settings, SyncEntityType.discovery -> false
        }
    }

    companion object {
        fun allowsPhotoDelete(hasServerCopy: Boolean?, cloudSyncEnabled: Boolean, lookup: Boolean?): Boolean =
            cloudSyncEnabled || (hasServerCopy ?: lookup ?: false)
    }
}
