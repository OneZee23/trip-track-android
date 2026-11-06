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
        fun effective(id: String?, isPlus: Boolean) = (fromRawValue(id.orEmpty()) ?: none).effective(isPlus)
    }

    val isPlus: Boolean get() = this != none
    val displayName: String get() = when (this) {
        none -> ""; carbon -> "Carbon"; gold -> "Gold"; neon -> "Neon"; racing -> "Racing"; chrome -> "Chrome"; matte -> "Matte"; camo -> "Camo"; sunset -> "Sunset"
    }
    fun effective(isPlus: Boolean) = if (this.isPlus && !isPlus) none else this
}
