// iOS: the `PersistenceController(inMemory: true)` + `CoreDataTripRepository` setUp shared by the store tests
package com.onezee.triptrack.data

import androidx.room.Room
import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.data.files.DirectoryTripPhotoFiles
import com.onezee.triptrack.data.files.DirectoryVehiclePhotoFiles
import com.onezee.triptrack.data.sync.SyncEntityType
import com.onezee.triptrack.data.sync.SyncRequest
import com.onezee.triptrack.data.sync.SyncRequestSink
import com.onezee.triptrack.model.SyncStatus
import com.onezee.triptrack.model.TrackPointPayload
import com.onezee.triptrack.model.TripCheckpointPayload
import com.onezee.triptrack.model.TripOrigin
import com.onezee.triptrack.model.TripPhotoMetadataPayload
import com.onezee.triptrack.model.TripSegmentPayload
import com.onezee.triptrack.model.TripSyncPayload
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * An in-memory store per test, closed after it. On iOS every store test had to nil its fields in
 * tearDown or a leftover in-memory store broke an unrelated class later in the run; closing the
 * database here is the same discipline.
 */
@RunWith(RobolectricTestRunner::class)
abstract class RoomTestCase {
    protected lateinit var db: TripTrackDatabase
    protected lateinit var repo: RoomTripRepository
    protected lateinit var tripPhotos: DirectoryTripPhotoFiles
    protected lateinit var vehiclePhotos: DirectoryVehiclePhotoFiles
    protected lateinit var filesRoot: File
    protected val sink = RecordingSink()

    /** Cloud Sync is explicit in every test: a test must never depend on a leftover switch. */
    protected var cloudSyncEnabled = false
    protected var homeZoneActive = false
    protected val localUserId: UUID = UUID.fromString("0f6e9c9a-6b7f-4c55-9e7e-3c1b2a4d5e6f")
    protected val environment = DataEnvironment(
        cloudSyncEnabled = { cloudSyncEnabled },
        localUserId = { localUserId },
        homeZoneActive = { homeZoneActive },
    )

    protected val pendingUpload = SyncStatus.pendingUpload.rawValue
    protected val synced = SyncStatus.synced.rawValue
    protected val pendingDelete = SyncStatus.pendingDelete.rawValue

    @Before
    fun openStore() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TripTrackDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        filesRoot = Files.createTempDirectory("triptrack-files").toFile()
        tripPhotos = DirectoryTripPhotoFiles(File(filesRoot, "TripPhotos"))
        vehiclePhotos = DirectoryVehiclePhotoFiles(File(filesRoot, "VehiclePhotos"))
        repo = RoomTripRepository(db, environment, tripPhotos, sink)
    }

    @After
    fun closeStore() {
        db.close()
        filesRoot.deleteRecursively()
    }

    protected fun <T> blocking(block: suspend () -> T): T = runBlocking { block() }

    /** A test body: always Unit, so JUnit sees a void method whatever the last assertion returns. */
    protected fun test(block: suspend () -> Unit) = runBlocking { block() }

    /** Rows of a table, read through SQLite itself rather than a DAO under test. */
    protected fun countRows(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    protected suspend fun insert(trip: TripEntity): UUID {
        db.tripDao().upsertTrip(trip)
        return trip.id
    }

    protected suspend fun trip(id: UUID): TripEntity? = db.tripDao().trip(id)

    protected suspend fun update(id: UUID, change: (TripEntity) -> TripEntity) {
        db.tripDao().upsertTrip(change(db.tripDao().trip(id)!!))
    }

    class RecordingSink : SyncRequestSink {
        val enqueued = mutableListOf<SyncRequest>()
        val cancelled = mutableListOf<Pair<UUID, SyncEntityType>>()
        override fun enqueue(request: SyncRequest) {
            enqueued.add(request)
        }

        override fun cancel(entityId: UUID, entityType: SyncEntityType) {
            cancelled.add(entityId to entityType)
        }
    }
}

/** iOS: the `TripSyncPayload(id:title:...)` memberwise call every pull test spells out. */
@Suppress("LongParameterList")
fun tripPayload(
    id: UUID,
    startDate: Instant,
    endDate: Instant? = startDate.plusSeconds(3_600),
    title: String? = "t",
    distance: Double = 1_000.0,
    isPrivate: Boolean = true,
    vehicleId: UUID? = null,
    isTransfer: Boolean? = null,
    previewPolyline: String? = null,
    badgesJson: String? = null,
    xpEarned: Int? = 0,
    conflictVersion: Int = 1,
    lastModifiedAt: Instant = Instant.now(),
    serverCreatedAt: Instant? = Instant.now(),
    trackPoints: List<TrackPointPayload>? = null,
    photos: List<TripPhotoMetadataPayload>? = null,
    checkpoints: List<TripCheckpointPayload>? = null,
    segments: List<TripSegmentPayload>? = null,
    source: TripOrigin? = null,
    energyMode: String? = null,
    recordingBreaks: List<Instant>? = null,
) = TripSyncPayload(
    id = id,
    title = title,
    description = null,
    startDate = startDate,
    endDate = endDate,
    distance = distance,
    maxSpeed = 20.0,
    averageSpeed = 15.0,
    fuelUsed = 0.0,
    elevation = 0.0,
    region = null,
    isPrivate = isPrivate,
    vehicleId = vehicleId,
    isTransfer = isTransfer,
    fuelCurrency = null,
    previewPolyline = previewPolyline,
    badgesJson = badgesJson,
    xpEarned = xpEarned,
    conflictVersion = conflictVersion,
    lastModifiedAt = lastModifiedAt,
    serverCreatedAt = serverCreatedAt,
    trackPoints = trackPoints,
    photos = photos,
    checkpoints = checkpoints,
    segments = segments,
    source = source,
    energyMode = energyMode,
    recordingBreaks = recordingBreaks,
)
