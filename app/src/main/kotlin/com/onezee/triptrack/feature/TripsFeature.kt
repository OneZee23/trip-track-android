package com.onezee.triptrack.feature

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import com.onezee.triptrack.data.PlaceStore
import com.onezee.triptrack.data.TripRepository
import com.onezee.triptrack.data.VehicleStore
import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.data.files.TripPhotoFiles
import com.onezee.triptrack.data.sync.SyncRequestSink
import com.onezee.triptrack.feature.drafts.DraftDecision
import com.onezee.triptrack.feature.drafts.DraftDecisionQueue
import com.onezee.triptrack.feature.drafts.DraftsSeenStore
import com.onezee.triptrack.feature.drafts.DraftsViewModel
import com.onezee.triptrack.feature.feed.FeedViewModel
import com.onezee.triptrack.feature.feed.TripSocialApi
import com.onezee.triptrack.feature.journey.JourneyComposerViewModel
import com.onezee.triptrack.feature.journey.JourneyDetailViewModel
import com.onezee.triptrack.feature.journey.JourneyEditViewModel
import com.onezee.triptrack.feature.journey.JourneyManager
import com.onezee.triptrack.feature.settings.RoomWorldChanges
import com.onezee.triptrack.feature.settings.WorldChange
import com.onezee.triptrack.feature.trip.CurrentRecordingSource
import com.onezee.triptrack.feature.trip.TripDetailViewModel
import com.onezee.triptrack.feature.trip.TripSummary
import com.onezee.triptrack.location.LocationGraph
import com.onezee.triptrack.model.Journey
import com.onezee.triptrack.model.SocialFeedRequest
import com.onezee.triptrack.model.SocialFeedTrip
import com.onezee.triptrack.model.SocialJourneyResponse
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.model.TripConfirmation
import com.onezee.triptrack.logic.encodePolyline
import com.onezee.triptrack.network.ApiError
import com.onezee.triptrack.ui.TripPhotoImages
import com.onezee.triptrack.ui.navigation.TabNavigator
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import com.onezee.triptrack.location.recording.RecorderState
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.reflect.KClass
import com.onezee.triptrack.location.drafts.DraftDecision as RecordingDraftDecision

/**
 * The trip screens' singletons and view model factories (iOS: the services the Feed, trip, journey
 * and drafts screens reach through `.shared`). Built once by the app graph.
 *
 * The account wiring (sign-in, the API client, Cloud Sync) is another wave's: until it sets
 * [accountId], [socialApi], [cloudSyncEnabled] and [remotePhotoBytes], the screens behave as for a
 * signed-out person whose server is unreachable — Mine works fully, the social feed shows its error.
 */
class TripsFeature(
    val repository: TripRepository,
    private val places: PlaceStore,
    private val vehicles: VehicleStore,
    private val photoFiles: TripPhotoFiles,
    private val draftQueue: DraftDecisionQueue,
    recorderState: StateFlow<RecorderState>,
    changes: Flow<Unit>,
    private val preferences: SharedPreferences,
    private val syncSink: SyncRequestSink,
    private val isOffline: () -> Boolean,
    val worker: CoroutineDispatcher = Dispatchers.IO,
    /** The screens' «now»: relative dates, day groups, journeys' open ends. Tests fix it. */
    @Volatile var clock: () -> Instant = Instant::now,
) {
    @Volatile var accountId: () -> UUID? = { null }
    @Volatile var socialApi: TripSocialApi = NoAccountSocialApi
    @Volatile var cloudSyncEnabled: () -> Boolean = { false }
    @Volatile var remotePhotoBytes: (suspend (String) -> ByteArray)? = null

    private val scope = CoroutineScope(SupervisorJob() + worker)

    /** The view models that live as long as the app (iOS: the tabs' StateObjects). */
    private val appViewModels = ViewModelStore()

    /** Invalidations of trips and journeys: what iOS posts as `.tripSaved`, `.syncPullCompleted`. */
    private val tripChanges: Flow<Unit> = changes.shareIn(scope, SharingStarted.WhileSubscribed())

    /** The live API at call time: the account wave can replace it after the screens are built. */
    val social: TripSocialApi = DelegatingSocial { socialApi }

    val photos = TripPhotoImages(photoFiles, remote = { url -> remotePhotoBytes?.invoke(url) ?: throw ApiError.Transport("No account client") }, io = worker)

    val signedIn: () -> Boolean = { accountId() != null }

    val journeys = JourneyManager(repository, syncSink, signedIn, { cloudSyncEnabled() })

    /** iOS: TripManager.currentTrip, read by the feed's recording banner. */
    private val recording = object : CurrentRecordingSource {
        override val currentRecording: Flow<TripSummary?> = recorderState.map { state ->
            val id = state.tripId
            val start = state.startDate
            if (!state.isRecording || id == null || start == null) null
            else TripSummary.from(Trip(id = id, startDate = start, distance = state.distance, maxSpeed = state.maxSpeed,
                vehicleId = state.vehicleId, isTransfer = state.isTransfer, confirmation = state.confirmation))
        }
    }

    /** Kept for the app's life, like the iOS tab's StateObject: the list survives tab switches. */
    val feed: FeedViewModel by lazy {
        appViewModel("feed") { FeedViewModel(repository, social, worker, signedIn, isOffline, clock = { clock() }, changes = tripChanges, recording = recording) }
    }

    /** Also the source of the dot on Я: alive before the list is ever opened. */
    val drafts: DraftsViewModel by lazy {
        appViewModel("drafts") {
            DraftsViewModel(repository, draftQueue, PreferencesDraftsSeen(preferences), worker, clock = { clock() }, changes = tripChanges).also { it.refresh() }
        }
    }

    /** The recording the feed's banner shows: distance and the paused-aware clock. */
    val recorder: StateFlow<RecorderState> = recorderState

    /** iOS `@AppStorage("feedSegmentFollowingV2")`: the feed reopens on the segment it was left on. */
    var feedFollowing: Boolean
        get() = preferences.getBoolean(FEED_FOLLOWING_KEY, false)
        set(value) = preferences.edit { putBoolean(FEED_FOLLOWING_KEY, value) }

    suspend fun vehicle(id: UUID): com.onezee.triptrack.model.Vehicle? = vehicles.vehicle(id)

    /** The journey whose window holds the trip: «Открыть путешествие» instead of «Объединить». */
    suspend fun journeyContaining(tripId: UUID): Journey? = repository.journeyContaining(tripId)

    suspend fun deletePhoto(id: UUID, tripId: UUID) = repository.deletePhoto(id, tripId)

    suspend fun journey(id: UUID): Journey? = repository.fetchJourney(id)

    /** iOS DistanceUnit.current: read fresh, the settings screen writes the same key. */
    fun distanceUnit(): com.onezee.triptrack.model.DistanceUnit =
        preferences.getString(com.onezee.triptrack.model.DistanceUnit.storageKey, null)?.let(com.onezee.triptrack.model.DistanceUnit::fromRawValue)
            ?: com.onezee.triptrack.model.DistanceUnit.km

    fun offline(): Boolean = isOffline()

    /** The cached localities of journey ends (geohash 5): the geocoder's answers, never a request. */
    suspend fun localities(keys: Set<String>): Map<String, String> = repository.cachedLocalities(keys)

    /** «Make private» from a feed card's menu: the same door as the trip screen's. */
    suspend fun makePrivate(id: UUID) = repository.updatePrivacy(id, true)

    /** «Delete» from a feed card's menu. */
    suspend fun deleteTrip(id: UUID) = repository.deleteTrip(id)

    val feedNavigator = TabNavigator<TripsDestination>()
    val meNavigator = TabNavigator<TripsDestination>()

    fun tripDetail(id: UUID, preview: Trip?, social: SocialFeedTrip?): TripDetailViewModel = TripDetailViewModel(
        id, repository, this.social, worker, accountId, preview = preview, social = social, places = places,
        vehicles = vehicles, clock = { clock() }, localPhotoExists = photoFiles::exists, draftDecisions = draftQueue,
    )

    fun journeyDetail(id: UUID, social: SocialJourneyResponse? = null): JourneyDetailViewModel = JourneyDetailViewModel(
        id, repository, journeys, worker, signedIn, social = social, changes = if (social == null) tripChanges else kotlinx.coroutines.flow.emptyFlow(),
    )

    fun journeyComposer(anchor: Trip, preselected: List<Trip>? = null): JourneyComposerViewModel =
        JourneyComposerViewModel(anchor, journeys, worker, preselected)

    fun journeyEdit(journey: Journey): JourneyEditViewModel = JourneyEditViewModel(journey, repository, journeys, worker, clock())

    /** Ends what the app itself never ends: the app-life view models and the change subscription. */
    fun close() {
        appViewModels.clear()
        scope.cancel()
    }

    private inline fun <reified VM : ViewModel> appViewModel(key: String, crossinline create: () -> VM): VM {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T = create() as T
        }
        return ViewModelProvider.create(appViewModels, factory)[key, VM::class]
    }

    private companion object {
        const val FEED_FOLLOWING_KEY = "feedSegmentFollowingV2"
    }
}

/** What a tab of the trip screens can push (iOS: FeedView's destinations and `MeDest.drafts`). */
sealed interface TripsDestination : com.onezee.triptrack.ui.navigation.NavDestination {
    /** A trip, opened with what the list already knows so the first frame has its numbers. */
    data class TripDetail(val id: UUID, val preview: Trip? = null, val social: SocialFeedTrip? = null) : TripsDestination {
        override val key: Any get() = "trip:$id"
    }

    data class JourneyDetail(val id: UUID) : TripsDestination {
        override val key: Any get() = "journey:$id"
    }

    /** Someone else's journey: loaded whole from the server, never written locally. */
    data class SocialJourney(val id: UUID) : TripsDestination {
        override val key: Any get() = "social-journey:$id"
    }

    data object Drafts : TripsDestination {
        override val key: Any get() = "drafts"
    }
}

private class DelegatingSocial(private val current: () -> TripSocialApi) : TripSocialApi {
    override suspend fun feed(request: SocialFeedRequest) = current().feed(request)
    override suspend fun react(tripId: UUID, previous: String?, emoji: String) = current().react(tripId, previous, emoji)
    override suspend fun reactions(tripId: UUID) = current().reactions(tripId)
    override suspend fun comments(request: com.onezee.triptrack.model.TripCommentsRequest) = current().comments(request)
    override suspend fun createComment(request: com.onezee.triptrack.model.SocialCommentCreateRequest) = current().createComment(request)
    override suspend fun deleteComment(id: UUID) = current().deleteComment(id)
    override suspend fun trip(id: UUID) = current().trip(id)
    override suspend fun photos(id: UUID) = current().photos(id)
    override suspend fun journey(id: UUID) = current().journey(id)
}

/** Before the account wiring lands there is no client: every social call fails like an unreachable server. */
private object NoAccountSocialApi : TripSocialApi {
    private fun refuse(): Nothing = throw ApiError.Transport("No account client")
    override suspend fun feed(request: SocialFeedRequest) = refuse()
    override suspend fun react(tripId: UUID, previous: String?, emoji: String) = refuse()
    override suspend fun reactions(tripId: UUID) = refuse()
    override suspend fun comments(request: com.onezee.triptrack.model.TripCommentsRequest) = refuse()
    override suspend fun createComment(request: com.onezee.triptrack.model.SocialCommentCreateRequest) = refuse()
    override suspend fun deleteComment(id: UUID) = refuse()
    override suspend fun trip(id: UUID) = refuse()
    override suspend fun photos(id: UUID) = refuse()
    override suspend fun journey(id: UUID) = refuse()
}

/**
 * iOS: DraftDecisionQueue. The decision is written to the recording side's durable queue before
 * anything else; the queue is then worked like the notification buttons' decisions are.
 */
class RecordingDraftQueue(private val location: LocationGraph) : DraftDecisionQueue {
    override suspend fun enqueue(id: UUID, decision: DraftDecision) {
        location.queueDraftDecision(id, when (decision) {
            DraftDecision.confirm -> RecordingDraftDecision.confirm
            DraftDecision.discard -> RecordingDraftDecision.discard
        })
    }

    override suspend fun decisionsQueued() {
        location.drafts.apply()
    }
}

/** Room invalidations of the trip and journey tables, the trip screens' change signal. */
fun tripChanges(database: TripTrackDatabase): Flow<Unit> = RoomWorldChanges(database).events
    .filter { it == WorldChange.trips }
    .map { }

/** iOS: DraftsBadge, `drafts.seenAt` in UserDefaults; an ISO instant in the app's preferences here. */
private class PreferencesDraftsSeen(private val preferences: SharedPreferences) : DraftsSeenStore {
    override suspend fun seenAt(): Instant? = preferences.getString(KEY, null)?.let { runCatching { Instant.parse(it) }.getOrNull() }
    override suspend fun markSeen(at: Instant) = preferences.edit { putString(KEY, at.toString()) }

    private companion object {
        const val KEY = "drafts.seenAt"
    }
}

/** A trip as the list knows it, for the first frame of its screen (iOS `openingPreview`). */
fun TripSummary.asPreviewTrip(): Trip = Trip(
    id = id, startDate = startDate, endDate = endDate, distance = distance, maxSpeed = maxSpeed,
    averageSpeed = averageSpeed, title = title, tripDescription = description, region = region,
    isPrivate = isPrivate, isTransfer = isTransfer, vehicleId = vehicleId,
    previewPolyline = coordinates.takeIf { it.size > 1 }?.let { Trip.encodePolyline(it) },
    earnedBadgeIds = badgeIds, isOnServer = isOnServer, source = source, confirmation = confirmation, energyMode = energyMode,
)

/** A draft is still a question; nothing else of this file decides on it. */
val TripSummary.isDraft: Boolean get() = confirmation == TripConfirmation.draft
