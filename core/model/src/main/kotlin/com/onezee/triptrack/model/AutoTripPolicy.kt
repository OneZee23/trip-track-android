// iOS: AutoTripPolicy.swift
package com.onezee.triptrack.model

// iOS: AutoTripPolicy.swift
object AutoTripPolicy {

    // iOS: AutoTripPolicy.swift
    sealed interface BluetoothDisconnectDecision {
        data object Ignore : BluetoothDisconnectDecision
        data object StopNow : BluetoothDisconnectDecision
        data object PromptOnly : BluetoothDisconnectDecision
        data class PromptThenStop(val afterMinutes: Int) : BluetoothDisconnectDecision
    }
}
