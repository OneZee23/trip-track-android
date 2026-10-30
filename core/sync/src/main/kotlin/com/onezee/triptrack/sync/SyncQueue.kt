package com.onezee.triptrack.sync

import com.onezee.triptrack.data.sync.SyncAction
import com.onezee.triptrack.data.sync.SyncEntityType
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// iOS: SyncQueue.swift (SyncOperation). IDs identify attempts independently of entity IDs.
data class SyncOperation(
    val entityType: SyncEntityType,
    val entityId: UUID,
    val action: SyncAction,
    val id: UUID = UUID.randomUUID(),
    val createdAt: Instant = Instant.now(),
    val retryCount: Int = 0,
    val lastError: String? = null,
)

// iOS: SyncQueue.swift (SyncTransport)
interface SyncTransport {
    suspend fun execute(operation: SyncOperation)
    suspend fun uploadTripsBatch(operations: List<SyncOperation>, onChunkSynced: (Int) -> Unit): Set<UUID> = emptySet()
}

// iOS: SyncQueue.swift (@Published snapshot)
data class SyncQueueState(
    val pending: List<SyncOperation> = emptyList(),
    val failed: List<SyncOperation> = emptyList(),
    val isSyncing: Boolean = false,
    val currentOperation: SyncOperation? = null,
    val batchTotal: Int = 0,
    val batchProcessed: Int = 0,
) {
    val pendingCount: Int get() = pending.size + failed.size
}

// iOS: SyncQueue.swift. Network awaits never hold the state lock.
class SyncQueue(
    private val persistence: QueuePersistence = QueuePersistence.Memory(),
    private val isAuthorized: () -> Boolean,
    private val isOffline: () -> Boolean = { false },
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    private val lock = Any()
    private val restored = persistence.read()
    private val pending = restored.pending.toMutableList()
    private val failed = restored.failed.toMutableList()
    private var generation = 0L
    private var retrying = false
    private var active: SyncOperation? = null
    private var transport: SyncTransport? = null
    private val mutableState = MutableStateFlow(SyncQueueState(pending.toList(), failed.toList()))
    val state = mutableState.asStateFlow()
    val pendingCount: Int get() = state.value.pendingCount

    fun configure(transport: SyncTransport) = synchronized(lock) { this.transport = transport }

    fun contains(entityId: UUID, type: SyncEntityType, action: SyncAction): Boolean = synchronized(lock) {
        (pending + failed + listOfNotNull(active)).any { it.entityId == entityId && it.entityType == type && it.action == action }
    }

    fun containsEntity(entityId: UUID, type: SyncEntityType): Boolean = synchronized(lock) {
        (pending + failed + listOfNotNull(active)).any { it.entityId == entityId && it.entityType == type }
    }

    fun enqueue(operation: SyncOperation): Unit = synchronized(lock) {
        if (pending.any { it.entityId == operation.entityId && it.entityType == operation.entityType && it.action == operation.action }) return
        pending.add(operation)
        publish()
    }

    fun cancelOperations(entityId: UUID, entityType: SyncEntityType) = synchronized(lock) {
        pending.removeAll { it.entityId == entityId && it.entityType == entityType }
        failed.removeAll { it.entityId == entityId && it.entityType == entityType }
        // An already sent request cannot be recalled; its late failure must not resurrect it.
        if (active?.let { it.entityId == entityId && it.entityType == entityType } == true) active = null
        publish()
    }

    fun clearAll() = synchronized(lock) {
        generation++
        pending.clear()
        failed.clear()
        active = null
        publish()
    }

    suspend fun processQueue() {
        val session = synchronized(lock) {
            val configured = transport ?: return
            if (state.value.isSyncing || !isAuthorized() || isOffline() || pending.isEmpty()) return
            pending.sortBy { priority.indexOf(it.entityType).let { index -> if (index < 0) Int.MAX_VALUE else index } }
            publish()
            mutableState.value = state.value.copy(isSyncing = true, batchTotal = pending.size, batchProcessed = 0)
            configured to generation
        }
        val (currentTransport, started) = session
        try {
            val batch = synchronized(lock) { pending.filter { it.entityType == SyncEntityType.trip && it.action == SyncAction.upload } }
            if (batch.size >= 2) {
                val handled = currentTransport.uploadTripsBatch(batch) { count -> synchronized(lock) {
                    if (generation == started) mutableState.value = state.value.copy(batchProcessed = state.value.batchProcessed + count)
                } }
                synchronized(lock) {
                    // A new edit queued while this chunk was in flight has a different operation ID.
                    val handledIds = batch.filter { it.entityId in handled }.map { it.id }.toSet()
                    pending.removeAll { it.id in handledIds }
                    publish()
                }
            }
            while (true) {
                val op = synchronized(lock) {
                    if (generation != started || !isAuthorized() || isOffline() || pending.isEmpty()) return@processQueue
                    val next = pending.removeAt(0)
                    active = next
                    try { publish() } catch (error: Exception) {
                        active = null
                        pending.add(0, next)
                        throw error
                    }
                    next
                }
                try {
                    currentTransport.execute(op)
                } catch (cancelled: CancellationException) {
                    synchronized(lock) {
                        if (generation == started && active?.id == op.id) pending.add(0, op)
                    }
                    throw cancelled
                } catch (error: Exception) {
                    synchronized(lock) {
                        if (generation == started && active?.id == op.id) {
                            // Keep server messages and payloads out of the persisted queue.
                            val reason = if (error is com.onezee.triptrack.network.ApiError) error.message else error.javaClass.simpleName
                            failed.add(op.copy(retryCount = op.retryCount + 1, lastError = reason))
                        }
                    }
                } finally {
                    synchronized(lock) {
                        active = null
                        mutableState.value = state.value.copy(batchProcessed = state.value.batchProcessed + 1)
                        publish()
                    }
                }
            }
        } finally {
            synchronized(lock) {
                active = null
                mutableState.value = state.value.copy(isSyncing = false, currentOperation = null, batchTotal = 0, batchProcessed = 0)
                publish()
            }
        }
    }

    suspend fun retryFailed() {
        val snapshot = synchronized(lock) {
            if (retrying || isOffline() || !isAuthorized()) return
            val eligible = failed.filter { it.retryCount < maxRetries }
            if (eligible.isEmpty()) return
            retrying = true
            eligible to generation
        }
        try {
            val (eligible, started) = snapshot
            pause((1L shl eligible.maxOf { it.retryCount }) * 1000)
            synchronized(lock) {
                if (generation != started) return
                val ids = eligible.map { it.id }.toSet()
                val remaining = failed.filter { it.id in ids }
                failed.removeAll { it.id in ids }
                pending.addAll(remaining)
                publish()
            }
            processQueue()
        } finally { synchronized(lock) { retrying = false } }
    }

    suspend fun retryFailedNow() {
        synchronized(lock) {
            pending.addAll(failed.map { it.copy(retryCount = 0, lastError = null) })
            failed.clear()
            publish()
        }
        processQueue()
    }

    private fun publish() {
        persistence.write(QueueSnapshot(listOfNotNull(active) + pending, failed.toList()))
        mutableState.value = state.value.copy(pending = pending.toList(), failed = failed.toList(), currentOperation = active)
    }

    companion object {
        const val maxRetries = 5
        private val priority = listOf(SyncEntityType.settings, SyncEntityType.vehicle, SyncEntityType.journey,
            SyncEntityType.trip, SyncEntityType.photo, SyncEntityType.discovery)
    }
}
