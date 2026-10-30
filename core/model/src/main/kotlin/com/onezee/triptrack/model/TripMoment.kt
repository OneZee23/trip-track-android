// iOS: TripMoment.swift
package com.onezee.triptrack.model

// iOS: TripMoment.swift
sealed interface TripMoment {
    data class Checkpoint(val value1: TripCheckpoint, val number: Int, val photos: List<TripPhoto>) : TripMoment
    data class Photos(val fix: TripRouteLocator.Fix, val photos: List<TripPhoto>) : TripMoment
}

// iOS: TripMoment.swift
object TripMoments {

    // iOS: TripMoment.swift
    data class PlacedPhoto(
        val photo: TripPhoto,
        val fix: TripRouteLocator.Fix,
    )
}
