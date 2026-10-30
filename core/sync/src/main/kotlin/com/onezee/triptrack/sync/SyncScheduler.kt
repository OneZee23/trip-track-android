package com.onezee.triptrack.sync

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

// iOS: SyncCoordinator.start / foregroundTimer; Android persistent scheduling uses WorkManager.
class SyncScheduler(
    private val workManager: WorkManager,
    private val coordinator: SyncCoordinator,
    private val networkMonitor: NetworkMonitor,
    private val scope: CoroutineScope,
) : AutoCloseable {
    private var timer: Job? = null
    private var networkJob: Job? = null
    private var localChangeJob: Job? = null
    fun start() {
        if (networkJob != null) return
        networkMonitor.start()
        workManager.enqueueUniquePeriodicWork(periodicName, ExistingPeriodicWorkPolicy.KEEP, periodicRequest())
        networkJob = scope.launch {
            var previous: NetworkState? = null
            networkMonitor.state.collect { current ->
                if (!current.isOffline && (previous?.isOffline == true || current.isOnWiFi && previous?.isOnWiFi != true)) {
                    requestSync()
                }
                previous = current
            }
        }
        scope.launch { coordinator.recoverPendingEntities() }
    }
    fun onForeground() {
        timer?.cancel()
        requestSync()
        timer = scope.launch {
            while (isActive) { delay(300_000); requestSync() }
        }
    }
    fun onBackground() { timer?.cancel(); timer = null }
    @Synchronized fun onLocalChange() {
        localChangeJob?.cancel()
        localChangeJob = scope.launch {
            // One persistent wake-up per edit burst; the enqueuer already starts an immediate drain.
            delay(250)
            workManager.enqueueUniqueWork(immediateName, ExistingWorkPolicy.APPEND_OR_REPLACE, immediateRequest())
        }
    }
    private fun requestSync() {
        // Foreground and network return should drain immediately; persistent work survives process death.
        onLocalChange()
        scope.launch { coordinator.runFullSync() }
    }
    override fun close() {
        onBackground()
        localChangeJob?.cancel(); localChangeJob = null
        networkJob?.cancel(); networkJob = null
        networkMonitor.close()
    }
    companion object {
        const val immediateName = "triptrack.sync.immediate"
        const val periodicName = "triptrack.sync.periodic"
        private fun constraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        fun immediateRequest(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).build()
        // WorkManager's minimum period is 15 minutes. The 300-second timer runs only in foreground.
        fun periodicRequest(): PeriodicWorkRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(constraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).build()
    }
}

// iOS: SyncCoordinator.runFullSync. Created by SyncWorkerFactory from the app's manual AppGraph.
class SyncWorker(context: Context, parameters: WorkerParameters, private val coordinator: suspend () -> SyncCoordinator) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = if (coordinator().runFullSync()) Result.success() else Result.retry()
}

// iOS: application service wiring. Install in WorkManager Configuration before its initializer runs.
class SyncWorkerFactory(private val coordinator: suspend () -> SyncCoordinator) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        if (workerClassName == SyncWorker::class.java.name) SyncWorker(appContext, workerParameters, coordinator) else null
}
