// iOS: SyncQueue.swift (SyncOperation.EntityType, SyncOperation.Action), SyncEnqueuer.enqueue
package com.onezee.triptrack.data.sync

import java.util.UUID

/** iOS: SyncOperation.EntityType. Raw values are the queue's vocabulary; never rename them. */
enum class SyncEntityType(val rawValue: String) {
    trip("trip"),
    vehicle("vehicle"),
    photo("photo"),
    // A car's photo is personal data like the car and leaves only with Cloud Sync on (0.6.4).
    vehiclePhoto("vehiclePhoto"),
    settings("settings"),
    journey("journey"),
    discovery("discovery"),
}

/** iOS: SyncOperation.Action. `unpublish` deletes the server copy and keeps the local trip. */
enum class SyncAction(val rawValue: String) {
    upload("upload"),
    update("update"),
    delete("delete"),
    unpublish("unpublish"),
}

/**
 * «Put this on the queue». The repositories decide WHEN an edit needs to travel, as on iOS; whether
 * it may leave the phone (privacy, Cloud Sync, drafts) is the enqueuer's gate in `core:sync`.
 *
 * `hasServerCopy` answers, for a photo delete, the one question nobody can ask after the row is
 * gone: did the server ever see this photo.
 */
data class SyncRequest(
    val entityType: SyncEntityType,
    val entityId: UUID,
    val action: SyncAction,
    val hasServerCopy: Boolean? = null,
)

/** iOS: SyncEnqueuer.enqueue and SyncQueue.cancelOperations, as seen from the store. */
interface SyncRequestSink {
    fun enqueue(request: SyncRequest)

    /** Drop queued operations of one entity, before an operation that supersedes them. */
    fun cancel(entityId: UUID, entityType: SyncEntityType)

    /** No queue yet (tests, previews, the store before `core:sync` is attached). */
    object None : SyncRequestSink {
        override fun enqueue(request: SyncRequest) = Unit
        override fun cancel(entityId: UUID, entityType: SyncEntityType) = Unit
    }
}
