// iOS: PlacesPresentation.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.onezee.triptrack.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// iOS: PlacesPresentation.swift
@Serializable
enum class PlacesSort(val rawValue: String) {
    @SerialName("recent")
    recent("recent"),
    @SerialName("frequent")
    frequent("frequent"),
    @SerialName("name")
    byName("name");

    companion object {
        fun fromRawValue(value: String): PlacesSort? = entries.find { it.rawValue == value }
    }
}

// iOS: PlacesPresentation.swift
object PlacesPresentation {

    // iOS: PlacesPresentation.swift
    data class Group(
        val kind: Kind,
        val items: List<PlaceListItem>,
    ) {

        // iOS: PlacesPresentation.swift
        enum class Kind(val rawValue: String) {
            all("all"),
            frequent("frequent"),
            others("others"),
            today("today"),
            thisWeek("thisWeek"),
            earlier("earlier");

            companion object {
                fun fromRawValue(value: String): Kind? = entries.find { it.rawValue == value }
            }
        }
    }
}
