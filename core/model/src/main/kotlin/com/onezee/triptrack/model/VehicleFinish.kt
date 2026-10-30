// iOS: VehicleFinish.swift
package com.onezee.triptrack.model

// iOS: VehicleFinish.swift
enum class VehicleFinish(val rawValue: String) {
    gloss("gloss"),
    matte("matte"),
    pearl("pearl"),
    chrome("chrome");

    companion object {
        fun fromRawValue(value: String): VehicleFinish? = entries.find { it.rawValue == value }
    }
}
