// iOS: VehicleCardStyle.swift
package com.onezee.triptrack.model

enum class VehicleCardStyle(val rawValue: String) {
    none(""),
    carbon("card_carbon"),
    gold("card_gold"),
    neon("card_neon"),
    racing("card_racing"),
    chrome("card_chrome"),
    matte("card_matte"),
    camo("card_camo"),
    sunset("card_sunset");

    companion object {
        fun fromRawValue(value: String): VehicleCardStyle? = entries.find { it.rawValue == value }
    }
}
