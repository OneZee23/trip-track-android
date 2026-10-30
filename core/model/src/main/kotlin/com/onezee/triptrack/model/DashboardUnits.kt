// iOS: DashboardUnits.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.onezee.triptrack.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// iOS: DashboardUnits.swift
@Serializable
enum class DashboardUnits(val rawValue: String) {
    @SerialName("app")
    app("app"),
    @SerialName("metric")
    metric("metric"),
    @SerialName("imperial")
    imperial("imperial");

    companion object {
        fun fromRawValue(value: String): DashboardUnits? = entries.find { it.rawValue == value }
        fun parse(value: String?): DashboardUnits? = value?.let(::fromRawValue)
    }
}
