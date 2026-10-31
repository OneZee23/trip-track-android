// iOS: TripTrackApp service wiring (SyncCoordinator.shared, SyncQueue.shared, SyncEnqueuer),
// AuthService.performFirstSync
package com.onezee.triptrack.feature.account

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.onezee.triptrack.data.sync.SyncEntityType
import com.onezee.triptrack.data.sync.SyncRequest
import com.onezee.triptrack.data.sync.SyncRequestSink
import com.onezee.triptrack.sync.SyncCoordinator
import com.onezee.triptrack.sync.SyncEngine
import com.onezee.triptrack.sync.SyncQueueState
import com.onezee.triptrack.sync.SyncWorker
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The sync engine of the signed-in account. iOS has one queue for the process and clears it when
 * the account changes; here the queue persists on disk per account (`SyncEngine`), so the engine is
 * rebuilt for each account and closed when nobody is signed in.
 *
 * It is also the app's [SyncRequestSink]: every repository write goes through it to the current
 * engine's enqueuer, where the gates decide what may leave the phone (sign-in, drafts, privacy,
 * Cloud Sync). A request that arrives before the account is known, or while the engine is being
 * replaced, waits and is handed to the new engine; with no account it is dropped, as the enqueuer
 * would refuse it anyway. The pending flags in Room bring back anything lost with the process.
 */
class SyncHost(
    /** Builds the engine of the account the token store holds now (`SyncEngine.create`). */
    private val createEngine: suspend () -> SyncEngine,
    /** With nobody signed in, the periodic and immediate work would only wake the phone. */
    private val cancelScheduledWork: () -> Unit,
    private val cloudSyncEnabled: () -> Boolean,
    private val scope: CoroutineScope,
) : SyncRequestSink, AccountSyncControl {
    private sealed interface Request {
        data class Enqueue(val request: SyncRequest) : Request
        data class Cancel(val id: UUID, val type: SyncEntityType) : Request
    }

    private val switching = Mutex()
    private val gate = Any()
    private val waiting = mutableListOf<Request>()
    private var decided = false
    private var account: UUID? = null
    private val mutableEngine = MutableStateFlow<SyncEngine?>(null)
    private val ready = MutableStateFlow(false)
    @Volatile private var foreground = false

    val engine: StateFlow<SyncEngine?> = mutableEngine.asStateFlow()

    /** The current account's queue, empty with nobody signed in. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val queueState: Flow<SyncQueueState> = mutableEngine.flatMapLatest { it?.queue?.state ?: flowOf(SyncQueueState()) }

    override suspend fun switchAccount(accountId: UUID?) = switching.withLock {
        if (ready.value && accountId == account) return@withLock
        // Hold new requests until the next engine exists: they belong to whoever is signed in now.
        val old = synchronized(gate) {
            decided = false
            mutableEngine.value.also { mutableEngine.value = null }
        }
        old?.close()
        val next = accountId?.let { createEngine() }
        account = accountId
        if (next == null) { if (old != null) cancelScheduledWork() } else {
            next.start()
            if (foreground) next.onForeground()
        }
        val held = synchronized(gate) {
            mutableEngine.value = next
            decided = true
            waiting.toList().also { waiting.clear() }
        }
        if (next != null) for (request in held) deliver(next, request)
        ready.value = true
    }

    override fun enqueue(request: SyncRequest) = route(Request.Enqueue(request))
    override fun cancel(entityId: UUID, entityType: SyncEntityType) = route(Request.Cancel(entityId, entityType))

    private fun route(request: Request) {
        val target = synchronized(gate) {
            if (!decided) { waiting += request; return }
            mutableEngine.value
        } ?: return
        deliver(target, request)
    }

    private fun deliver(engine: SyncEngine, request: Request) = when (request) {
        is Request.Enqueue -> engine.requests.enqueue(request.request)
        is Request.Cancel -> engine.requests.cancel(request.id, request.type)
    }

    fun onForeground() {
        foreground = true
        mutableEngine.value?.onForeground()
    }

    fun onBackground() {
        foreground = false
        mutableEngine.value?.onBackground()
    }

    override suspend fun clearAccountWork() { mutableEngine.value?.clearAccountWork() }

    override suspend fun firstSync() {
        val current = mutableEngine.value ?: return
        // iOS: performFirstSync. Signing in alone never mass-uploads personal content: only with
        // Cloud Sync on is everything re-marked (the data-loss-safe full mirror), and recovery turns
        // the flags into the same trip, car, settings, journey and photo operations iOS enqueues.
        // Without it the pass still pushes trips made public by hand.
        if (cloudSyncEnabled()) {
            current.trips.markAllPendingUpload()
            current.coordinator.recoverPendingEntities()
        }
        current.coordinator.runFullSync()
    }

    override fun requestSync() {
        val current = mutableEngine.value ?: return
        scope.launch { current.coordinator.runFullSync() }
    }

    /** The WorkManager worker's coordinator: waits for the launch decision, null for a guest. */
    suspend fun awaitCoordinator(): SyncCoordinator? {
        ready.first { it }
        return mutableEngine.value?.coordinator
    }
}

/**
 * The sync worker of the app's WorkManager configuration. `SyncScheduler` enqueues `SyncWorker` by
 * class name; this factory answers for it with a worker that finds the CURRENT account's
 * coordinator, and with nobody signed in simply succeeds.
 */
class AccountSyncWorkerFactory(private val coordinator: suspend () -> SyncCoordinator?) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        if (workerClassName == SyncWorker::class.java.name) AccountSyncWorker(appContext, workerParameters, coordinator) else null
}

/** iOS: SyncCoordinator.runFullSync from a background wake-up. False asks WorkManager to retry. */
class AccountSyncWorker(
    context: Context,
    parameters: WorkerParameters,
    private val coordinator: suspend () -> SyncCoordinator?,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val current = coordinator() ?: return Result.success()
        return if (current.runFullSync()) Result.success() else Result.retry()
    }
}
