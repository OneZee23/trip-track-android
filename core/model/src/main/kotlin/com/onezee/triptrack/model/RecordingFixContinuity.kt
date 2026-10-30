// iOS: RecordingFixContinuity.swift
package com.onezee.triptrack.model

// iOS: RecordingFixContinuity.swift
object RecordingFixContinuity {

    // iOS: RecordingFixContinuity.swift
    enum class Rejection(val rawValue: String) {
        invalid("invalid"),
        timestamp("timestamp"),
        speed("speed"),
        displacement("displacement");

        companion object {
            fun fromRawValue(value: String): Rejection? = entries.find { it.rawValue == value }
        }
    }
}
