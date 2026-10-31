package com.onezee.triptrack.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.onezee.triptrack.data.DataEnvironment
import com.onezee.triptrack.data.GeohashOf
import com.onezee.triptrack.data.PlaceStore
import com.onezee.triptrack.data.RoomRecordingStore
import com.onezee.triptrack.data.RoomRoadStore
import com.onezee.triptrack.data.SettingsStore
import com.onezee.triptrack.data.TerritoryStore
import com.onezee.triptrack.data.TripRepository
import com.onezee.triptrack.data.VehicleStore
import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.data.sync.SyncAction
import com.onezee.triptrack.data.sync.SyncEntityType
import com.onezee.triptrack.data.sync.SyncRequest
import com.onezee.triptrack.data.sync.SyncRequestSink
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.location.auto.ActivityTransitions
import com.onezee.triptrack.location.auto.AutoRecordSettings
import com.onezee.triptrack.location.auto.AutoTripController
import com.onezee.triptrack.location.auto.CarConnectionWatcher
import com.onezee.triptrack.location.auto.PreferencesAutoRecordSettings
import com.onezee.triptrack.location.auto.PreferencesAutoTripMemory
import com.onezee.triptrack.location.drafts.DraftDecision
import com.onezee.triptrack.location.drafts.DraftDecisions
import com.onezee.triptrack.location.drafts.PreferencesDraftDecisionQueue
import com.onezee.triptrack.location.permissions.PermissionLedger
import com.onezee.triptrack.location.permissions.PreferencesPermissionLedger
import com.onezee.triptrack.location.recording.PauseSource
import com.onezee.triptrack.location.recording.PreferencesRecorderSessionStore
import com.onezee.triptrack.location.recording.RawFixLog
import com.onezee.triptrack.location.recording.StartRefusal
import com.onezee.triptrack.location.recording.StartResult
import com.onezee.triptrack.location.recording.StopOutcome
import com.onezee.triptrack.location.recording.TripDeletion
import com.onezee.triptrack.location.recording.TripFinisher
import com.onezee.triptrack.location.recording.TripRecorder
import com.onezee.triptrack.location.service.RecordingNotifications
import com.onezee.triptrack.location.service.RecordingService
import com.onezee.triptrack.location.service.RecordingTexts
import com.onezee.triptrack.location.world.AndroidReverseGeocoder
import com.onezee.triptrack.location.world.PreferencesBadgeLedgerStore
import com.onezee.triptrack.location.world.RevealIngest
import com.onezee.triptrack.location.world.StoreRoadCollector
import com.onezee.triptrack.location.world.TrackPostProcessing
import com.onezee.triptrack.location.world.TripNamer
import com.onezee.triptrack.location.world.TripRewards
import com.onezee.triptrack.location.world.TripWorldEntry
import com.onezee.triptrack.logic.GeohashEncoder
import com.onezee.triptrack.model.AutoRecordMode
import com.onezee.triptrack.model.DistanceUnit
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Implemented by the Application: the service and the receivers find the graph through it. */
interface LocationGraphOwner {
    val locationGraph: LocationGraph
}

/**
 * The recording side of the app, built once per process (the iOS singletons: TripManager,
 * LocationManager, AutoTripService, NotificationManager, DraftDecisionQueue, RawFixLog).
 *
 * Everything that changes recording state runs on one thread, the counterpart of the main actor;
 * database work suspends onto Room's own executors from there.
 */
// iOS: TripTrackApp.swift, StoreHealth.swift (recording service ownership).
class LocationGraph(
    private val context: Context,
    db: TripTrackDatabase,
    val trips: TripRepository,
    private val settings: SettingsStore,
    vehicles: VehicleStore,
    places: PlaceStore,
    private val territory: TerritoryStore,
    private val sync: SyncRequestSink,
    private val environment: DataEnvironment,
    /** The app's words in the app's language. */
    private val strings: () -> AppStrings,
    /** iOS: TripAutoTitle.generate. */
    autoTitle: (Instant) -> String,
    isOffline: () -> Boolean,
    /** The fog module's ingest of a finished track; nothing until core:map provides it. */
    reveal: RevealIngest = RevealIngest.None,
    val autoRecord: AutoRecordSettings = PreferencesAutoRecordSettings(context),
) {
    private val dispatcher = Executors.newSingleThreadExecutor { Thread(it, "TripTrackRecorder") }.asCoroutineDispatcher()
    val scope = CoroutineScope(SupervisorJob() + dispatcher)

    /** A test process outlives its Application; release the recorder's thread with its owner. */
    fun close() {
        scope.cancel()
        dispatcher.close()
    }

    /** Read fresh at every start and launch: a pull or the settings screen may change it. */
    @Volatile
    var distanceUnit: DistanceUnit = DistanceUnit.km
        private set

    val rawFixLog = RawFixLog(File(context.noBackupFilesDir, RawFixLog.DIRECTORY))
    val permissionLedger: PermissionLedger = PreferencesPermissionLedger(context)
    val notifications = RecordingNotifications(context, { RecordingTexts(strings(), distanceUnit) })
    val activityTransitions = ActivityTransitions(context)

    private val recordingStore = RoomRecordingStore(db, environment)
    private val rewards = TripRewards(trips, recordingStore, settings, vehicles, PreferencesBadgeLedgerStore(context))
    val world = TripWorldEntry(trips, rewards, places, sync, { environment.localUserId() }, StoreRoadCollector(RoomRoadStore(db)), reveal)
    val postProcessing = TrackPostProcessing(trips, recordingStore, sync)
    val namer = TripNamer(trips, recordingStore, AndroidReverseGeocoder(context), sync, isOffline, autoTitle)
    private var recorderRef: TripRecorder? = null
    val deletion = TripDeletion(trips, rawFixLog, sync, notifications) { recorderRef?.state?.value?.isRecording == true }
    private val finisher = TripFinisher(world, postProcessing, namer, deletion, notifications)
    val recorder = TripRecorder(
        recordingStore, trips, settings, rawFixLog, PreferencesRecorderSessionStore(context), finisher,
        canUseLocation = ::hasLocationPermission,
    ).also { recorderRef = it }
    val autoTrip = AutoTripController(
        recorder, autoRecord, PreferencesAutoTripMemory(context), notifications,
        selectVehicle = ::selectVehicle,
        ensureRecordingService = { RecordingService.start(context) },
        scope = scope,
    )
    private val draftQueue = PreferencesDraftDecisionQueue(context)
    val drafts = DraftDecisions(
        draftQueue, trips, world, deletion, notifications, sync,
        activeTripId = { recorder.state.value.tripId },
        rebuildTerritory = { territory.rebuildFromTrips(GeohashOf { lat, lon -> GeohashEncoder.encode(lat, lon, 6) }) },
    )
    private val carConnection = CarConnectionWatcher(context) { connected -> scope.launch { autoTrip.onCarConnection(connected) } }
    private val started = CompletableDeferred<Unit>()

    /**
     * Application.onCreate. Recovery first, so nothing starts a recording on top of an orphan: a
     * fresh orphan resumes without a question, an older one waits for the record screen. Then the
     * decisions taken on notifications while the app was dead, the post-processing a dead process
     * did not finish, and the auto-start registrations.
     */
    fun start() {
        notifications.createChannels()
        recorder.listener = autoTrip
        scope.launch {
            try {
                refreshDistanceUnit()
                rawFixLog.mark("launch")
                rawFixLog.purgeOld()
                val recovery = recorder.recoverOrphans()
                if (recovery?.isFresh == true && recorder.adoptRecoverable()) RecordingService.start(context)
            } finally {
                started.complete(Unit)
            }
            drafts.apply()
            postProcessing.processUnprocessedTrips()
            rearmAutoRecord()
            namer.retryUntitled({ id -> trips.fetchEntity(id)?.startDate }) { delay(1_500) }
        }
    }

    /** Receivers and the service wait for the launch recovery before acting on recording state. */
    suspend fun awaitStarted() = started.await()

    /** After a reboot, an app update or a change of the auto-record mode. */
    fun rearmAutoRecord() {
        if (autoRecord.mode == AutoRecordMode.off) {
            activityTransitions.unregister()
            carConnection.stop()
        } else {
            activityTransitions.register()
            carConnection.start()
        }
    }

    /** The record screen's start. The service is started here, while the app is in front. */
    suspend fun startRecording(vehicleId: UUID? = null, isTransfer: Boolean = false): StartResult {
        awaitStarted()
        refreshDistanceUnit()
        val result = recorder.start(vehicleId, isTransfer)
        if (result is StartResult.Started && !RecordingService.start(context)) {
            recorder.stop()
            return StartResult.Refused(StartRefusal.unknown)
        }
        return result
    }

    suspend fun stopRecording(): StopOutcome = recorder.stop()

    suspend fun togglePause() = recorder.togglePause(PauseSource.screen)

    /** «Continue» on the recovery question. */
    suspend fun continueRecovered(): Boolean {
        awaitStarted()
        val adopted = recorder.adoptRecoverable()
        if (adopted) RecordingService.start(context)
        return adopted
    }

    /** «Finish and save» on the recovery question: adopt, then the normal stop. */
    suspend fun finishRecovered(): StopOutcome {
        awaitStarted()
        if (!recorder.adoptRecoverable()) return StopOutcome.NotRecording
        return recorder.stop()
    }

    /** The app came to the screen: a recording without its service gets it back, a stale one may end. */
    fun onAppForeground() {
        scope.launch {
            awaitStarted()
            if (recorder.state.value.isRecording) RecordingService.start(context)
            autoTrip.onAppForeground()
            drafts.apply()
        }
    }

    /**
     * The system refused the foreground service (no location permission, or a background start it
     * did not allow). A recording without its service writes nothing: end it and say why, as iOS
     * says when an automatic start is refused.
     */
    suspend fun serviceRefused() {
        awaitStarted()
        if (!recorder.state.value.isRecording) return
        recorder.stop()
        val background = context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        notifications.autoStartFailed(if (hasLocationPermission() && background) StartRefusal.unknown else StartRefusal.locationDenied)
    }

    /** iOS: DraftDecisionQueue.enqueue, written before anything else can fail. */
    fun queueDraftDecision(tripId: UUID, decision: DraftDecision) = draftQueue.enqueue(tripId, decision)

    fun hasLocationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private suspend fun refreshDistanceUnit() {
        distanceUnit = settings.settings()?.distanceUnit?.let(DistanceUnit::fromRawValue) ?: DistanceUnit.km
    }

    /** iOS: SettingsManager.vehicleId(forDeviceName:) then selectVehicle. Only a car that may take trips. */
    private suspend fun selectVehicle(id: UUID) {
        val recordable = trips.recordableVehicleId(id) ?: return
        val saved = settings.save { it.copy(selectedVehicleId = recordable) } ?: return
        sync.enqueue(SyncRequest(SyncEntityType.settings, saved.id, SyncAction.update))
    }
}
