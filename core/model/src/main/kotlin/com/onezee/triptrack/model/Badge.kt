// iOS: Badge.swift
package com.onezee.triptrack.model

// iOS: Badge.swift
enum class BadgeCategory(val rawValue: String) {
    distance("distance"),
    exploration("exploration"),
    special("special"),
    streaks("streaks"),
    exclusive("exclusive");

    companion object {
        fun fromRawValue(value: String): BadgeCategory? = entries.find { it.rawValue == value }
    }
}

// iOS: Badge.swift
enum class BadgeRarity(val rawValue: Int) {
    common(0),
    uncommon(1),
    rare(2),
    epic(3),
    legendary(4),
    exclusive(5);

    companion object {
        fun fromRawValue(value: Int): BadgeRarity? = entries.find { it.rawValue == value }
    }
}

// iOS: Badge.swift
data class BadgeStats(
    val totalTrips: Int,
    val totalDistanceKm: Double,
    val maxSpeedKmh: Double,
    val totalDurationHours: Double,
    val uniqueRegions: Int,
    val longestTripKm: Double,
    val hasNightTrip: Boolean,
    val hasEarlyMorningTrip: Boolean,
    val hasLateNightTrip: Boolean,
    val maxAltitude: Double,
    val maxLatitude: Double,
    val maxElevationGainSingleTrip: Double,
    val currentStreak: Int,
    val bestStreak: Int,
    val hasSingleTripMarathon: Boolean,
    val hasSingleTripIronButt: Boolean,
    val longestSingleTripDuration: Double,
    val hasSundayTrip: Boolean,
    val hasWeekendWarrior: Boolean,
    val hasWinterMountainTrip: Boolean,
    val hasSeaLevelTrip: Boolean,
    val countriesCount: Int,
    val hasAfterMidnightTrip: Boolean,
    val distinctMonthsWithTrips: Int,
    val maxTripsOnSingleVehicle: Int,
    val privateTripCount: Int,
)

// iOS: Badge.swift
data class Badge(
    val id: String,
    val titleRu: String,
    val titleEn: String,
    val descriptionRu: String,
    val descriptionEn: String,
    val icon: String,
    val category: BadgeCategory = BadgeCategory.distance,
    val isHidden: Boolean = false,
    val isRepeatable: Boolean = false,
    val rarity: BadgeRarity = BadgeRarity.common,
    var globalUnlockPercent: Double? = null,
    val figures: List<BadgeFigure> = emptyList(),
) {

    // iOS: Badge.swift
    enum class RecordMetric {
        tripDistance,
        tripMaxSpeed,
        tripDuration,
        tripElevationGain,
        tripMaxAltitude,
    }
}
