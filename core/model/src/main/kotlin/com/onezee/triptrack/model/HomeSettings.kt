// iOS: HomeSettings.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.RadiusSerializer
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable

// iOS: HomeSettings.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class HomeSettings(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var latitude: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var longitude: Double? = null,
    @Required
    var radius: Radius = Radius.block,
    @Required
    var showsOnMap: Boolean = true,
    @Required
    var trimsPublicTracks: Boolean = false,
) {

    // iOS: HomeSettings.swift
    @Serializable(with = RadiusSerializer::class)
    enum class Radius(val rawValue: Int) {
        yard(200),
        block(500),
        district(1000);

        companion object {
            fun fromRawValue(value: Int): Radius? = entries.find { it.rawValue == value }
        }
    }
}
