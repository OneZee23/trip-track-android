// iOS: ElectricUnit.swift
package com.onezee.triptrack.model

// iOS: ElectricUnit.swift
enum class ElectricUnit(val rawValue: String) {
    per100kWh("per100kWh"),
    miPerKWh("miPerKWh");

    companion object {
        fun fromRawValue(value: String): ElectricUnit? = entries.find { it.rawValue == value }
    }
}
