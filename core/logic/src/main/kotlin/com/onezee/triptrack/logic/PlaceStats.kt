package com.onezee.triptrack.logic

import com.onezee.triptrack.model.PlaceListItem
import com.onezee.triptrack.model.Place
import com.onezee.triptrack.model.PlacePass
import com.onezee.triptrack.model.PlaceStats
import java.time.Instant
import kotlin.math.abs
import kotlin.math.min

// iOS: PlaceStats.swift
val PlaceStats.isFirstTime: Boolean get() = passCount == 1
val PlaceStats.hasUsual: Boolean get() = passCount > 1
val PlaceStats.Companion.directionTolerance: Double get() = 45.0
val PlaceStats.Companion.frequentGuestPasses: Int get() = 5
val PlaceStats.Companion.frequentGuestWindow: Double get() = 90 * 86_400.0

fun PlaceStats.Companion.build(passes: List<PlacePass>, now: Instant = Instant.now()): PlaceStats {
    val sorted = passes.sortedBy { it.timestamp }
    var pool = passes.filter { it.hasCourse }.sortedByDescending { it.timestamp }
    val directions = mutableListOf<PlaceStats.Direction>()
    while (pool.isNotEmpty()) {
        // The newest remaining pass anchors a direction, so today's route wins.
        val anchor = pool.first()
        val group = pool.filter { angularDistance(it.course, anchor.course) <= directionTolerance }
        val ids = group.map { it.id }.toSet()
        pool = pool.filter { it.id !in ids }
        val times = group.map { it.elapsedFromStart }.sorted()
        directions += PlaceStats.Direction(anchor.course, group.size, median(times), times.first(), times.last(),
            anchor.timestamp, anchor.tripId)
    }
    return PlaceStats(passes.size, sorted.firstOrNull()?.timestamp, sorted.lastOrNull()?.timestamp,
        passes.takeIf { it.isNotEmpty() }?.let { median(it.map { pass -> pass.elapsedFromStart }.sorted()) },
        passes.count { now.secondsSince(it.timestamp) <= frequentGuestWindow } >= frequentGuestPasses,
        directions.sortedWith(compareByDescending<PlaceStats.Direction> { it.count }.thenByDescending { it.lastAt }))
}

fun PlaceStats.Companion.angularDistance(a: Double, b: Double): Double {
    val d = abs(a - b) % 360
    return min(d, 360 - d)
}

private fun median(sorted: List<Double>): Double {
    val n = sorted.size
    return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2
}

// iOS: PlaceListItem.swift. Presentation uses the main direction, with an overall fallback.
val PlaceListItem.isFirstTime: Boolean get() = stats.isFirstTime
val PlaceListItem.isFrequentGuest: Boolean get() = stats.isFrequentGuest
val PlaceListItem.lastAt: Instant? get() = stats.lastAt

fun PlaceListItem.Companion.build(place: Place, passes: List<PlacePass>, now: Instant = Instant.now()): PlaceListItem {
    val stats = PlaceStats.build(passes, now)
    return PlaceListItem(place, stats, if (stats.hasUsual) stats.directions.firstOrNull()?.median ?: stats.medianElapsed else null)
}

fun PlaceListItem.Companion.sorted(items: List<PlaceListItem>): List<PlaceListItem> = items.sortedWith { a, b ->
    val first = a.lastAt
    val second = b.lastAt
    when {
        first != null && second != null -> second.compareTo(first)
        first == null && second == null -> (a.place.name ?: "").compareTo(b.place.name ?: "")
        first == null -> 1
        else -> -1
    }
}
