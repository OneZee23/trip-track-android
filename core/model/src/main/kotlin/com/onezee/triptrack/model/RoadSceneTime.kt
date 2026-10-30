// iOS: RoadSceneTime.swift
package com.onezee.triptrack.model

// iOS: RoadSceneTime.swift
enum class RoadSceneTime(val rawValue: String) {
    dawn("dawn"),
    day("day"),
    sunset("sunset"),
    night("night");

    companion object {
        fun fromRawValue(value: String): RoadSceneTime? = entries.find { it.rawValue == value }
    }
}
