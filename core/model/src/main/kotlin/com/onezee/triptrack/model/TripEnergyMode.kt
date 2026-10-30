// iOS: TripEnergyMode.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.TripEnergyModeSerializer
import kotlinx.serialization.Serializable

// iOS: TripEnergyMode.swift
@Serializable(with = TripEnergyModeSerializer::class)
enum class TripEnergyMode(val rawValue: String) {
    auto("auto"),
    electric("electric"),
    fuel("fuel");

    // Swift stores auto as an absent database value, while JSON still carries "auto".
    val stored: String? get() = if (this == auto) null else rawValue

    companion object {
        fun fromRawValue(value: String): TripEnergyMode? = entries.find { it.rawValue == value }
        fun parse(value: String?): TripEnergyMode = value?.let(::fromRawValue) ?: auto
    }
}
