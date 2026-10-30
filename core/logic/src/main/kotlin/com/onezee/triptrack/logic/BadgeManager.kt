package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.UUID
import kotlin.math.abs

// iOS: BadgeManager.swift, unlockedBadgeIds / badgeEarnCounts. The storage adapter owns persistence.
data class BadgeLedger(val unlockedIds: Set<String> = emptySet(), val earnCounts: Map<String, Int> = emptyMap())

// iOS: BadgeManager.swift, BadgeEvalResult. The next ledger is written together by the caller.
data class BadgeEvalResult(val allEarned: List<BadgeRule>, val repeatedBadgeCounts: Map<String, Int>, val ledger: BadgeLedger)

// iOS: BadgeManager.swift
object BadgeManager {
    const val unlockedKey = "unlockedBadgeIds"
    const val earnCountsKey = "badgeEarnCounts"

    private fun hoursTouched(trip: Trip, zone: ZoneId): Set<Int> {
        val start = trip.startDate
        val end = trip.endDate ?: start
        if (end <= start) return setOf(start.atZone(zone).hour)
        if (end.secondsSince(start) >= 24 * 3600) return (0..23).toSet()
        return buildSet {
            var t = start
            while (t < end) {
                add(t.atZone(zone).hour)
                t = t.plusSeconds(300)
            }
            add(end.atZone(zone).hour)
        }
    }

    private fun windowOverlaps(hours: Set<Int>, start: Int, end: Int): Boolean =
        hours.any { if (start <= end) it >= start && it < end else it >= start || it < end }

    fun computeStats(trips: List<Trip>, now: Instant, zone: ZoneId, weekFields: WeekFields,
                     currentStreak: Int = 0, bestStreak: Int = 0): BadgeStats {
        var totalDist = 0.0
        var maxSpeed = 0.0
        var totalDuration = 0.0
        var longestTrip = 0.0
        val regions = mutableSetOf<String>()
        var hasNight = false
        var hasLateNight = false
        var hasEarlyMorning = false
        var maxAltitude = 0.0
        var maxLatitude = 0.0
        var maxElevationGain = 0.0
        var hasSingleMarathon = false
        var hasSingleIronButt = false
        var longestTripDuration = 0.0
        var hasSunday = false
        var hasSeaLevel = false
        var hasWinterMountain = false
        var hasAfterMidnight = false
        val monthsThisYear = mutableSetOf<Int>()
        val vehicleCounts = mutableMapOf<UUID, Int>()
        var privateCount = 0
        val currentYear = now.atZone(zone).year
        for (trip in trips) {
            // Hand-entered distances/speeds must not become persisted achievements.
            if (!trip.source.earnsRewards) continue
            totalDist += trip.rewardKm
            maxSpeed = maxOf(maxSpeed, trip.maxSpeed * 3.6)
            val duration = (trip.endDate ?: now).secondsSince(trip.startDate)
            totalDuration += duration
            longestTrip = maxOf(longestTrip, trip.rewardKm)
            longestTripDuration = maxOf(longestTripDuration, duration)
            trip.region?.let { regions.add(it) }
            if (trip.rewardKm >= 42.195) hasSingleMarathon = true
            if (trip.rewardKm >= 500) hasSingleIronButt = true
            val hours = if (!hasNight || !hasLateNight || !hasEarlyMorning || !hasAfterMidnight) hoursTouched(trip, zone) else emptySet()
            if (!hasNight && windowOverlaps(hours, 22, 5)) hasNight = true
            if (!hasLateNight && windowOverlaps(hours, 23, 4)) hasLateNight = true
            if (!hasEarlyMorning && windowOverlaps(hours, 0, 6)) hasEarlyMorning = true
            val date = trip.startDate.atZone(zone)
            if (date.dayOfWeek == DayOfWeek.SUNDAY) hasSunday = true
            if (trip.elevation >= 1000) maxElevationGain = maxOf(maxElevationGain, trip.elevation)
            if (date.monthValue in setOf(12, 1, 2) && trip.elevation >= 200) hasWinterMountain = true
            if (!hasAfterMidnight && windowOverlaps(hours, 0, 4)) hasAfterMidnight = true
            if (date.year == currentYear) monthsThisYear.add(date.monthValue)
            trip.vehicleId?.let { vehicleCounts[it] = (vehicleCounts[it] ?: 0) + 1 }
            if (trip.isPrivate) privateCount++
            val measured = trip.measuredPoints
            for (point in measured) {
                maxAltitude = maxOf(maxAltitude, point.altitude)
                maxLatitude = maxOf(maxLatitude, abs(point.latitude))
            }
            if (trip.rewardKm >= 20 && measured.isNotEmpty()) {
                val low = measured.count { it.altitude < 10 && it.altitude >= 0 }
                if (low.toDouble() / measured.size > 0.8) hasSeaLevel = true
            }
        }
        // iOS counts the supplied list for trip/weekend milestones, including manual entries.
        return BadgeStats(trips.size, totalDist, maxSpeed, totalDuration / 3600, regions.size, longestTrip,
            hasNight, hasEarlyMorning, hasLateNight, maxAltitude, maxLatitude, maxElevationGain,
            currentStreak, bestStreak, hasSingleMarathon, hasSingleIronButt, longestTripDuration,
            hasSunday, checkWeekendWarrior(trips, zone, weekFields), hasWinterMountain, hasSeaLevel, 0,
            hasAfterMidnight, monthsThisYear.size, vehicleCounts.values.maxOrNull() ?: 0, privateCount)
    }

    private fun checkWeekendWarrior(trips: List<Trip>, zone: ZoneId, weekFields: WeekFields): Boolean {
        val weeks = trips.map { it.startDate.atZone(zone).toLocalDate() }
            .filter { it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY }
            .map { it.minusDays(it.get(weekFields.dayOfWeek()).toLong() - 1) }.distinct().sorted()
        if (weeks.size < 4) return false
        var consecutive = 1
        for ((previous, next) in weeks.zipWithNext()) {
            val days = ChronoUnit.DAYS.between(previous, next)
            if (days == 7L) {
                consecutive++
                if (consecutive >= 4) return true
            } else if (days > 7) consecutive = 1
        }
        return false
    }

    fun unlockedBadges(stats: BadgeStats, storedUnlockedIds: Set<String>): List<BadgeRule> {
        val external = storedUnlockedIds.intersect(BadgeDefinitions.externallyUnlockedIds)
        return BadgeDefinitions.all.filter { it.checkUnlocked(stats) || it.id in external }
    }

    fun unlock(id: String, ledger: BadgeLedger): BadgeEvalResult {
        val badge = BadgeDefinitions.all.firstOrNull { it.id == id }
        if (badge == null || id in ledger.unlockedIds) return BadgeEvalResult(emptyList(), emptyMap(), ledger)
        return BadgeEvalResult(listOf(badge), emptyMap(), ledger.copy(unlockedIds = ledger.unlockedIds + id))
    }

    fun checkNewBadges(stats: BadgeStats, ledger: BadgeLedger): BadgeEvalResult {
        val unlocked = unlockedBadges(stats, ledger.unlockedIds)
        return BadgeEvalResult(unlocked.filter { it.id !in ledger.unlockedIds }, emptyMap(),
            ledger.copy(unlockedIds = unlocked.map { it.id }.toSet()))
    }

    fun evaluateBadgesForTrip(trip: Trip, allTrips: List<Trip>, ledger: BadgeLedger, now: Instant,
                              zone: ZoneId, weekFields: WeekFields, currentStreak: Int = 0, bestStreak: Int = 0): BadgeEvalResult {
        if (!trip.source.earnsRewards) return BadgeEvalResult(emptyList(), emptyMap(), ledger)
        val global = checkNewBadges(computeStats(allTrips, now, zone, weekFields, currentStreak, bestStreak), ledger)
        val single = computeStats(listOf(trip), now, zone, weekFields, currentStreak, bestStreak)
        val repeatable = BadgeDefinitions.all.filter { it.isRepeatable && it.checkUnlocked(single) }
        val counts = repeatable.associate { it.id to ((ledger.earnCounts[it.id] ?: 0) + 1) }
        return BadgeEvalResult(global.allEarned.filter { !it.isRepeatable } + repeatable, counts,
            global.ledger.copy(earnCounts = ledger.earnCounts + counts))
    }

    fun lastEarnedDates(badgeIds: Set<String>, trips: List<Trip>): Map<String, Instant> = buildMap {
        for (trip in trips) for (id in trip.earnedBadgeIds) {
            if (id in badgeIds && (get(id) == null || trip.startDate > getValue(id))) put(id, trip.startDate)
        }
    }
}

// iOS: BadgeFigure.swift. The threshold and its displayed figure share Measure's rounding and words.
fun BadgeFigure.shown(unit: DistanceUnit, lang: String, words: MeasureWords): String {
    val parts = when (kind) {
        BadgeFigure.Kind.distance -> Measure.distancePartsKm(value, unit, lang, words, style)
        BadgeFigure.Kind.speed -> Measure.speedParts(DistanceUnit.km.metresPerSecond(value), unit, lang, words)
        BadgeFigure.Kind.elevation -> Measure.elevationParts(value, unit, lang, words)
    }
    return parts.value + (if (atLeast) "+ " else " ") + parts.unit
}
