// iOS: ProfileBackground.swift
package com.onezee.triptrack.model

enum class ProfileBackground(val rawValue: String) {
    none(""),
    sunset("sunset"),
    ocean("ocean"),
    forest("forest"),
    mountain("mountain"),
    midnight("midnight"),
    dawn("dawn"),
    copper("copper"),
    slate("slate"),
    aurora("aurora"),
    sand("sand"),
    plusNebula("plus_nebula"),
    plusLava("plus_lava"),
    plusGlacier("plus_glacier"),
    plusNeon("plus_neon"),
    plusCarbon("plus_carbon"),
    plusGold("plus_gold"),
    plusTropic("plus_tropic"),
    plusStorm("plus_storm");

    companion object {
        fun fromRawValue(value: String): ProfileBackground? = entries.find { it.rawValue == value }
    }
}
