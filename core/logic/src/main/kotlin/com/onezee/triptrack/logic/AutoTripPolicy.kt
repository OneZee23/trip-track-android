package com.onezee.triptrack.logic

import com.onezee.triptrack.model.AutoRecordMode
import com.onezee.triptrack.model.AutoRecordTrigger
import com.onezee.triptrack.model.AutoTripPolicy
import com.onezee.triptrack.model.AutoTripPolicy.BluetoothDisconnectDecision

// iOS: AutoTripPolicy.swift. Time intervals are seconds, distances are metres.
val AutoTripPolicy.eventDeduplicationWindow: Double get() = 5.0
val AutoTripPolicy.triggerDeduplicationWindow: Double get() = 30.0
val AutoTripPolicy.newDrivingSessionGap: Double get() = 10.0 * 60
val AutoTripPolicy.inactivityTimeout: Double get() = 10.0 * 60
val AutoTripPolicy.staleTripTimeout: Double get() = 15.0 * 60
val AutoTripPolicy.bluetoothDisconnectFastStopIdleThreshold: Double get() = 5.0 * 60
val AutoTripPolicy.meaningfulMovementDistance: Double get() = 100.0
val AutoTripPolicy.immediateEndOnBtDisconnectMinDistance: Double get() = 500.0
val AutoTripPolicy.immediateEndOnBtDisconnectMinDuration: Double get() = 180.0
/**
 * Speed (m/s, ~14 km/h) above which a lost stereo is ignored: the car is clearly driving. Compared
 * with the live speedometer, which reads zero within about two seconds of a real stop, so parking
 * and switching the engine off still end the trip.
 */
val AutoTripPolicy.bluetoothDisconnectDrivingSpeed: Double get() = 4.0
val AutoTripPolicy.junkTripMinDistance: Double get() = 500.0
val AutoTripPolicy.junkTripMinDuration: Double get() = 120.0
val AutoTripPolicy.junkTripWalkingSpeedKmh: Double get() = 15.0
val AutoTripPolicy.junkTripWalkingMinDuration: Double get() = 180.0
val AutoTripPolicy.backgroundLaunchTaskTimeout: Double get() = 25.0
val AutoTripPolicy.audioRouteSettleDelay: Double get() = 1.0

// iOS: AutoTripPolicy.swift. Motion also fires in taxis; only a linked stereo proves this is our car.
fun AutoTripPolicy.motionSignalStartsTrip(
    trigger: AutoRecordTrigger,
    isAutomotive: Boolean,
    linkedStereoConnected: Boolean,
): Boolean = when (trigger) {
    AutoRecordTrigger.motionOrStereo -> isAutomotive
    AutoRecordTrigger.stereoOnly -> linkedStereoConnected
}

fun AutoTripPolicy.onBluetoothDisconnect(
    mode: AutoRecordMode,
    isRecording: Boolean,
    isPaused: Boolean,
    isDriving: Boolean,
    isIdleBeyondFastStop: Boolean,
    tripDistance: Double,
    tripDuration: Double,
    autoStopTimeout: Int,
): BluetoothDisconnectDecision {
    if (!isRecording || isPaused) return BluetoothDisconnectDecision.Ignore
    // A stereo lost on the move is not an arrival: the car drives on and the Bluetooth link dropped.
    // Neither end the trip nor ask the driver at the wheel; parking ends it through inactivity.
    if (isDriving) return BluetoothDisconnectDecision.Ignore
    return when (mode) {
        AutoRecordMode.off -> BluetoothDisconnectDecision.Ignore
        AutoRecordMode.remind -> BluetoothDisconnectDecision.PromptOnly
        AutoRecordMode.auto -> {
            if (isIdleBeyondFastStop || (tripDistance >= immediateEndOnBtDisconnectMinDistance &&
                tripDuration >= immediateEndOnBtDisconnectMinDuration)) BluetoothDisconnectDecision.StopNow
            else BluetoothDisconnectDecision.PromptThenStop(afterMinutes = autoStopTimeout)
        }
    }
}

fun AutoTripPolicy.stopMode(settings: AutoRecordMode, tripIsDraft: Boolean): AutoRecordMode =
    if (tripIsDraft) AutoRecordMode.auto else settings

// iOS: AutoTripService.swift, updateMovementForInactivity/recoverStaleTripIfNeeded/autoStopTrip.
fun AutoTripPolicy.shouldPromptForInactivity(
    mode: AutoRecordMode, tripIsDraft: Boolean, isRecording: Boolean, isPaused: Boolean, idleSeconds: Double,
): Boolean = isRecording && !isPaused && stopMode(mode, tripIsDraft) == AutoRecordMode.auto && idleSeconds > inactivityTimeout

fun AutoTripPolicy.shouldRecoverStaleTrip(
    mode: AutoRecordMode, tripIsDraft: Boolean, isRecording: Boolean, isPaused: Boolean, idleSeconds: Double,
): Boolean = isRecording && !isPaused && stopMode(mode, tripIsDraft) == AutoRecordMode.auto && idleSeconds > staleTripTimeout

fun AutoTripPolicy.canStop(isRecording: Boolean, isPaused: Boolean, userAction: Boolean = false): Boolean =
    isRecording && (userAction || !isPaused)

fun AutoTripPolicy.autoStopDelaySeconds(minutes: Int): Double = minutes.toDouble() * 60
