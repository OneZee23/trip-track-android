// iOS: Powertrain.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.onezee.triptrack.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// iOS: Powertrain.swift
@Serializable
enum class Powertrain(val rawValue: String) {
    @SerialName("fuel")
    fuel("fuel"),
    @SerialName("electric")
    electric("electric"),
    @SerialName("pluginHybrid")
    pluginHybrid("pluginHybrid");

    companion object {
        fun fromRawValue(value: String): Powertrain? = entries.find { it.rawValue == value }
        fun parse(value: String?): Powertrain? = value?.let(::fromRawValue)
    }
}
