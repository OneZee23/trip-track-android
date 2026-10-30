// iOS: DistanceUnit.swift
package com.onezee.triptrack.model

// iOS: DistanceUnit.swift
enum class DistanceUnit(val rawValue: String) {
    km("km"),
    miles("miles");

    companion object {
        const val metresPerMile = 1609.344
        const val metresPerFoot = 0.3048
        const val storageKey = "distanceUnit"

        fun fromRawValue(value: String): DistanceUnit? = entries.find { it.rawValue == value }
    }
}
