package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.math.floor
import kotlin.math.sqrt

// iOS: Trip.swift / TripOrigin.swift. Rewards use metric storage, never a display setting.
val Trip.scoringKm: Double get() = distance / 1000.0
val TripOrigin.earnsRewards: Boolean get() = this == TripOrigin.recorded
val Trip.rewardKm: Double get() = if (source.earnsRewards) scoringKm else 0.0

// iOS: GamificationModels.swift
val XPBreakdown.total: Int get() = base + newRegionBonus + longTripBonus + firstTripOfDay + regionDiscovery

// iOS: GamificationModels.swift. Levels 1–30 are frozen against already stored profile XP.
object LevelSystem {
    const val maxLevel = 110
    private val baseThresholds = intArrayOf(
        0, 50, 150, 300, 500, 800, 1_200, 1_700, 2_300, 3_000,
        4_000, 5_200, 6_600, 8_200, 10_000, 12_000, 14_500, 17_000, 19_500, 22_000,
        25_000, 28_500, 32_000, 35_500, 39_000, 43_000, 48_000, 53_000, 58_000, 63_000,
    )
    fun xpForLevel(level: Int): Int {
        if (level < 1) return 0
        if (level <= 30) return baseThresholds[level - 1]
        val n = minOf(level, maxLevel) - 30
        return 63_000 + 5_000 * n + 300 * n * (n + 1)
    }
    fun level(xp: Int): Int = (2..maxLevel).lastOrNull { xp >= xpForLevel(it) } ?: 1
    fun xpForNextLevel(level: Int): Int = xpForLevel(if (level < maxLevel) level + 1 else maxLevel)
    fun progressToNextLevel(xp: Int, level: Int): Double {
        if (level >= maxLevel) return 1.0
        val range = xpForNextLevel(level) - xpForLevel(level)
        return if (range > 0) (xp - xpForLevel(level)).toDouble() / range else 1.0
    }
}

// iOS: GamificationModels.swift, DriverRank.from(level:).
fun DriverRank.Companion.from(level: Int): DriverRank = when (level) {
    in 1..4 -> DriverRank.novice
    in 5..9 -> DriverRank.driver
    in 10..14 -> DriverRank.traveler
    in 15..19 -> DriverRank.explorer
    in 20..24 -> DriverRank.navigator
    in 25..29 -> DriverRank.trucker
    in 30..39 -> DriverRank.legend
    in 40..49 -> DriverRank.pioneer
    in 50..59 -> DriverRank.nomad
    in 60..69 -> DriverRank.conqueror
    in 70..79 -> DriverRank.cartographer
    in 80..89 -> DriverRank.odysseus
    in 90..99 -> DriverRank.roadSpirit
    in 100..110 -> DriverRank.eternal
    else -> DriverRank.novice
}

// iOS: GamificationModels.swift, VehicleLevelSystem. Each next step costs level × 100 km.
object VehicleLevelSystem {
    const val stepKm = 100.0
    fun kmForLevel(level: Int): Double = if (level > 1) stepKm / 2 * level.toDouble() * (level - 1) else 0.0
    fun kmForNextLevel(level: Int): Double = kmForLevel(maxOf(1, level) + 1)
    fun level(km: Double): Int {
        if (!(km > 0)) return 1
        var level = maxOf(1, floor((1 + sqrt(1 + 8 * km / stepKm)) / 2).toInt())
        while (kmForLevel(level + 1) <= km) level++
        while (level > 1 && kmForLevel(level) > km) level--
        return level
    }
    fun progressToNext(km: Double, level: Int): Double {
        val current = kmForLevel(level)
        val range = kmForNextLevel(level) - current
        return if (range > 0) ((km - current) / range).coerceIn(0.0, 1.0) else 0.0
    }
    fun kmToNextLevel(km: Double, level: Int): Double = maxOf(0.0, kmForNextLevel(level) - km)
}

// iOS: VehicleOdometer.swift. Transfers never wind a car's odometer; manual trips do.
object VehicleOdometer {
    fun tracked(trips: List<Trip>, vehicleId: UUID): Double =
        trips.sumOf { if (it.vehicleId == vehicleId && !it.isTransfer) it.distance else 0.0 } / 1000
    fun trackedByVehicle(trips: List<Trip>): Map<UUID, Double> {
        val metres = mutableMapOf<UUID, Double>()
        for (trip in trips) {
            val id = trip.vehicleId ?: continue
            if (!trip.isTransfer) metres[id] = (metres[id] ?: 0.0) + trip.distance
        }
        return metres.mapValues { (_, value) -> value / 1000 }
    }
    fun rewardByVehicle(trips: List<Trip>): Map<UUID, Double> {
        val km = mutableMapOf<UUID, Double>()
        for (trip in trips) {
            val id = trip.vehicleId ?: continue
            if (!trip.isTransfer) km[id] = (km[id] ?: 0.0) + trip.rewardKm
        }
        return km
    }
}

// iOS: Vehicle.swift. A server vehicle may not have its local reward snapshot yet.
val Vehicle.levelSourceKm: Double get() = rewardOdometerKm ?: odometerKm
val Vehicle.displayOdometerKm: Double get() = manualOdometerKm ?: odometerKm
val Vehicle.untrackedKm: Double? get() = manualOdometerKm?.let { (it - odometerKm).takeIf { gap -> gap > 0 } }
val Vehicle.progressToNextLevel: Double get() = VehicleLevelSystem.progressToNext(levelSourceKm, level)
val Vehicle.kmToNextLevel: Double get() = VehicleLevelSystem.kmToNextLevel(levelSourceKm, level)

// iOS: GamificationManager.swift. Persistence and sync consume these pure results.
object GamificationManager {
    fun calculateXP(trip: Trip, allTrips: List<Trip>, zone: ZoneId): XPBreakdown {
        if (!trip.source.earnsRewards) return XPBreakdown()
        val base = maxOf(1, trip.rewardKm.toInt())
        val otherTrips = allTrips.filter { it.id != trip.id }
        val newRegion = trip.region?.let { region -> otherTrips.none { it.region == region } } ?: false
        return XPBreakdown(base = base,
            newRegionBonus = if (newRegion) base / 2 else 0,
            longTripBonus = if (trip.rewardKm >= 200) base else 0,
            firstTripOfDay = if (otherTrips.none { it.startDate.atZone(zone).toLocalDate() == trip.startDate.atZone(zone).toLocalDate() }) 20 else 0,
            regionDiscovery = if (newRegion) 50 else 0)
    }

    // Backfill intentionally has no first-trip-of-day bonus, matching the original migration.
    fun backfillXP(trips: List<Trip>): Int {
        val seenRegions = mutableSetOf<String>()
        return trips.filter { it.source.earnsRewards }.sortedBy { it.startDate }.sumOf { trip ->
            val base = maxOf(1, trip.rewardKm.toInt())
            base + (if (trip.rewardKm >= 200) base else 0) +
                (if (trip.region?.let { seenRegions.add(it) } == true) 50 + base / 2 else 0)
        }
    }

    fun newProfileLevel(previousLevel: Int, newXP: Int): Int = maxOf(previousLevel, LevelSystem.level(newXP))
    fun completedVehicleOdometer(previousKm: Double, trip: Trip): Double = previousKm + trip.scoringKm
    fun completedVehicleLevel(newOdometerKm: Double, unrewardedKm: Double): Int =
        VehicleLevelSystem.level(maxOf(0.0, newOdometerKm - unrewardedKm))
}

// iOS: GamificationManager.swift, updateStreak.
data class TripStreak(val current: Int = 0, val best: Int = 0, val lastTripDate: Instant? = null)
fun GamificationManager.updateStreak(previous: TripStreak, tripDate: Instant, zone: ZoneId): TripStreak {
    val last = previous.lastTripDate
    val days = last?.let { java.time.temporal.ChronoUnit.DAYS.between(it.atZone(zone).toLocalDate(), tripDate.atZone(zone).toLocalDate()) }
    if (days != null && days <= 0) return previous
    val current = if (days == 1L) previous.current + 1 else 1
    return TripStreak(current, maxOf(previous.best, current), tripDate)
}

// iOS: TripLevelHistory.swift. Unknown XP history remains unknown instead of inventing level one.
object TripLevelHistory {
    fun levels(trips: List<Trip>): Map<UUID, Int>? {
        val sorted = trips.sortedBy { it.startDate }
        if (sorted.none { it.xpEarned > 0 }) return null
        var running = 0
        return buildMap {
            for (trip in sorted) {
                running += maxOf(0, trip.xpEarned)
                put(trip.id, LevelSystem.level(running))
            }
        }
    }
}
