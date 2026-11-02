@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.onezee.triptrack.feature.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import com.onezee.triptrack.data.*
import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.data.files.DirectoryTripPhotoFiles
import com.onezee.triptrack.data.files.DirectoryVehiclePhotoFiles
import com.onezee.triptrack.data.sync.*
import com.onezee.triptrack.feature.feed.TripSocialApi
import com.onezee.triptrack.model.*
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

// iOS: JourneyPublishTests.swift (Core Data setup), TripDetailFirstPaintTests.swift (screen owner)
@RunWith(RobolectricTestRunner::class)
// A plain Application: the real one starts location, sync and recovery, none of which these need.
@Config(sdk = [35], application = android.app.Application::class)
abstract class PresentationTestCase {
    protected val scheduler = TestCoroutineScheduler()
    protected val dispatcher = StandardTestDispatcher(scheduler)
    protected val scope = CoroutineScope(SupervisorJob() + dispatcher)
    protected lateinit var db: TripTrackDatabase
    protected lateinit var repository: RoomTripRepository
    protected lateinit var vehicleStore: RoomVehicleStore
    protected val sink = TestSyncSink()
    protected val api = TestSocialApi()
    protected val t0 = Instant.ofEpochSecond(1_760_000_000)
    protected val owner = UUID.randomUUID()
    protected var authorized = true
    protected var cloud = false
    private lateinit var directory: java.io.File
    private val owners = ViewModelStore()
    @Before fun setupPresentation() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TripTrackDatabase::class.java).allowMainThreadQueries().build()
        directory = Files.createTempDirectory("trip-presentation").toFile()
        val environment = DataEnvironment(cloudSyncEnabled = { cloud }, localUserId = { owner }, clock = { t0 })
        repository = RoomTripRepository(db, environment, DirectoryTripPhotoFiles(directory), sink)
        val photos = RoomVehiclePhotoStore(db, DirectoryVehiclePhotoFiles(java.io.File(directory, "vehicles")), sink)
        vehicleStore = RoomVehicleStore(db, repository, photos, environment, sink)
    }
    @After fun closePresentation() {
        owners.clear()
        scope.cancel()
        scheduler.runCurrent()
        db.close()
        directory.deleteRecursively()
        Dispatchers.resetMain()
    }
    protected fun <T : ViewModel> own(vm: T): T { owners.put(UUID.randomUUID().toString(), vm); return vm }
    protected suspend fun insert(day: Int = 0, private: Boolean = true, draft: Boolean = false, km: Double = 100.0): Trip {
        val id = UUID.randomUUID()
        val start = t0.plusSeconds(day * 86_400L)
        db.tripDao().upsertTrip(TripEntity(id, startDate = start, endDate = start.plusSeconds(3600), distance = km * 1000,
            isPrivate = private, confirmation = if (draft) "draft" else "confirmed"))
        return repository.fetchTripDetail(id)!!
    }
}

// iOS: JourneyPublishTests.swift (queue inspection)
class TestSyncSink : SyncRequestSink {
    val operations = mutableListOf<SyncRequest>()
    override fun enqueue(request: SyncRequest) { operations.add(request) }
    override fun cancel(entityId: UUID, entityType: SyncEntityType) { operations.removeAll { it.entityId == entityId && it.entityType == entityType } }
}

// iOS: APIClient call boundary in SocialFeedStore.swift / TripCommentsStore.swift
class TestSocialApi : TripSocialApi {
    val requests = mutableListOf<SocialFeedRequest>()
    var feedPage: suspend (SocialFeedRequest) -> SocialFeedResponse = { SocialFeedResponse(emptyList()) }
    var reaction: suspend (UUID, String?, String) -> Unit = { _, _, _ -> }
    var commentsPage: suspend (TripCommentsRequest) -> TripCommentsResponse = { TripCommentsResponse(emptyList()) }
    var create: suspend (SocialCommentCreateRequest) -> SocialCommentCreateResponse = { SocialCommentCreateResponse(UUID.randomUUID(), Instant.EPOCH) }
    var delete: suspend (UUID) -> Unit = {}
    var remoteTrip: suspend (UUID) -> SocialTripResponse = { throw IllegalStateException("No fixture") }
    override suspend fun feed(request: SocialFeedRequest): SocialFeedResponse { requests.add(request); return feedPage(request) }
    override suspend fun react(tripId: UUID, previous: String?, emoji: String) = reaction(tripId, previous, emoji)
    var reactionEntries: suspend (UUID) -> List<SocialReactionEntry> = { emptyList() }
    override suspend fun reactions(tripId: UUID): List<SocialReactionEntry> = reactionEntries(tripId)
    override suspend fun comments(request: TripCommentsRequest) = commentsPage(request)
    override suspend fun createComment(request: SocialCommentCreateRequest) = create(request)
    override suspend fun deleteComment(id: UUID) = delete(id)
    override suspend fun trip(id: UUID) = remoteTrip(id)
    override suspend fun photos(id: UUID): List<SocialTripPhoto> = emptyList()
    var journeyPage: suspend (UUID) -> SocialJourneyResponse = { error("No fixture") }
    override suspend fun journey(id: UUID): SocialJourneyResponse = journeyPage(id)
    fun card(id: UUID = UUID.randomUUID(), author: UUID = UUID.randomUUID(), day: Instant = Instant.EPOCH,
        reaction: String? = null, journey: SocialFeedTripJourney? = null) = SocialFeedTrip(id, SocialAuthor(author, profileLevel = 1),
        startDate = day, distance = 1000.0, duration = 60, photoCount = 0, reactionCount = if (reaction == null) 0 else 1,
        reactionBreakdown = reaction?.let { listOf(ReactionTally(it, 1)) }.orEmpty(), myReaction = reaction, badgeIds = emptyList(), journey = journey)
}
