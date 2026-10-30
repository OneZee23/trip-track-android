// iOS: JourneyAggregate.swift
package com.onezee.triptrack.model

import java.time.Instant

// iOS: JourneyAggregate.swift
data class JourneyAggregate(
    val days: List<Day>,
    val legCount: Int,
    val totalMetres: Double,
    val drivingSeconds: Double,
    val calendarDays: Int,
    val regions: List<String>,
    val firstStart: GeoPoint? = null,
    val farthestEnd: GeoPoint? = null,
) {

    // iOS: JourneyAggregate.swift
    sealed interface Item {
        data class Leg(val value: Trip) : Item
        data class Local(val value1: List<Trip>, val anchor: GeoPoint, val lastDayNumber: Int) : Item
    }

    // iOS: JourneyAggregate.swift
    data class Day(
        val number: Int,
        val date: Instant,
        var items: List<Item>,
    )
}
