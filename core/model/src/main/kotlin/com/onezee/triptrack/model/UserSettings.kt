// iOS: UserSettings.swift
package com.onezee.triptrack.model

import java.util.UUID

// iOS: UserSettings.swift
data class UserSettings(
    var avatarEmoji: String,
    var themeMode: String,
    var language: String,
    var fuelConsumption: Double,
    var fuelPrice: Double,
    var selectedVehicleId: UUID? = null,
)
