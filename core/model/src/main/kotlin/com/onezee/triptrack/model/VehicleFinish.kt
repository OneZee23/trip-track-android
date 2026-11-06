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
        val availableFinishes = listOf(gloss, matte)
        fun effective(raw: String?, isPlus: Boolean) = (entries.firstOrNull { it.rawValue == raw } ?: gloss).let { if (it.isPlus && !isPlus) gloss else it }
    }
    val isPlus: Boolean get() = this != gloss
}
