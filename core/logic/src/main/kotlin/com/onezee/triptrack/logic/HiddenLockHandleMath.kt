package com.onezee.triptrack.logic

import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.HiddenSection
import com.onezee.triptrack.model.TrackPoint
import com.onezee.triptrack.model.TripHiddenParts
import java.time.Duration
import java.util.UUID
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min

// iOS: HiddenPartsEditorModel.swift.
object HiddenLockHandleMath {
    const val parkedRadiusMetres = 60.0
    const val parkedDurationSeconds = 120L
    const val handleSearchRadiusPoints = 45
    const val handleFallbackDistancePoints = 28.0
    const val parkingGhostDistancePoints = 32.0
    const val detentToleranceMetres = 35.0
    const val minimumVisibleGapMetres = TripHiddenPartsResolver.sliverMetres
    const val sectionHalfLengthMetres = 120.0
    const val minimumSectionMetres = 40.0
    val edgeDetentsMetres = listOf(200.0, 500.0, 1_000.0, 2_000.0)

    data class ParkedStops(val departureIndex: Int?, val arrivalIndex: Int?)

    data class ScreenPoint(val x: Double, val y: Double)

    data class NearestPoint(val index: Int, val distance: Double)

    data class HandleIndex(val key: String, val index: Int, val ghost: Boolean, val parkingOnly: Boolean = false)

    data class SectionRemoval(val section: HiddenSection, val originalIndex: Int)

    fun parkedStops(points: List<TrackPoint>): ParkedStops {
        if (points.size < 2) return ParkedStops(null, null)
        val first = points.first()
        var departure = 0
        while (departure + 1 < points.size && distanceFrom(first, points[departure + 1]) <= parkedRadiusMetres) departure++
        val parkedHead = Duration.between(first.timestamp, points[departure].timestamp).seconds >= parkedDurationSeconds

        val lastIndex = points.lastIndex
        val last = points[lastIndex]
        var arrival = lastIndex
        while (arrival > 0 && distanceFrom(last, points[arrival - 1]) <= parkedRadiusMetres) arrival--
        val parkedTail = Duration.between(points[arrival].timestamp, last.timestamp).seconds >= parkedDurationSeconds
        return ParkedStops(departure.takeIf { parkedHead }, arrival.takeIf { parkedTail })
    }

    fun displayedStartMetres(prefix: List<Double>, index: Int, departureIndex: Int?): Double {
        if (prefix.isEmpty()) return 0.0
        val boundary = index.coerceIn(prefix.indices)
        return (prefix[boundary] - prefix[departureIndex?.coerceIn(prefix.indices) ?: 0]).coerceAtLeast(0.0)
    }

    fun displayedEndMetres(prefix: List<Double>, index: Int, arrivalIndex: Int?): Double {
        if (prefix.isEmpty()) return 0.0
        val boundary = index.coerceIn(prefix.indices)
        return (prefix[arrivalIndex?.coerceIn(prefix.indices) ?: prefix.lastIndex] - prefix[boundary]).coerceAtLeast(0.0)
    }

    fun handleIndices(
        points: List<TrackPoint>,
        prefix: List<Double>,
        parts: TripHiddenParts,
        defaultEdgeMetres: Int?,
    ): List<HandleIndex> {
        if (points.isEmpty() || prefix.isEmpty()) return emptyList()
        val stops = parkedStops(points)
        val startMetres = parts.startMetres ?: defaultEdgeMetres ?: 0
        val endMetres = parts.endMetres ?: defaultEdgeMetres ?: 0
        val startParking = startMetres > 0 && startMetresForParking(prefix, stops.departureIndex) == startMetres
        val endParking = endMetres > 0 && endMetresForParking(prefix, stops.arrivalIndex) == endMetres
        val startIndex = if (startParking) (stops.departureIndex!! + 1).coerceIn(points.indices)
            else startBoundaryIndex(prefix, startMetres)
        val endIndex = if (endParking) stops.arrivalIndex!!.coerceIn(points.indices)
            else endBoundaryIndex(prefix, endMetres)
        val handles = mutableListOf(
            HandleIndex("start", startIndex, startMetres <= 0, startParking),
            HandleIndex("end", endIndex, endMetres <= 0, endParking),
        )
        parts.sections.forEach { section ->
            sectionIndices(points, section)?.let { indices ->
                handles += HandleIndex("section-start:${section.id}", indices.first, false)
                handles += HandleIndex("section-end:${section.id}", indices.last, false)
            }
        }
        return handles
    }

    fun sectionIndices(points: List<TrackPoint>, section: HiddenSection): IntRange? {
        val start = points.indexOfFirst { it.timestamp >= section.from }
        val end = points.indexOfLast { it.timestamp <= section.to }
        if (start < 0 || end < start) return null
        return start..end
    }

    fun nearestHandleKey(
        points: List<TrackPoint>,
        prefix: List<Double>,
        parts: TripHiddenParts,
        defaultEdgeMetres: Int?,
        point: GeoPoint,
    ): String? = handleIndices(points, prefix, parts, defaultEdgeMetres)
        .minByOrNull { handle ->
            val trackPoint = points[handle.index]
            GeometryUtils.haversineDistance(point, GeoPoint(trackPoint.latitude, trackPoint.longitude))
        }?.key

    fun startBoundaryIndex(prefix: List<Double>, startMetres: Int): Int {
        if (prefix.isEmpty() || startMetres <= 0) return 0
        return prefix.indexOfFirst { it >= startMetres }.takeIf { it >= 0 } ?: prefix.lastIndex
    }

    fun endBoundaryIndex(prefix: List<Double>, endMetres: Int): Int {
        if (prefix.isEmpty() || endMetres <= 0) return prefix.lastIndex.coerceAtLeast(0)
        val total = prefix.last()
        return prefix.indexOfLast { total - it >= endMetres }.coerceAtLeast(0)
    }

    fun startMetresForBoundary(prefix: List<Double>, requestedIndex: Int): Int {
        if (prefix.size < 2) return 0
        val index = canonicalStartIndex(prefix, requestedIndex.coerceIn(prefix.indices))
        if (index == 0) return 0
        return integerThreshold(prefix[index - 1])
    }

    fun endMetresForBoundary(prefix: List<Double>, requestedIndex: Int): Int {
        if (prefix.size < 2) return 0
        val index = canonicalEndIndex(prefix, requestedIndex.coerceIn(prefix.indices))
        if (index == prefix.lastIndex) return 0
        return integerThreshold(prefix.last() - prefix[index + 1])
    }

    fun startMetresForParking(prefix: List<Double>, departureIndex: Int?): Int {
        if (departureIndex == null || departureIndex >= prefix.lastIndex) return 0
        return startMetresForBoundary(prefix, departureIndex + 1)
    }

    fun endMetresForParking(prefix: List<Double>, arrivalIndex: Int?): Int {
        if (arrivalIndex == null || arrivalIndex <= 0) return 0
        return integerThreshold(prefix.last() - prefix[arrivalIndex])
    }

    fun nearestPoint(
        project: (Int) -> ScreenPoint?,
        currentIndex: Int,
        lowerBound: Int,
        upperBound: Int,
        finger: ScreenPoint,
        pointCount: Int,
    ): NearestPoint? {
        if (pointCount == 0 || lowerBound > upperBound) return null
        val lower = lowerBound.coerceIn(0, pointCount - 1)
        val upper = upperBound.coerceIn(0, pointCount - 1)
        if (lower > upper) return null
        val current = currentIndex.coerceIn(lower, upper)
        val local = nearestInRange(
            project,
            finger,
            maxOf(lower, current - handleSearchRadiusPoints),
            min(upper, current + handleSearchRadiusPoints),
        ) ?: return null
        if (local.distance <= handleFallbackDistancePoints) return local
        return nearestInRange(project, finger, lower, upper) ?: local
    }

    fun detentIndex(
        prefix: List<Double>,
        currentIndex: Int,
        lowerBound: Int,
        upperBound: Int,
        originMetres: Double,
        fromEnd: Boolean,
    ): Int {
        if (prefix.isEmpty() || lowerBound > upperBound) return currentIndex
        val current = currentIndex.coerceIn(lowerBound, upperBound)
        val total = prefix.last()
        val displayed = if (fromEnd) total - prefix[current] - originMetres else prefix[current] - originMetres
        val detent = edgeDetentsMetres.minByOrNull { kotlin.math.abs(it - displayed) } ?: return current
        if (kotlin.math.abs(detent - displayed) > detentToleranceMetres) return current
        return (lowerBound..upperBound).minByOrNull { index ->
            val distance = if (fromEnd) total - prefix[index] - originMetres else prefix[index] - originMetres
            kotlin.math.abs(distance - detent)
        } ?: current
    }

    fun allowedStartMaximum(prefix: List<Double>, endIndex: Int, lowerBound: Int = 0): Int {
        if (prefix.isEmpty()) return 0
        val end = endIndex.coerceIn(prefix.indices)
        val minimumPrefix = prefix[end] - minimumVisibleGapMetres
        return (lowerBound.coerceIn(0, end)..end).lastOrNull { prefix[it] <= minimumPrefix }
            ?: lowerBound.coerceIn(0, end)
    }

    fun allowedEndMinimum(prefix: List<Double>, startIndex: Int, upperBound: Int = prefix.lastIndex): Int {
        if (prefix.isEmpty()) return 0
        val start = startIndex.coerceIn(prefix.indices)
        val maximum = upperBound.coerceIn(start, prefix.lastIndex)
        val minimumPrefix = prefix[start] + minimumVisibleGapMetres
        return (start..maximum).firstOrNull { prefix[it] >= minimumPrefix } ?: maximum
    }

    fun createSection(
        prefix: List<Double>,
        centreIndex: Int,
        visibleStart: Int,
        visibleEnd: Int,
        existing: List<IntRange>,
    ): IntRange? {
        if (prefix.isEmpty()) return null
        val start = visibleStart.coerceIn(prefix.indices)
        val end = visibleEnd.coerceIn(start, prefix.lastIndex)
        val centre = centreIndex.coerceIn(start, end)
        val fromMetres = prefix[centre] - sectionHalfLengthMetres
        val toMetres = prefix[centre] + sectionHalfLengthMetres
        val from = (start..centre).firstOrNull { prefix[it] >= fromMetres } ?: start
        val to = (centre..end).lastOrNull { prefix[it] <= toMetres } ?: end
        if (to <= from || prefix[to] - prefix[from] < minimumSectionMetres) return null
        val section = from..to
        if (existing.any { section.first <= it.last && it.first <= section.last }) return null
        return section
    }

    fun sectionDistanceMetres(prefix: List<Double>, section: IntRange): Double {
        if (prefix.isEmpty()) return 0.0
        return (prefix[section.last.coerceIn(prefix.indices)] - prefix[section.first.coerceIn(prefix.indices)]).coerceAtLeast(0.0)
    }

    fun shouldRemoveSection(prefix: List<Double>, section: IntRange): Boolean =
        sectionDistanceMetres(prefix, section) < minimumSectionMetres

    fun sectionRemovalOnRelease(
        prefix: List<Double>,
        points: List<TrackPoint>,
        sections: List<HiddenSection>,
        sectionId: UUID,
    ): SectionRemoval? {
        val originalIndex = sections.indexOfFirst { it.id == sectionId }
        if (originalIndex < 0) return null
        val section = sections[originalIndex]
        val indices = sectionIndices(points, section) ?: return null
        return SectionRemoval(section, originalIndex).takeIf { shouldRemoveSection(prefix, indices) }
    }

    fun restoreSection(sections: List<HiddenSection>, removal: SectionRemoval): List<HiddenSection> =
        sections.toMutableList().apply {
            if (none { it.id == removal.section.id }) add(removal.originalIndex.coerceIn(0, size), removal.section)
        }

    fun nearestDetent(metres: Double): Double? = edgeDetentsMetres
        .minByOrNull { kotlin.math.abs(it - metres) }
        ?.takeIf { kotlin.math.abs(it - metres) <= detentToleranceMetres }

    fun isOutwardPastStop(
        finger: ScreenPoint,
        stop: ScreenPoint,
        nearestPointBehindStop: ScreenPoint?,
    ): Boolean {
        val stopDistance = hypot(finger.x - stop.x, finger.y - stop.y)
        val behind = nearestPointBehindStop ?: return false
        val behindDistance = hypot(finger.x - behind.x, finger.y - behind.y)
        return stopDistance > parkingGhostDistancePoints && behindDistance > stopDistance
    }

    private fun nearestInRange(
        project: (Int) -> ScreenPoint?,
        finger: ScreenPoint,
        lowerBound: Int,
        upperBound: Int,
    ): NearestPoint? {
        var best: NearestPoint? = null
        for (index in lowerBound..upperBound) {
            val point = project(index) ?: continue
            val distance = hypot(point.x - finger.x, point.y - finger.y)
            if (best == null || distance < best.distance) best = NearestPoint(index, distance)
        }
        return best
    }

    private fun canonicalStartIndex(prefix: List<Double>, requested: Int): Int {
        var index = requested
        while (index > 0 && prefix[index - 1] == prefix[index]) index--
        return index
    }

    private fun canonicalEndIndex(prefix: List<Double>, requested: Int): Int {
        var index = requested
        while (index + 1 < prefix.size && prefix[index + 1] == prefix[index]) index++
        return index
    }

    private fun integerThreshold(metres: Double): Int = (floor(metres) + 1.0)
        .toLong().coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()

    private fun distanceFrom(origin: TrackPoint, point: TrackPoint): Double = GeometryUtils.haversineDistance(
        GeoPoint(origin.latitude, origin.longitude), GeoPoint(point.latitude, point.longitude),
    )
}
