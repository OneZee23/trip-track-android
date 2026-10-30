// iOS: TripOrigin.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.TripOriginSerializer
import kotlinx.serialization.Serializable

// iOS: TripOrigin.swift
@Serializable(with = TripOriginSerializer::class)
enum class TripOrigin(val rawValue: String) {
    recorded("recorded"),
    manual("manual");

    companion object {
        fun fromRawValue(value: String): TripOrigin? = entries.find { it.rawValue == value }
    }
}
