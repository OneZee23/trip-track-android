// iOS: VehicleType.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.onezee.triptrack.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// iOS: VehicleType.swift
@Serializable
enum class VehicleType(val rawValue: String) {
    @SerialName("car")
    car("car"),
    @SerialName("moto")
    moto("moto"),
    @SerialName("moped")
    moped("moped"),
    @SerialName("bicycle")
    bicycle("bicycle");

    companion object {
        fun fromRawValue(value: String): VehicleType? = entries.find { it.rawValue == value }
    }
}
