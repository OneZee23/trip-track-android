package com.onezee.triptrack.location

import android.content.Context
import androidx.room.Room
import com.onezee.triptrack.data.DataEnvironment
import com.onezee.triptrack.data.PlaceManager
import com.onezee.triptrack.data.PreferencesPendingPlaceHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import com.onezee.triptrack.data.RoomPlaceStore
import com.onezee.triptrack.data.RoomRecordingStore
import com.onezee.triptrack.data.RoomRoadStore
import com.onezee.triptrack.data.RoomSettingsStore
import com.onezee.triptrack.data.RoomTripRepository
import com.onezee.triptrack.data.RoomVehiclePhotoStore
import com.onezee.triptrack.data.RoomVehicleStore
import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.data.files.DirectoryTripPhotoFiles
import com.onezee.triptrack.data.files.DirectoryVehiclePhotoFiles
import com.onezee.triptrack.data.sync.SyncEntityType
import com.onezee.triptrack.data.sync.SyncRequest
import com.onezee.triptrack.data.sync.SyncRequestSink
import com.onezee.triptrack.location.drafts.DraftDecisions
import com.onezee.triptrack.location.drafts.PreferencesDraftDecisionQueue
import com.onezee.triptrack.location.recording.RawFixLog
import com.onezee.triptrack.location.recording.RecorderSessionStore
import com.onezee.triptrack.location.recording.TripDeletion
import com.onezee.triptrack.location.recording.TripFinisher
import com.onezee.triptrack.location.recording.TripNotices
import com.onezee.triptrack.location.recording.TripRecorder
import com.onezee.triptrack.location.world.BadgeLedgerStore
import com.onezee.triptrack.location.world.StoreRoadCollector
import com.onezee.triptrack.location.world.TrackPostProcessing
import com.onezee.triptrack.location.world.TripNamer
import com.onezee.triptrack.location.world.TripRewards
import com.onezee.triptrack.location.world.TripWorldEntry
import com.onezee.triptrack.logic.LocationFix
import com.onezee.triptrack.logic.TrackTestKit
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * One recording stack over an in-memory store. The database and the files stand for the disk:
 * [relaunch] builds a fresh recorder, queue and finisher over them, which is what a process death
 * leaves behind. The clock is the test's: fixes carry their own times and [now] follows them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
abstract class RecorderTestCase {
    protected val context: Context get() = RuntimeEnvironment.getApplication()
    protected lateinit var db: TripTrackDatabase
    protected lateinit var repo: RoomTripRepository
    protected lateinit var store: RoomRecordingStore
    protected lateinit var settings: RoomSettingsStore
    protected lateinit var vehicles: RoomVehicleStore
    protected lateinit var places: RoomPlaceStore
    protected lateinit var rawFixLog: RawFixLog
    protected lateinit var recorder: TripRecorder
    protected lateinit var world: TripWorldEntry
    protected lateinit var deletion: TripDeletion
    protected lateinit var drafts: DraftDecisions
    protected lateinit var postProcessing: TrackPostProcessing
    private lateinit var filesRoot: File

    protected val sink = Sink()
    protected val notices = Notices()
    protected val session = RecorderSessionStore.InMemory()
    protected val badges = BadgeLedgerStore.InMemory()
    protected var now: Instant = TrackTestKit.epoch
    protected var locationAllowed = true
    protected val localUserId: UUID = UUID.fromString("2b1d0c4e-5f60-4a7b-8c9d-0e1f2a3b4c5d")
    protected val environment = DataEnvironment(localUserId = { localUserId }, clock = { now })

    @Before
    fun openStack() {
        context.getSharedPreferences("place-history", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, TripTrackDatabase::class.java).allowMainThreadQueries().build()
        filesRoot = Files.createTempDirectory("triptrack-recording").toFile()
        repo = RoomTripRepository(db, environment, DirectoryTripPhotoFiles(File(filesRoot, "TripPhotos")), sink)
        store = RoomRecordingStore(db, environment)
        settings = RoomSettingsStore(db) { now }
        vehicles = RoomVehicleStore(db, repo, RoomVehiclePhotoStore(db, DirectoryVehiclePhotoFiles(File(filesRoot, "VehiclePhotos"))), environment)
        places = RoomPlaceStore(db) { now }
        rawFixLog = RawFixLog(File(filesRoot, RawFixLog.DIRECTORY)) { now }
        blocking { settings.loadOrCreate() }
        relaunch()
    }

    @After
    fun closeStack() {
        blocking { placeManager.settle() }
        placeScope.cancel()
        db.close()
        filesRoot.deleteRecursively()
        context.getSharedPreferences(PreferencesDraftDecisionQueue.FILE_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** What the next process builds over the same disk. */
    protected lateinit var placeManager: PlaceManager
    private val placeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    protected fun relaunch() {
        val rewards = TripRewards(
            repo, store, settings, vehicles, badges,
            zone = { ZoneOffset.UTC }, weekFields = { WeekFields.of(Locale.ROOT) }, clock = { now },
        )
        placeManager = PlaceManager(repo, places, PreferencesPendingPlaceHistory(context.getSharedPreferences("place-history", Context.MODE_PRIVATE)), placeScope)
        world = TripWorldEntry(
            repo, rewards, placeManager, sync = sink, localUserId = { localUserId },
            roads = StoreRoadCollector(RoomRoadStore(db)) { now }, clock = { now },
        )
        postProcessing = TrackPostProcessing(repo, store, sink)
        val namer = TripNamer(repo, store, { null }, sink, isOffline = { true }, autoTitle = { "auto" })
        lateinit var current: TripRecorder
        deletion = TripDeletion(repo, rawFixLog, sink, notices) { current.state.value.isRecording }
        val finisher = TripFinisher(world, postProcessing, namer, deletion, notices, clock = { now })
        current = TripRecorder(store, repo, settings, rawFixLog, session, finisher, { locationAllowed }, clock = { now }, placeManager = placeManager)
        recorder = current
        drafts = DraftDecisions(
            PreferencesDraftDecisionQueue(context), repo, world, deletion, notices, sink,
            activeTripId = { current.state.value.tripId },
        )
    }

    protected fun <T> blocking(block: suspend () -> T): T = runBlocking { block() }

    /** A test body: always Unit, whatever the last assertion returns. */
    protected fun test(block: suspend () -> Unit) = runBlocking { block() }

    /** Feeds fixes as the provider would, moving the clock with them. */
    protected suspend fun drive(fixes: List<LocationFix>) {
        for (fix in fixes) {
            now = fix.timestamp
            recorder.onProviderFix(fix, 0.0)
        }
    }

    /** Straight north at [speed] m/s, one fix a second, from [from] seconds after the epoch. */
    protected fun straight(seconds: IntRange, speed: Double = 10.0, startNorth: Double = 0.0, accuracy: Double = 8.0) =
        seconds.map { t ->
            TrackTestKit.fix(north = startNorth + (t - seconds.first) * speed, speed = speed, after = t.toDouble(), accuracy = accuracy)
        }

    protected suspend fun storedPointCount(tripId: UUID): Int = db.tripDao().countTrackPoints(tripId)

    class Sink : SyncRequestSink {
        val enqueued = mutableListOf<SyncRequest>()
        val cancelled = mutableListOf<Pair<UUID, SyncEntityType>>()
        override fun enqueue(request: SyncRequest) {
            enqueued += request
        }

        override fun cancel(entityId: UUID, entityType: SyncEntityType) {
            cancelled += entityId to entityType
        }
    }

    class Notices : TripNotices {
        val draftPrompts = mutableListOf<UUID>()
        val cleared = mutableListOf<UUID>()
        var startedNoticeCleared = 0
        override fun draftConfirmPrompt(tripId: UUID, metres: Double) {
            draftPrompts += tripId
        }

        override fun clearDraftPrompt(tripId: UUID) {
            cleared += tripId
        }

        override fun clearDraftStartedNotice() {
            startedNoticeCleared++
        }
    }
}
