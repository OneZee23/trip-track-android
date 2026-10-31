// iOS: TripTrackApp service wiring, SyncEnqueuer, SyncCoordinator foreground trigger, AuthService.performFirstSync
package com.onezee.triptrack.feature.account

import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.onezee.triptrack.data.DataEnvironment
import com.onezee.triptrack.data.RoomTripRepository
import com.onezee.triptrack.data.files.DirectoryTripPhotoFiles
import com.onezee.triptrack.data.sync.SyncAction
import com.onezee.triptrack.data.sync.SyncEntityType
import com.onezee.triptrack.data.sync.SyncRequest
import com.onezee.triptrack.model.SyncStatus
import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.feature.journey.JourneyManager
import com.onezee.triptrack.sync.SyncEngine
import com.onezee.triptrack.sync.SyncScheduler
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test

class SyncHostTests : AccountTestCase() {
    private lateinit var workManager: WorkManager
    private lateinit var host: SyncHost
    private val engines = mutableListOf<SyncEngine>()
    private var cancelled = 0
    private val t0: Instant = Instant.parse("2026-10-01T09:00:00Z")

    @Before fun setUpHost() {
        // The app's own Configuration.Provider initializes WorkManager on demand.
        workManager = WorkManager.getInstance(context)
        workManager.cancelAllWork().result.get()
        host = SyncHost(
            createEngine = {
                SyncEngine.create(context, db, client, "test.host", { localUser }, { accountSettings.cloudSyncEnabled }, { null },
                    { false }, {}, {}, workManager = workManager).also { engines += it }
            },
            cancelScheduledWork = {
                cancelled++
                workManager.cancelUniqueWork(SyncScheduler.immediateName)
                workManager.cancelUniqueWork(SyncScheduler.periodicName)
            },
            cloudSyncEnabled = { accountSettings.cloudSyncEnabled },
            scope = scope,
        )
    }

    @After fun closeEngines() {
        for (engine in engines) { engine.clearAccountWork(); engine.close() }
    }

    private fun request(id: UUID = UUID.randomUUID()) = SyncRequest(SyncEntityType.trip, id, SyncAction.upload)

    private fun publicTrip(): UUID = runBlocking {
        val id = UUID.randomUUID()
        db.tripDao().upsertTrip(TripEntity(id = id, startDate = t0, endDate = t0.plusSeconds(600), isPrivate = false,
            syncStatus = SyncStatus.pendingUpload.rawValue, userId = localUser, lastModifiedAt = t0))
        id
    }

    private fun uniqueState(name: String): WorkInfo.State? = workManager.getWorkInfosForUniqueWork(name).get().lastOrNull()?.state

    private fun awaitUnique(name: String, predicate: (WorkInfo.State?) -> Boolean) = runBlocking {
        withTimeout(5_000) { while (!predicate(uniqueState(name))) delay(20) }
    }

    /** Requests made while the launch reads the session wait for the account's engine. */
    @Test fun requestsBeforeTheAccountIsKnownReachItsEngine() = runBlocking {
        seedSession(accountA)
        val id = publicTrip()
        host.enqueue(request(id))
        host.switchAccount(accountA)
        val engine = assertNotNull(host.engine.value)
        withTimeout(5_000) { while (!engine.queue.containsEntity(id, SyncEntityType.trip)) delay(10) }
    }

    /** A guest has no engine; the enqueuer would refuse anyway, so nothing is kept. */
    @Test fun aGuestHasNoEngineAndRequestsAreDropped() = runBlocking {
        host.switchAccount(null)
        host.enqueue(request())
        assertNull(host.engine.value)
        assertNull(host.awaitCoordinator())
        assertEquals(0, cancelled, "nothing was scheduled for a guest, so nothing is cancelled either")
    }

    @Test fun eachAccountGetsItsOwnEngineAndSigningOutClosesIt() = runBlocking {
        seedSession(accountA)
        host.switchAccount(accountA)
        val first = assertNotNull(host.engine.value)
        assertEquals(first.coordinator, host.awaitCoordinator())
        host.switchAccount(accountA)
        assertTrue(host.engine.value === first, "the same account keeps its engine")
        seedSession(accountB)
        host.switchAccount(accountB)
        val second = assertNotNull(host.engine.value)
        assertTrue(second !== first)
        host.switchAccount(null)
        assertNull(host.engine.value)
        assertEquals(1, cancelled)
    }

    /** iOS: foreground triggers a pass and the 300-second timer; here also a persistent wake-up. */
    @Test fun theForegroundOrdersAnImmediatePass() {
        seedSession(accountA)
        runBlocking { host.switchAccount(accountA) }
        awaitUnique(SyncScheduler.periodicName) { it == WorkInfo.State.ENQUEUED || it == WorkInfo.State.RUNNING }
        host.onForeground()
        awaitUnique(SyncScheduler.immediateName) { it != null }
        host.onBackground()
    }

    /** A local change (a repository write through the sink) wakes the queue, with the gates deciding. */
    @Test fun aLocalChangeIsQueuedAndWakesTheWorker() {
        seedSession(accountA)
        runBlocking { host.switchAccount(accountA) }
        workManager.cancelUniqueWork(SyncScheduler.immediateName).result.get()
        val id = publicTrip()
        host.enqueue(request(id))
        awaitUnique(SyncScheduler.immediateName) { it != null && it != WorkInfo.State.CANCELLED }
    }

    /** The gate still decides: a private trip does not leave with Cloud Sync off. */
    @Test fun thePrivacyGateStillDecides() = runBlocking {
        seedSession(accountA)
        host.switchAccount(accountA)
        val id = UUID.randomUUID()
        db.tripDao().upsertTrip(TripEntity(id = id, startDate = t0, endDate = t0.plusSeconds(600), isPrivate = true, userId = localUser))
        host.enqueue(request(id))
        delay(300)
        assertTrue(!host.engine.value!!.queue.containsEntity(id, SyncEntityType.trip))
    }

    /**
     * The publish flows of the screens go through the app's sink, that is through the signed-in
     * account's enqueuer and its gates: with Cloud Sync off a published journey and its legs leave,
     * and hiding a trip that is on the server queues its unpublish.
     */
    @Test fun publishingTripsAndJourneysGoesThroughTheAccountsGates() = runBlocking {
        seedSession(accountA)
        host.switchAccount(accountA)
        val engine = host.engine.value!!
        val repository = RoomTripRepository(db, DataEnvironment({ accountSettings.cloudSyncEnabled }, { localUser }),
            DirectoryTripPhotoFiles(File(context.cacheDir, "TripPhotos-${UUID.randomUUID()}")), host)
        val leg = UUID.randomUUID()
        db.tripDao().upsertTrip(TripEntity(id = leg, startDate = t0, endDate = t0.plusSeconds(3600), isPrivate = true,
            syncStatus = SyncStatus.synced.rawValue, userId = localUser, lastModifiedAt = t0))
        val journeys = JourneyManager(repository, host, signedIn = { true }, cloudSyncEnabled = { false })
        val journey = journeys.create(listOf(repository.fetchTripDetail(leg)!!), "Кавказ")
        journeys.publish(journey.id)
        withTimeout(5_000) {
            while (!engine.queue.containsEntity(journey.id, SyncEntityType.journey) || !engine.queue.containsEntity(leg, SyncEntityType.trip)) delay(10)
        }
        db.tripDao().upsertTrip(db.tripDao().trip(leg)!!.copy(serverCreatedAt = t0))
        engine.queue.cancelOperations(leg, SyncEntityType.trip)
        repository.updatePrivacy(leg, true)
        withTimeout(5_000) { while (!engine.queue.contains(leg, SyncEntityType.trip, SyncAction.unpublish)) delay(10) }
    }

    /** iOS: performFirstSync. Cloud Sync on re-marks everything and the recovery queues it. */
    @Test fun theFirstSyncMirrorsEverythingOnlyWithCloudSyncOn() = runBlocking {
        seedSession(accountA)
        routes["/sync/pull"] = { ok("{\"trips\":{\"upserted\":[],\"deleted\":[]},\"vehicles\":{\"upserted\":[],\"deleted\":[]},\"photos\":{\"upserted\":[],\"deleted\":[]},\"settings\":null,\"serverTime\":\"2026-10-01T09:00:00.000Z\"}") }
        val id = UUID.randomUUID()
        db.tripDao().upsertTrip(TripEntity(id = id, startDate = t0, endDate = t0.plusSeconds(600), isPrivate = true,
            syncStatus = SyncStatus.synced.rawValue, userId = localUser, lastModifiedAt = t0))
        host.switchAccount(accountA)
        val engine = host.engine.value!!
        host.firstSync()
        assertEquals(SyncStatus.synced.rawValue, db.tripDao().trip(id)!!.syncStatus, "without Cloud Sync nothing is re-marked")
        prefs.set(AccountSettings.CLOUD_KEY, "true")
        host.firstSync()
        assertEquals(SyncStatus.pendingUpload.rawValue, db.tripDao().trip(id)!!.syncStatus)
        assertTrue(engine.queue.containsEntity(id, SyncEntityType.trip) || requestsTo("/trips/upsert").isNotEmpty())
    }

    /** The worker of the app's WorkManager configuration: with nobody signed in a pass succeeds at once. */
    @Test fun theWorkerPassSucceedsForAGuest() = runBlocking {
        host.switchAccount(null)
        assertEquals(androidx.work.ListenableWorker.Result.success(), AccountSyncWorker.pass(host.awaitCoordinator()))
    }
}
