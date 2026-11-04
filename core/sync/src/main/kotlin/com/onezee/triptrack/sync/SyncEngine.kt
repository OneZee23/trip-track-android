package com.onezee.triptrack.sync

import android.content.Context
import androidx.work.WorkManager
import com.onezee.triptrack.data.*
import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.data.files.DirectoryTripPhotoFiles
import com.onezee.triptrack.data.files.DirectoryVehiclePhotoFiles
import com.onezee.triptrack.data.sync.SyncRequestSink
import com.onezee.triptrack.network.ApiClient
import com.onezee.triptrack.network.SecretsApi
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*

// iOS: service wiring in TripTrackApp + SyncCoordinator.start. One engine per account/store/API host.
class SyncEngine private constructor(
    val trips: TripRepository,
    val vehicles: VehicleStore,
    val vehiclePhotos: VehiclePhotoStore,
    val requests: SyncRequestSink,
    val queue: SyncQueue,
    val coordinator: SyncCoordinator,
    val scheduler: SyncScheduler,
    private val enqueuer: SyncEnqueuer,
    private val scope: CoroutineScope,
) : AutoCloseable {
    fun start() = scheduler.start()
    fun onForeground() = scheduler.onForeground()
    fun onBackground() = scheduler.onBackground()
    fun clearAccountWork() { queue.clearAll() }
    override fun close() { scheduler.close(); enqueuer.close(); scope.cancel() }

    companion object {
        /** AppGraph rebuilds this engine on account changes; Worker's factory resolves the CURRENT coordinator. */
        suspend fun create(
            context: Context,
            db: TripTrackDatabase,
            client: ApiClient,
            apiScope: String,
            localUserId: () -> UUID,
            cloudSyncEnabled: () -> Boolean,
            zone: () -> PrivacyZoneSpec?,
            isRecording: () -> Boolean,
            syncAccountProfile: suspend () -> Unit,
            purgeVehiclePreferences: suspend (UUID) -> Unit,
            invalidateTripSnapshots: () -> Unit = {},
            reloadSettings: suspend () -> Unit = {},
            defaultHiddenEdgeMetres: () -> Int? = { null },
            workManager: WorkManager = WorkManager.getInstance(context),
        ): SyncEngine = withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val owner = client.tokenStore.state.value.accountId
            val identity = SyncStoreIdentity.get(db)
            val accountId = { client.tokenStore.state.value.accountId }
            val authorized = { owner != null && accountId() == owner && client.tokenStore.state.value.isSignedIn }
            val network = NetworkMonitor(app)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val persistenceScope = "$apiScope/$owner/$identity"
            val name = MessageDigest.getInstance("SHA-256").digest(persistenceScope.toByteArray()).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
            val queue = SyncQueue(FileQueuePersistence(File(app.noBackupFilesDir, "Sync/$name.json"), persistenceScope), authorized,
                { network.state.value.isOffline })
            lateinit var scheduler: SyncScheduler
            val enqueuer = SyncEnqueuer(db, queue, authorized, cloudSyncEnabled, scope) { scheduler.onLocalChange() }
            val tripFiles = DirectoryTripPhotoFiles(File(app.noBackupFilesDir, "TripPhotos"))
            val vehicleFiles = DirectoryVehiclePhotoFiles(File(app.noBackupFilesDir, "VehiclePhotos"))
            val environment = DataEnvironment(cloudSyncEnabled, localUserId, { zone() != null }, defaultHiddenEdgeMetres)
            val repository = RoomTripRepository(db, environment, tripFiles, enqueuer)
            val vehiclePhotos = RoomVehiclePhotoStore(db, vehicleFiles, enqueuer)
            val vehicles = RoomVehicleStore(db, repository, vehiclePhotos, environment, enqueuer)
            val discoveries = RoomDiscoveryStore(db)
            val discussion = DiscussionArchive(client, File(app.noBackupFilesDir, "TripDiscussions"))
            val discoverySync = DiscoverySync(SecretsApi(client), discoveries) { authorized() && cloudSyncEnabled() }
            val loader = ManualTripTrackLoader(repository, client, accountId, cloudSyncEnabled)
            queue.configure(APISyncTransport(client, db, repository, vehicles, tripFiles, vehicleFiles, cloudSyncEnabled, zone,
                loader, discussion::archive, discoverySync::retry, defaultHiddenEdgeMetres = defaultHiddenEdgeMetres))
            val applier = PullApplier(repository, discoveries, vehiclePhotos, purgeVehiclePreferences, invalidateTripSnapshots, reloadSettings)
            val restore = VehiclePhotoRestore(client, db, vehicleFiles, vehiclePhotos, cloudSyncEnabled, accountId)
            val coordinator = SyncCoordinator(client, db, repository, queue, enqueuer, applier,
                LastSyncedAtStore(app.getSharedPreferences("triptrack.sync", Context.MODE_PRIVATE), apiScope),
                { SyncStoreIdentity.get(db) }, accountId, authorized, { network.state.value.isOffline }, cloudSyncEnabled, localUserId,
                isRecording, restore::run, syncAccountProfile)
            scheduler = SyncScheduler(workManager, coordinator, network, scope)
            SyncEngine(repository, vehicles, vehiclePhotos, enqueuer, queue, coordinator, scheduler, enqueuer, scope)
        }
    }
}
