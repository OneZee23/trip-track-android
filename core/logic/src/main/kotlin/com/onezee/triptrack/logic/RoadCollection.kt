package com.onezee.triptrack.logic

import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.RoadLevel
import com.onezee.triptrack.model.RoadRarity
import com.onezee.triptrack.model.TrackPoint
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.model.TripOrigin
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// iOS: GamificationModels.swift, RoadRarity.from. Metric kilometres: the thresholds are game rules.
fun RoadRarity.Companion.from(distanceKm: Double): RoadRarity = when {
    distanceKm < 20 -> RoadRarity.common
    distanceKm < 100 -> RoadRarity.uncommon
    distanceKm < 300 -> RoadRarity.rare
    distanceKm < 1000 -> RoadRarity.epic
    else -> RoadRarity.legendary
}

// iOS: GamificationModels.swift, RoadLevel.from.
fun RoadLevel.Companion.from(timesDriven: Int): RoadLevel = when {
    timesDriven >= 50 -> RoadLevel.mastered
    timesDriven >= 25 -> RoadLevel.gold
    timesDriven >= 10 -> RoadLevel.silver
    timesDriven >= 3 -> RoadLevel.bronze
    else -> RoadLevel.discovered
}

// iOS: RoadCollectionManager.swift, the matching half. The road rows themselves are core:data's.
object RoadCollection {
    /** The ordered geohash-5 cells a trip crosses, consecutive duplicates skipped. */
    fun fingerprint(points: List<TrackPoint>): List<String> {
        val sequence = mutableListOf<String>()
        var last = ""
        for (point in points) {
            val hash = GeohashEncoder.encode(point.latitude, point.longitude, 5)
            if (hash != last) {
                sequence += hash
                last = hash
            }
        }
        return sequence
    }

    /** A cell and its eight neighbours: the slack a start or an end is matched with. */
    fun neighbourhood(hash: String): Set<String> = (GeohashEncoder.neighbors(hash) + hash).toSet()

    /**
     * iOS: findMatchingRoad. Ends near each other in either direction, and a Jaccard similarity of
     * the cell sets of at least 0.7.
     */
    fun matches(
        roadStart: String?,
        roadEnd: String?,
        roadSequence: String?,
        startNeighbours: Set<String>,
        endNeighbours: Set<String>,
        fingerprint: Set<String>,
    ): Boolean {
        if (roadStart == null || roadEnd == null) return false
        val forward = roadStart in startNeighbours && roadEnd in endNeighbours
        val reverse = roadEnd in startNeighbours && roadStart in endNeighbours
        if (!forward && !reverse) return false
        val cells = (roadSequence ?: return false).split(",").filter { it.isNotEmpty() }.toSet()
        val union = fingerprint.union(cells)
        val similarity = if (union.isEmpty()) 0.0 else fingerprint.intersect(cells).size.toDouble() / union.size
        return similarity >= 0.7
    }

    /** iOS: formatRoadName. The trip's title, else its region, else «start → end» cells. */
    fun roadName(trip: Trip): String {
        trip.title?.takeIf { it.isNotEmpty() }?.let { return it }
        val first = trip.trackPoints.firstOrNull() ?: return "Road"
        val last = trip.trackPoints.last()
        val start = GeohashEncoder.encode(first.latitude, first.longitude, 4)
        val end = GeohashEncoder.encode(last.latitude, last.longitude, 4)
        if (start == end) return trip.region ?: "Loop"
        return trip.region ?: "$start → $end"
    }
}

// iOS: SimilarRecordedTrips.swift. How many recorded trips drove this road: the completion card's
// count comes from the history on this phone, restored trips included.
object SimilarRecordedTrips {
    fun count(trip: Trip, history: List<Trip>): Int {
        if (!isEligible(trip)) return 0
        val reference = Fingerprint.of(coordinates(trip)) ?: return 0
        val matches = mutableSetOf(trip.id)
        for (candidate in history) {
            if (candidate.id in matches || !isEligible(candidate)) continue
            val longest = max(trip.distance, candidate.distance)
            if (abs(trip.distance - candidate.distance) > longest * 0.3) continue
            val coordinates = coordinates(candidate)
            if (!reference.hasMatchingEndpoints(coordinates)) continue
            val fingerprint = Fingerprint.of(coordinates) ?: continue
            if (!reference.isSimilar(fingerprint)) continue
            matches += candidate.id
        }
        return matches.size
    }

    private fun isEligible(trip: Trip): Boolean =
        trip.endDate != null && !trip.isDraft && !trip.isJunk() && trip.source == TripOrigin.recorded &&
            trip.distance.isFinite() && trip.distance > 0 && trip.recordingBreaks.isEmpty()

    /** A known gap (a fill, a split segment, a minute without a point) is not joined into a road. */
    private fun coordinates(trip: Trip): List<GeoPoint> {
        if (trip.trackPoints.any { it.isInterpolated }) return emptyList()
        for ((a, b) in trip.trackPoints.zipWithNext()) {
            if (a.recordingSegmentIndex != b.recordingSegmentIndex || b.timestamp.secondsSince(a.timestamp) > 60) return emptyList()
        }
        trip.previewPolyline?.let { preview ->
            val decoded = Trip.decodePolyline(preview)
            if (decoded.size >= 2 && decoded.all { it.isValid }) return decoded
        }
        return trip.trackPoints.map { GeoPoint(it.latitude, it.longitude) }
    }

    private class Fingerprint(val start: Cell, val end: Cell, val cells: Set<Cell>) {
        fun hasMatchingEndpoints(coordinates: List<GeoPoint>): Boolean {
            val first = coordinates.firstOrNull() ?: return false
            val last = coordinates.last()
            if (!first.isValid || !last.isValid) return false
            val otherStart = Cell.of(first)
            val otherEnd = Cell.of(last)
            return (start.isNear(otherStart) && end.isNear(otherEnd)) || (start.isNear(otherEnd) && end.isNear(otherStart))
        }

        fun isSimilar(other: Fingerprint): Boolean {
            val intersection = cells.count { it in other.cells }
            val union = cells.size + other.cells.size - intersection
            return intersection.toDouble() / union >= 0.7
        }

        companion object {
            const val cellDegrees = 360.0 / 8192

            fun of(coordinates: List<GeoPoint>): Fingerprint? {
                if (coordinates.size < 2 || !coordinates.all { it.isValid }) return null
                val start = Cell.of(coordinates.first())
                val end = Cell.of(coordinates.last())
                val visited = mutableSetOf(start)
                for ((a, b) in coordinates.zipWithNext()) {
                    var longitudeDelta = b.longitude - a.longitude
                    if (longitudeDelta > 180) longitudeDelta -= 360
                    if (longitudeDelta < -180) longitudeDelta += 360
                    val latitudeDelta = b.latitude - a.latitude
                    // Four samples per crossed cell: dense adjacent points stay O(1).
                    val steps = max(1, ceil(max(abs(longitudeDelta), abs(latitudeDelta)) / cellDegrees * 4).toInt())
                    for (step in 1..steps) {
                        val fraction = step.toDouble() / steps
                        visited += Cell.of(GeoPoint(a.latitude + latitudeDelta * fraction, a.longitude + longitudeDelta * fraction))
                    }
                }
                if (visited.size < 2) return null
                return Fingerprint(start, end, visited)
            }
        }
    }

    private data class Cell(val x: Int, val y: Int) {
        fun isNear(other: Cell): Boolean {
            val dx = abs(x - other.x)
            return min(dx, 8192 - dx) <= 1 && abs(y - other.y) <= 1
        }

        companion object {
            fun of(coordinate: GeoPoint): Cell {
                val rawX = floor((coordinate.longitude + 180) / Fingerprint.cellDegrees).toInt()
                return Cell((rawX % 8192 + 8192) % 8192, min(4095, max(0, floor((coordinate.latitude + 90) / Fingerprint.cellDegrees).toInt())))
            }
        }
    }
}
