package com.onezee.triptrack.location.recording

import com.onezee.triptrack.data.RecordingStore
import com.onezee.triptrack.data.SettingsStore
import com.onezee.triptrack.data.TripRepository
import com.onezee.triptrack.logic.LocationFix
import com.onezee.triptrack.logic.RecordingMath
import com.onezee.triptrack.logic.decide
import com.onezee.triptrack.logic.withRecordingBreaks
import com.onezee.triptrack.model.FixGate
import com.onezee.triptrack.model.TrackPoint
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.model.TripConfirmation
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * iOS: TripManager.swift with the recording half of MapViewModel.swift (start, pause, stop and the
 * force-quit recovery). One recording at a time; every operation runs under one lock, the way iOS
 * runs them on the main actor, so a notification action, an auto-start and a tap cannot interleave.
 *
 * The math is `RecordingMath` from core:logic (Kalman, continuity gate, the two anchors). This class
 * adds what iOS keeps on the managed object: batch saves, the pause boundary written at once, the
 * stop pipeline and the orphan recovery. Everything after the stop (rewards, post-processing,
 * places, names) is the [finisher]'s, outside the lock.
 */
class TripRecorder(
    private val store: RecordingStore,
    private val trips: TripRepository,
    private val settings: SettingsStore,
    private val rawFixLog: RawFixLog,
    private val session: RecorderSessionStore,
    private val finisher: TripFinisher,
    /** False while location access is denied: no recording can start (iOS: `locationDenied`). */
    private val canUseLocation: () -> Boolean,
    private val clock: () -> Instant = Instant::now,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(RecorderState())
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    // iOS: recordedLocationPublisher. Only points admitted to the saved route draw the live trail.
    private val _recordedPoints = MutableSharedFlow<TrackPoint>(extraBufferCapacity = 64)
    val recordedPoints: SharedFlow<TrackPoint> = _recordedPoints.asSharedFlow()

    var listener: RecorderListener = object : RecorderListener {}

    private var math: RecordingMath? = null
    private val liveSpeed = LiveSpeed()
    private var unsavedPoints = 0
    private var lastSave: Instant = Instant.EPOCH

    // MARK: Start

    /**
     * iOS: MapViewModel.startRecording. Either a recording runs afterwards or the refusal says why.
     * The car is checked here, at the stamp, not on the screens before it: a selection may still
     * point at a car archived or sold on another phone.
     */
    suspend fun start(
        vehicleId: UUID? = null,
        isTransfer: Boolean = false,
        confirmation: TripConfirmation = TripConfirmation.confirmed,
    ): StartResult {
        val result = mutex.withLock {
            val current = _state.value
            if (current.isRecording) return@withLock StartResult.Refused(StartRefusal.alreadyRecording)
            if (!canUseLocation()) return@withLock StartResult.Refused(StartRefusal.locationDenied)
            // The recovery question is open: an auto-start would race the orphan's adoption.
            if (current.recovery != null) return@withLock StartResult.Refused(StartRefusal.recoveryPending)
            // A passenger's trip carries no car, and the choice never becomes the next trip's.
            val stamped = if (isTransfer) null else trips.recordableVehicleId(vehicleId ?: settings.settings()?.selectedVehicleId)
            val now = clock()
            val id = UUID.randomUUID()
            store.createTrip(id, now, stamped, isTransfer, confirmation)
            // The raw journal opens with the trip: without the file «GPS was dead» and «GPS worked
            // but the fixes were thrown away» look the same even in an exported log.
            rawFixLog.begin(id)
            begin(Trip(id = id, startDate = now, isTransfer = isTransfer, vehicleId = stamped, confirmation = confirmation), null)
            StartResult.Started(id)
        }
        if (result is StartResult.Started) listener.recordingDidStart(result.tripId)
        return result
    }

    /** iOS: backdateTrip. An auto-start learns when the drive began only after it started, before any fix. */
    suspend fun backdate(to: Instant) = mutex.withLock {
        val current = math ?: return@withLock
        store.backdateStart(current.trip.id, to)
        math = RecordingMath(current.trip.copy(startDate = to).withRecordingBreaks(current.trip.recordingBreaks))
        _state.value = _state.value.copy(startDate = to)
    }

    private fun begin(trip: Trip, restored: RecorderSession?) {
        val recordingMath = RecordingMath(trip)
        val now = clock()
        if (restored?.isPaused == true) recordingMath.setPaused(true, restored.pauseStartedAt ?: now)
        math = recordingMath
        unsavedPoints = 0
        lastSave = now
        liveSpeed.reset(now)
        val paused = restored?.isPaused == true
        _state.value = RecorderState(
            tripId = trip.id,
            isRecording = true,
            isPaused = paused,
            startDate = trip.startDate,
            vehicleId = trip.vehicleId,
            isTransfer = trip.isTransfer,
            confirmation = trip.confirmation,
            distance = trip.distance,
            maxSpeed = trip.maxSpeed,
            pausedSeconds = restored?.pausedSeconds ?: 0.0,
            pauseStartedAt = if (paused) restored?.pauseStartedAt ?: now else null,
        )
        saveSession()
    }

    // MARK: Fixes

    /**
     * iOS: RealGPSProvider.locationManager(_:didUpdateLocations:) through TripManager.handleNewLocation.
     * Every provider fix is judged by `FixGate`; while recording, the verdict goes to the raw journal.
     * An accepted fix moves the speedometer and, unless paused, the recording.
     */
    suspend fun onProviderFix(fix: LocationFix, ageSeconds: Double) {
        val accepted = mutex.withLock {
            val recording = math
            val decision = FixGate.decide(fix.horizontalAccuracy, ageSeconds, fix.speed, recording != null)
            if (recording != null) rawFixLog.record(RawFixLog.line(fix, decision))
            if (decision != FixGate.Decision.Accept) return@withLock null
            val now = clock()
            liveSpeed.onFix(fix.speed, now)
            var point: TrackPoint? = null
            if (recording != null && !recording.isPaused) {
                point = recording.handleNewLocation(fix, now)
                if (point != null) {
                    // Batch saves: every 10 points or 15 seconds, counted on stored points as on iOS.
                    unsavedPoints++
                    if (unsavedPoints >= SAVE_BATCH_SIZE || secondsBetween(lastSave, now) >= SAVE_INTERVAL) flush()
                }
            }
            _state.value = _state.value.copy(
                speed = liveSpeed.speed,
                gpsAccuracy = fix.horizontalAccuracy,
                gpsStale = false,
                distance = recording?.trip?.distance ?: _state.value.distance,
                maxSpeed = recording?.trip?.maxSpeed ?: _state.value.maxSpeed,
            )
            point?.let { _recordedPoints.tryEmit(it) }
            _state.value
        }
        accepted?.let { listener.onAcceptedFix(it) }
    }

    /** The half-second tick of the screen and the notification: speed decay and «GPS lost». */
    suspend fun tick() = mutex.withLock {
        val now = clock()
        liveSpeed.tick(now)
        val stale = liveSpeed.isStale(now)
        val current = _state.value
        if (current.speed != liveSpeed.speed || current.gpsStale != stale) {
            _state.value = current.copy(speed = liveSpeed.speed, gpsStale = stale)
        }
    }

    // MARK: Pause

    suspend fun togglePause(source: PauseSource = PauseSource.screen) {
        setPaused(!_state.value.isPaused, source)
    }

    /**
     * iOS: MapViewModel.togglePause and TripManager.isPaused. The boundary is saved the moment the
     * pause starts, with the points recorded so far: a recalculation or a restart must never count
     * the paused stretch. Both anchors and the filter are dropped, so the first fix after the pause
     * does not add the jump from where the car stopped.
     */
    suspend fun setPaused(paused: Boolean, source: PauseSource = PauseSource.screen) {
        val changed = mutex.withLock {
            val recording = math ?: return@withLock false
            if (recording.isPaused == paused) return@withLock false
            val now = clock()
            recording.setPaused(paused, now)
            val current = _state.value
            if (paused) {
                flush()
                store.saveBreaks(recording.trip.id, recording.trip.recordingBreaks)
                _state.value = current.copy(isPaused = true, pauseStartedAt = now)
            } else {
                val pausedFor = current.pauseStartedAt?.let { secondsBetween(it, now) } ?: 0.0
                _state.value = current.copy(isPaused = false, pauseStartedAt = null, pausedSeconds = current.pausedSeconds + pausedFor)
            }
            saveSession()
            true
        }
        // Nothing may end a trip the person paused: a stop timer armed before the pause dies here.
        if (changed && paused) listener.manualPause()
    }

    // MARK: Stop

    /**
     * iOS: MapViewModel.stopRecording and TripManager.stopTrip. The end is the trimmed tail, else
     * the caller's hint (an auto-stop passes the moment the odometer last moved), else now. The
     * final numbers overwrite the live ones through the same distance door as every other count.
     */
    suspend fun stop(suggestedEndDate: Instant? = null): StopOutcome {
        val trip = mutex.withLock {
            val recording = math ?: return StopOutcome.NotRecording
            // Out of the recording state before the slow part, so a second stop bails here.
            math = null
            flush(recording)
            rawFixLog.end()
            val tripId = recording.trip.id
            val points = store.trackPoints(tripId)
            val now = clock()
            val start = recording.trip.startDate
            val end = RecordingEnd.endDate(points, start, suggestedEndDate, now)
            val live = recording.trip
            val stats = RecordingEnd.finalStats(points, live.recordingBreaks, live.distance, live.maxSpeed)
            val elapsed = secondsBetween(start, end)
            val average = if (points.size > 1) (if (elapsed > 0) stats.distance / elapsed else 0.0) else live.averageSpeed
            store.finish(tripId, end, stats.distance, stats.maxSpeed, average, RecordingEnd.previewPolyline(points))
            _state.value = RecorderState(gpsAccuracy = _state.value.gpsAccuracy, isFinishing = true)
            session.save(null)
            trips.fetchTripDetail(tripId)?.let { it.withRecordingBreaks(it.recordingBreaks) }
        }
        listener.recordingDidEnd()
        try {
            return finisher.finish(trip)
        } finally {
            _state.value = _state.value.copy(isFinishing = false)
        }
    }

    /** iOS: TripManager.recordingFinishPreview. Only when a confirmation opens; it reads the whole track. */
    suspend fun finishPreview(suggestedEndDate: Instant? = null): com.onezee.triptrack.model.RecordingFinishPreview? = mutex.withLock {
        val recording = math ?: return@withLock null
        flush(recording)
        val points = store.trackPoints(recording.trip.id)
        val end = RecordingEnd.endDate(points, recording.trip.startDate, suggestedEndDate, clock())
        val stats = RecordingEnd.finalStats(points, recording.trip.recordingBreaks, recording.trip.distance, recording.trip.maxSpeed)
        com.onezee.triptrack.model.RecordingFinishPreview(
            recording.trip.id, end, stats.distance, secondsBetween(recording.trip.startDate, end), stats.maxSpeed,
        )
    }

    // MARK: Recovery

    /**
     * iOS: TripManager.cleanupOrphanedTrips. Empty and junk orphans go; every other one but the most
     * recent is closed at its last fix; the most recent, if younger than six hours, waits in
     * `state.recovery` (fresh ones are adopted by the caller without asking).
     */
    suspend fun recoverOrphans(): RecoverableRecording? = mutex.withLock {
        if (math != null) return@withLock null
        val verdict = OrphanTriage.triage(store.orphans(), clock())
        verdict.delete.forEach { store.deleteOrphan(it) }
        for ((id, end) in verdict.close) store.closeOrphan(id, end, RecordingEnd.previewPolyline(store.trackPoints(id)))
        _state.value = _state.value.copy(recovery = verdict.recoverable)
        verdict.recoverable
    }

    /**
     * iOS: adoptRecoverableOrphan. The orphan becomes the active recording again (both «Continue»
     * and «Finish and save» go through here; the latter stops right after). A recording the process
     * died in while paused comes back paused (see [RecorderSession]).
     */
    suspend fun adoptRecoverable(): Boolean {
        val adopted = mutex.withLock {
            if (math != null) return@withLock null
            val recovery = _state.value.recovery ?: return@withLock null
            val orphan = recovery.orphan
            val trip = Trip(
                id = orphan.id, startDate = orphan.startDate, distance = orphan.distance,
                maxSpeed = orphan.maxSpeed, averageSpeed = orphan.averageSpeed, vehicleId = orphan.vehicleId,
                isTransfer = orphan.isTransfer, confirmation = orphan.confirmation,
            ).withRecordingBreaks(orphan.recordingBreaks)
            // The journal on disk outlived the process; resume appends instead of truncating.
            rawFixLog.resume(orphan.id)
            begin(trip, session.load()?.takeIf { it.tripId == orphan.id })
            orphan.id
        } ?: return false
        listener.recordingDidStart(adopted)
        return true
    }

    // MARK: Storage

    private suspend fun flush(recording: RecordingMath? = math) {
        val current = recording ?: return
        val points = current.takeTrackPoints()
        store.appendPoints(current.trip.id, points, current.trip.distance, current.trip.maxSpeed, current.trip.averageSpeed)
        unsavedPoints = 0
        lastSave = clock()
    }

    private fun saveSession() {
        val current = _state.value
        val id = current.tripId ?: return session.save(null)
        session.save(RecorderSession(id, current.isPaused, current.pausedSeconds, current.pauseStartedAt))
    }

    companion object {
        const val SAVE_BATCH_SIZE = 10
        const val SAVE_INTERVAL = 15.0
    }
}
