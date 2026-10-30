// iOS: TripConfirmation.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.onezee.triptrack.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// iOS: TripConfirmation.swift
@Serializable
enum class TripConfirmation(val rawValue: String) {
    @SerialName("confirmed")
    confirmed("confirmed"),
    @SerialName("draft")
    draft("draft");

    companion object {
        fun fromRawValue(value: String): TripConfirmation? = entries.find { it.rawValue == value }
    }
}

// iOS: TripConfirmation.swift
@Serializable
enum class RoadFillState(val rawValue: String) {
    @SerialName("unchecked")
    unchecked("unchecked"),
    @SerialName("pending")
    pending("pending"),
    @SerialName("done")
    done("done");

    companion object {
        fun fromRawValue(value: String): RoadFillState? = entries.find { it.rawValue == value }
    }
}
