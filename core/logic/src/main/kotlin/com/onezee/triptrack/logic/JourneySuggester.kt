package com.onezee.triptrack.logic

import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.Journey
import com.onezee.triptrack.model.JourneyAggregate
import com.onezee.triptrack.model.Trip
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

// iOS: JourneySuggester.swift
object JourneySuggester {
    const val awayRadius = 50_000.0
    const val homeRadius = 5_000.0
    const val silence = 7 * 86_400.0
    const val cellPrecision = 7
    const val minHomeNights = 10
    const val minHomeSpanDays = 14
    const val usualVisits = 3
    const val usualWindow = 30 * 86_400.0
    const val homeReaskDelay = 30 * 86_400.0
    const val recency = 14 * 86_400.0
    const val chainGap = 36 * 3_600.0

    fun shouldAskHome(
        homeLocation: GeoPoint?,
        homeAsked: Boolean,
        declinedAt: Instant?,
        now: Instant,
    ): Boolean =
        homeLocation == null &&
            !homeAsked &&
            (declinedAt == null || now.secondsSince(declinedAt) >= homeReaskDelay)

    fun inferHome(trips: List<Trip>, zone: ZoneId = ZoneId.systemDefault()): GeoPoint? {
        val nights = mutableMapOf<String, MutableSet<LocalDate>>()
        val points = mutableMapOf<String, MutableList<GeoPoint>>()
        var earliest: Instant? = null
        var latest: Instant? = null
        for (trip in trips) {
            val finish = trip.endDate ?: trip.startDate
            earliest = minOf(earliest ?: trip.startDate, trip.startDate)
            latest = maxOf(latest ?: finish, finish)
            val end = JourneyAggregate.endCoordinate(trip) ?: continue
            val hour = finish.atZone(zone).hour
            if (hour < 18 && hour >= 6) continue
            // A return after midnight belongs to the preceding evening.
            val night = finish.minusSeconds(6 * 3_600).atZone(zone).toLocalDate()
            val cell = cell(end)
            nights.getOrPut(cell) { mutableSetOf() }.add(night)
            points.getOrPut(cell) { mutableListOf() }.add(end)
        }
        if (
            earliest == null ||
                latest == null ||
                ChronoUnit.DAYS.between(
                    earliest.atZone(zone).toLocalDate(),
                    latest.atZone(zone).toLocalDate(),
                ) + 1 < minHomeSpanDays
        )
            return null
        val winner =
            nights.keys
                .sortedWith(
                    compareByDescending<String> { nights.getValue(it).size }
                        .thenByDescending { points.getValue(it).size }
                        .thenBy { it }
                )
                .firstOrNull() ?: return null
        if (nights.getValue(winner).size < minHomeNights) return null
        val ends = points.getValue(winner)
        return GeoPoint(
            ends.sumOf { it.latitude } / ends.size,
            ends.sumOf { it.longitude } / ends.size,
        )
    }

    fun chainAround(anchor: Trip, trips: List<Trip>): List<Trip> {
        val ordered = trips.sortedBy { it.startDate }
        val index = ordered.indexOfFirst { it.id == anchor.id }
        if (index < 0) return listOf(anchor)
        var back = index
        var forward = index
        while (back > 0 && joins(ordered[back - 1], ordered[back])) back--
        while (
            forward < ordered.lastIndex && joins(ordered[forward], ordered[forward + 1])
        ) forward++
        return ordered.subList(back, forward + 1)
    }

    private fun joins(earlier: Trip, later: Trip): Boolean {
        val end = JourneyAggregate.endCoordinate(earlier) ?: return false
        val start = JourneyAggregate.startCoordinate(later) ?: return false
        return later.startDate.secondsSince(earlier.endDate ?: earlier.startDate) <= chainGap &&
            start.distanceFrom(end) <= homeRadius
    }

    fun suggestion(
        trips: List<Trip>,
        home: GeoPoint,
        existing: List<Journey>,
        now: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Trip>? {
        val ordered = trips.sortedBy { it.startDate }
        val taken =
            ordered.filter { trip -> existing.any { it.contains(trip) } }.map { it.id }.toSet()
        for (index in ordered.indices.reversed()) {
            val last = ordered[index]
            if (now.secondsSince(last.endDate ?: last.startDate) > recency) return null
            if (last.id in taken) continue
            val end = JourneyAggregate.endCoordinate(last) ?: continue
            val chain = chain(index, ordered, home, taken) ?: continue
            // Hotel nights inside the candidate cannot make their own hotel a usual destination.
            val usual = usualCells(ordered, chain.map { it.id }.toSet(), now)
            if (isSettled(end, home, usual) && hasNightAway(chain, home, usual, zone)) return chain
        }
        return null
    }

    private fun chain(
        last: Int,
        ordered: List<Trip>,
        home: GeoPoint,
        taken: Set<UUID>,
    ): List<Trip>? {
        var i = last
        while (true) {
            val front = ordered[i]
            val start = JourneyAggregate.startCoordinate(front)
            if (start != null && start.distanceFrom(home) <= homeRadius)
                return if (last - i + 1 >= 2) ordered.subList(i, last + 1) else null
            if (i <= 0) return null
            val prev = ordered[i - 1]
            if (prev.id in taken) return null
            val end = JourneyAggregate.endCoordinate(prev) ?: return null
            if (
                start == null ||
                    front.startDate.secondsSince(prev.endDate ?: prev.startDate) > silence
            )
                return null
            if (
                start.distanceFrom(end) > homeRadius &&
                    !(end.distanceFrom(home) >= awayRadius &&
                        start.distanceFrom(home) >= awayRadius)
            )
                return null
            i--
        }
    }

    private fun hasNightAway(
        chain: List<Trip>,
        home: GeoPoint,
        usual: Set<String>,
        zone: ZoneId,
    ): Boolean {
        for ((a, b) in chain.zipWithNext()) {
            val spot = JourneyAggregate.endCoordinate(a) ?: continue
            if (spot.distanceFrom(home) < awayRadius || isSettled(spot, home, usual)) continue
            val from = a.endDate ?: a.startDate
            val date = from.atZone(zone).toLocalDate()
            // Foundation's nextTime moves a nonexistent 02:00 to the next valid local time.
            fun night(day: LocalDate): Instant {
                val local = day.atTime(LocalTime.of(2, 0))
                val transition = zone.rules.getTransition(local)
                return if (transition?.isGap == true) transition.instant
                else local.atZone(zone).toInstant()
            }
            val today = night(date)
            val next = if (today > from) today else night(date.plusDays(1))
            if (next <= b.startDate) return true
        }
        return false
    }

    private fun isSettled(point: GeoPoint, home: GeoPoint, usual: Set<String>): Boolean =
        point.distanceFrom(home) <= homeRadius || cell(point) in usual

    private fun cell(point: GeoPoint): String =
        GeohashEncoder.encode(point.latitude, point.longitude, cellPrecision)

    private fun usualCells(trips: List<Trip>, excluded: Set<UUID>, now: Instant): Set<String> {
        val counts = mutableMapOf<String, Int>()
        for (trip in trips) {
            val finish = trip.endDate ?: trip.startDate
            if (trip.id in excluded || finish > now || now.secondsSince(finish) > usualWindow)
                continue
            val end = JourneyAggregate.endCoordinate(trip) ?: continue
            val cell = cell(end)
            counts[cell] = (counts[cell] ?: 0) + 1
        }
        return counts.filterValues { it >= usualVisits }.keys
    }
}
