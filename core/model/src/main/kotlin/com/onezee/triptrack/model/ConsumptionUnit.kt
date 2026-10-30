// iOS: ConsumptionUnit.swift
package com.onezee.triptrack.model

// iOS: ConsumptionUnit.swift
enum class ConsumptionUnit(val rawValue: String) {
    per100("per100"),
    mpg("mpg");

    companion object {
        const val mpgConstant = 235.214583
        const val litresPerGallon = 3.785411784

        fun fromRawValue(value: String): ConsumptionUnit? = entries.find { it.rawValue == value }
    }
}
