// iOS: TripPhotoPlacement.swift
package com.onezee.triptrack.model

import java.util.UUID

// iOS: TripPhotoPlacement.swift
object TripPhotoPlacement {

    // iOS: TripPhotoPlacement.swift
    data class Prepared(
        val links: Map<UUID, List<TripPhoto>>,
        val placed: List<Placed>,
        val fixes: Map<UUID, TripRouteLocator.Fix>,
    )

    // iOS: TripPhotoPlacement.swift
    data class Placed(
        val id: UUID,
        val filename: String,
        val latitude: Double,
        val longitude: Double,
        val source: Source,
    ) {

        // iOS: TripPhotoPlacement.swift
        enum class Source(val rawValue: String) {
            photo("photo"),
            track("track");

            companion object {
                fun fromRawValue(value: String): Source? = entries.find { it.rawValue == value }
            }
        }
    }
}
