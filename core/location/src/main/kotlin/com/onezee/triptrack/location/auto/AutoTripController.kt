// iOS: AutoTripService.swift
package com.onezee.triptrack.location.auto

import com.onezee.triptrack.location.recording.RecorderListener
import com.onezee.triptrack.location.recording.RecorderState
import com.onezee.triptrack.location.recording.StartRefusal
import com.onezee.triptrack.location.recording.StartResult
import com.onezee.triptrack.location.recording.TripRecorder
import com.onezee.triptrack.location.recording.secondsBetween
import com.onezee.triptrack.logic.RecordingMovementTracker
import com.onezee.triptrack.logic.autoStopDelaySeconds
import com.onezee.triptrack.logic.bluetoothDisconnectFastStopIdleThreshold
import com.onezee.triptrack.logic.eventDeduplicationWindow
import com.onezee.triptrack.logic.inactivityTimeout
import com.onezee.triptrack.logic.motionSignalStartsTrip
import com.onezee.triptrack.logic.newDrivingSessionGap
import com.onezee.triptrack.logic.onBluetoothDisconnect
import com.onezee.triptrack.logic.staleTripTimeout
import com.onezee.triptrack.logic.stopMode
import com.onezee.triptrack.logic.triggerDeduplicationWindow
import com.onezee.triptrack.model.AutoRecordMode
import com.onezee.triptrack.model.AutoTripPolicy
import com.onezee.triptrack.model.AutoTripPolicy.BluetoothDisconnectDecision
import com.onezee.triptrack.model.TripConfirmation
import com.onezee.triptrack.model.SavedBluetoothDevice
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** iOS: AppStrings.TripStopReason, why the stop question is asked. */
enum class StopPromptReason { bluetooth, inactivity }

/** The notifications of the auto-trip service (iOS: the auto-trip half of NotificationManager). */
interface AutoTripNotices {
    /** `minutes == null`: «Remind» mode asks and does nothing itself, so the text promises no stop. */
    fun tripStopPrompt(minutes: Int?, reason: StopPromptReason)
    fun cancelTripStopPrompt()
    /** The car was detected, the start was refused: silence would let the driver believe it records. */
    fun autoStartFailed(reason: StartRefusal)
    /** «Trip completed: 12.4 km · 23:10», before an automatic stop of a confirmed trip. */
    fun autoStopped(metres: Double, durationSeconds: Double)

    object None : AutoTripNotices {
        override fun tripStopPrompt(minutes: Int?, reason: StopPromptReason) = Unit
        override fun cancelTripStopPrompt() = Unit
        override fun autoStartFailed(reason: StartRefusal) = Unit
        override fun autoStopped(metres: Double, durationSeconds: Double) = Unit
    }
}

/**
 * iOS: AutoTripService.swift. Starts a recording when the phone senses a drive (Activity
 * Recognition, the counterpart of Core Motion), when the chosen car stereo connects (the Bluetooth
 * connection, the counterpart of the audio route) or when Android Auto connects; ends it by the
 * rules of `AutoTripPolicy`.
 *
 * Three rules hold everywhere:
 * - a paused trip is never ended automatically (`autoStopTrip` checks it last of all);
 * - «who started it stops it»: a draft the app started is ended by the full-automatic rules,
 *   whatever the person's mode (`effectiveStopMode`);
 * - «Remind» records a draft at once and asks at the end, instead of waiting for an answer.
 *
 * Event handlers run on whatever thread delivers them; the few fields they share are guarded, and
 * every recording action goes through the recorder's own lock.
 */
class AutoTripController(
    private val recorder: TripRecorder,
    private val settings: AutoRecordSettings,
    private val memory: AutoTripMemory,
    private val notices: AutoTripNotices,
    /** Make this car the next trip's, if it may take trips (archived and sold cars may not). */
    private val selectVehicle: suspend (UUID) -> Unit,
    /** Start the foreground service for a recording that has just begun; false if the system refused. */
    private val ensureRecordingService: () -> Boolean,
    private val scope: CoroutineScope,
    private val clock: () -> Instant = Instant::now,
    /** The auto-stop timer's wait; tests replace it to fire the timer without waiting minutes. */
    private val wait: suspend (millis: Long) -> Unit = { delay(it) },
    // A sleeping process can miss the connect event. Production reads the current outputs,
    // rather than trusting a Bluetooth address left in preferences by an earlier process.
    private val linkedStereo: () -> SavedBluetoothDevice? = {
        memory.connectedStereo?.let { settings.savedDevice(it, null) }
    },
) : RecorderListener {
    private val tracker = RecordingMovementTracker()
    private val lastEvents = mutableMapOf<Pair<Boolean, String>, Instant>()
    internal var stopTimer: Job? = null
        private set
    internal var pendingRestart: Job? = null
        private set

    /** iOS: effectiveStopMode, «who started it stops it». */
    private val effectiveStopMode: AutoRecordMode
        get() = AutoTripPolicy.stopMode(settings.mode, recorder.state.value.isDraft)

    // MARK: Motion

    /** IN_VEHICLE entered. [estimatedStart] is when the drive began, from the transition itself. */
    suspend fun onAutomotiveDetected(estimatedStart: Instant?) {
        onMotionSignal(isAutomotive = true, estimatedStart = estimatedStart)
    }

    /** iOS: significant-location wake and handleBackgroundLaunch, after launch recovery. */
    suspend fun onWake(isAutomotive: Boolean = false) {
        onMotionSignal(isAutomotive, estimatedStart = null)
    }

    private suspend fun onMotionSignal(isAutomotive: Boolean, estimatedStart: Instant?) {
        val stereo = linkedStereo()
        if (settings.mode == AutoRecordMode.off || !AutoTripPolicy.motionSignalStartsTrip(
                settings.trigger, isAutomotive, linkedStereoConnected = stereo != null,
            )) return
        // A wake may discover a different car after a missed connect event; its disconnect
        // must be allowed to end this trip instead of matching the old persisted address.
        memory.connectedStereo = stereo?.uuid
        val now = clock()
        // A long gap since the car last stopped: a new driving session, remind again.
        memory.lastAutomotiveEndTime?.let {
            if (secondsBetween(it, now) > AutoTripPolicy.newDrivingSessionGap) memory.hasRemindedForCurrentTrip = false
        }
        // The car moves again: any pending inactivity stop is void.
        if (recorder.state.value.isRecording) cancelAutoStopTimer()
        handleAutomotiveDetected(estimatedStart, stereo)
    }

    /** IN_VEHICLE left. It flickers at red lights, so it never stops anything; it only dates the session. */
    fun onAutomotiveEnded() {
        memory.lastAutomotiveEndTime = clock()
    }

    private suspend fun handleAutomotiveDetected(estimatedStart: Instant?, stereo: SavedBluetoothDevice?) {
        if (settings.mode == AutoRecordMode.off) return
        if (recorder.state.value.isRecording) {
            // A trip parked past the stale window whose stop never fired: this is a new session,
            // so end the old one first. Only when the stop is the app's to make.
            if (effectiveStopMode != AutoRecordMode.auto || !isStale(AutoTripPolicy.staleTripTimeout)) return
            cancelAutoStopTimer()
            autoStopTrip()
        }
        if (settings.mode == AutoRecordMode.remind && memory.hasRemindedForCurrentTrip) return
        // Bluetooth and motion often fire within milliseconds of each other.
        val now = clock()
        val duplicate = synchronized(this) {
            val last = memory.lastTripTriggerTime
            if (last != null && secondsBetween(last, now) < AutoTripPolicy.triggerDeduplicationWindow) {
                true
            } else {
                memory.lastTripTriggerTime = now
                false
            }
        }
        if (duplicate) return
        stereo?.vehicleId?.let { selectVehicle(it) }
        if (recorder.state.value.isRecording) return
        triggerTripStart(estimatedStart)
    }

    // MARK: Car stereo

    /** A saved stereo connected (ACL or A2DP). Unknown devices are not the car. */
    suspend fun onStereoConnected(address: String?, name: String?) {
        val device = settings.savedDevice(address, name) ?: return
        if (!shouldProcess(connected = true, device.uuid)) return
        memory.connectedStereo = device.uuid
        device.vehicleId?.let { selectVehicle(it) }
        if (recorder.state.value.isRecording) {
            // Back in the car after more than fifteen idle minutes: that was a stop, not a flap.
            if (isStale(AutoTripPolicy.staleTripTimeout)) {
                cancelAutoStopTimer()
                autoStopTrip()
                // A moment for the stop notice to show before the new trip starts.
                pendingRestart = scope.launch {
                    wait(1_000)
                    if (!recorder.state.value.isRecording) triggerTripStart(null)
                }
            } else {
                cancelAutoStopTimer()
            }
            return
        }
        cancelAutoStopTimer()
        triggerTripStart(null)
    }

    /** A saved stereo disconnected: the strongest end-of-session signal there is. */
    suspend fun onStereoDisconnected(address: String?, name: String?) {
        val device = settings.savedDevice(address, name) ?: return
        val tracked = memory.connectedStereo
        if (tracked != null && !tracked.equals(device.uuid, ignoreCase = true)) return
        if (!shouldProcess(connected = false, device.uuid)) return
        memory.connectedStereo = null
        disconnected()
    }

    // MARK: Android Auto

    /** iOS: AudioRouteDetector.swift, .carAudio. Only a linked output is our stereo. */
    suspend fun onCarConnection(connected: Boolean) {
        if (connected) {
            onStereoConnected(ANDROID_AUTO, null)
        } else {
            onStereoDisconnected(ANDROID_AUTO, null)
        }
    }

    private suspend fun disconnected() {
        memory.hasRemindedForCurrentTrip = false
        val state = recorder.state.value
        val now = clock()
        val decision = AutoTripPolicy.onBluetoothDisconnect(
            mode = effectiveStopMode,
            isRecording = state.isRecording,
            isPaused = state.isPaused,
            isIdleBeyondFastStop = isStale(AutoTripPolicy.bluetoothDisconnectFastStopIdleThreshold),
            tripDistance = state.distance,
            tripDuration = state.startDate?.let { secondsBetween(it, now) } ?: 0.0,
            autoStopTimeout = settings.autoStopTimeout,
        )
        when (decision) {
            BluetoothDisconnectDecision.Ignore -> Unit
            BluetoothDisconnectDecision.StopNow -> autoStopTrip()
            BluetoothDisconnectDecision.PromptOnly -> notices.tripStopPrompt(null, StopPromptReason.bluetooth)
            is BluetoothDisconnectDecision.PromptThenStop -> {
                notices.tripStopPrompt(decision.afterMinutes, StopPromptReason.bluetooth)
                startAutoStopTimer(decision.afterMinutes)
            }
        }
    }

    // MARK: Recording hooks

    /** iOS: updateMovementForInactivity, on every accepted fix. */
    override fun onAcceptedFix(state: RecorderState) {
        if (!state.isRecording || state.isPaused) {
            // A paused trip is never stopped: the window starts fresh on resume.
            synchronized(tracker) { tracker.reset() }
            return
        }
        val now = clock()
        val prompt = synchronized(tracker) {
            if (tracker.record(state.distance, now)) return
            if (effectiveStopMode != AutoRecordMode.auto || !tracker.isStale(AutoTripPolicy.inactivityTimeout, now)) return
            // Re-armed, so the question is not repeated on every fix of the same stop.
            tracker.extendWindow(now)
            true
        }
        if (prompt) {
            val timeout = settings.autoStopTimeout
            notices.tripStopPrompt(timeout, StopPromptReason.inactivity)
            startAutoStopTimer(timeout)
        }
    }

    /** Nothing from the previous trip may reach this one. */
    override fun recordingDidStart(tripId: UUID) {
        synchronized(tracker) { tracker.reset() }
        cancelAutoStopTimer()
    }

    override fun recordingDidEnd() {
        synchronized(tracker) { tracker.reset() }
        cancelAutoStopTimer()
    }

    /** iOS: handleManualPause. A timer armed before the pause must not end the trip during it. */
    override fun manualPause() {
        cancelAutoStopTimer()
    }

    // MARK: Foreground and notification actions

    /** iOS: recoverStaleTripIfNeeded, on every return of the app to the screen. */
    suspend fun onAppForeground() {
        if (effectiveStopMode != AutoRecordMode.auto) return
        val state = recorder.state.value
        if (!state.isRecording || state.isPaused) return
        if (!isStale(AutoTripPolicy.staleTripTimeout)) return
        autoStopTrip()
    }

    /** «Stop now» on the stop question: the person asks, so a pause does not protect the trip. */
    suspend fun stopRequestedByUser() {
        cancelAutoStopTimer()
        autoStopTrip(userAction = true)
    }

    /** «Continue»: keep going, and do not ask again in a second. */
    fun continueRequestedByUser() {
        cancelAutoStopTimer()
        synchronized(tracker) { tracker.extendWindow(clock()) }
    }

    // MARK: Start and stop

    private suspend fun triggerTripStart(estimatedStart: Instant?) {
        if (recorder.state.value.isRecording) return
        val confirmation = when (settings.mode) {
            AutoRecordMode.off -> return
            AutoRecordMode.auto -> TripConfirmation.confirmed
            // Since 0.8.1 «Remind» records at once: the trip is a draft until «Mine».
            AutoRecordMode.remind -> TripConfirmation.draft
        }
        when (val result = recorder.start(confirmation = confirmation)) {
            is StartResult.Refused -> {
                // Not «already reminded»: the next detection must try again.
                if (result.reason != StartRefusal.alreadyRecording) notices.autoStartFailed(result.reason)
                return
            }
            is StartResult.Started -> Unit
        }
        if (!ensureRecordingService()) {
            // No service, no GPS: a recording that would write nothing. Say so instead.
            recorder.stop()
            notices.autoStartFailed(StartRefusal.unknown)
            return
        }
        if (confirmation == TripConfirmation.draft) memory.hasRemindedForCurrentTrip = true
        estimatedStart?.let { start -> if (start < clock()) recorder.backdate(start) }
    }

    /**
     * iOS: autoStopTrip. The last line of defence: whatever armed the timer and whenever it fires,
     * it does not end a paused trip. «Stop now» from the notification is the person and is obeyed.
     */
    private suspend fun autoStopTrip(userAction: Boolean = false) {
        val state = recorder.state.value
        if (!state.isRecording) return
        if (!userAction && state.isPaused) return
        val lastChange = synchronized(tracker) { tracker.lastChangeTime }
        // A draft gets its own question at the finish instead.
        if (!state.isDraft) notices.autoStopped(state.distance, state.startDate?.let { secondsBetween(it, clock()) } ?: 0.0)
        // The moment the trip stopped moving: a stop fired by the stale window is minutes late.
        recorder.stop(suggestedEndDate = lastChange)
        memory.hasRemindedForCurrentTrip = false
    }

    private fun startAutoStopTimer(minutes: Int) {
        synchronized(this) {
            stopTimer?.cancel()
            stopTimer = scope.launch {
                wait((AutoTripPolicy.autoStopDelaySeconds(minutes) * 1000).toLong())
                // Fired: the stop below ends the recording, and the end cancels the timer; it must
                // not cancel this very coroutine halfway through the finish.
                val self = coroutineContext[Job]
                synchronized(this@AutoTripController) {
                    if (stopTimer === self) stopTimer = null
                }
                autoStopTrip()
            }
        }
    }

    private fun cancelAutoStopTimer() {
        synchronized(this) {
            stopTimer?.cancel()
            stopTimer = null
        }
        notices.cancelTripStopPrompt()
    }

    private fun isStale(threshold: Double): Boolean = synchronized(tracker) { tracker.isStale(threshold, clock()) }

    /** iOS: TimeWindowDebouncer. One connect and one disconnect per device every five seconds. */
    private fun shouldProcess(connected: Boolean, key: String): Boolean = synchronized(lastEvents) {
        val now = clock()
        val eventKey = connected to key.uppercase()
        val last = lastEvents[eventKey]
        if (last != null && secondsBetween(last, now) < AutoTripPolicy.eventDeduplicationWindow) return false
        lastEvents[eventKey] = now
        true
    }

    companion object {
        const val ANDROID_AUTO = "android-auto"
    }
}
