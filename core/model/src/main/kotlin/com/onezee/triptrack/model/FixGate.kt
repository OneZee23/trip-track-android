// iOS: FixGate.swift
package com.onezee.triptrack.model

// iOS: FixGate.swift
object FixGate {

    // iOS: FixGate.swift
    enum class Reason(val rawValue: String) {
        invalid("invalid"),
        accuracy("accuracy"),
        stale("stale"),
        speed("speed");

        companion object {
            fun fromRawValue(value: String): Reason? = entries.find { it.rawValue == value }
        }
    }

    // iOS: FixGate.swift
    sealed interface Decision {
        data object Accept : Decision
        data class Reject(val value: Reason) : Decision
    }
}
