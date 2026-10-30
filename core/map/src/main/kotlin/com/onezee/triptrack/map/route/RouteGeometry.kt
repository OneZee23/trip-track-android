package com.onezee.triptrack.map.route

import com.onezee.triptrack.logic.GeometryUtils
import com.onezee.triptrack.logic.defaultGapThreshold
import com.onezee.triptrack.logic.simplifyIndices
import com.onezee.triptrack.logic.simplifyRDP
import com.onezee.triptrack.model.GeoBounds
import com.onezee.triptrack.model.GeoPoint

/**
 * One drawn line of a route (iOS: SpeedPolyline, or a plain MKPolyline when the route has no speeds).
 *
 * [speed] is the representative speed in m/s of a speed-coloured line; a NEGATIVE speed means unknown
 * (a gap filled in after the recording, drawn as a grey dash); `null` is a route without speeds at
 * all (someone else's trip, a synced preview), drawn in the accent colour.
 */
data class RouteLine(val coordinates: List<GeoPoint>, val speed: Double?) {
    val isUnknownSpeed: Boolean get() = speed != null && speed < 0
}

/** A route prepared for drawing: its lines in route order and the box that frames them. */
data class RouteShape(val lines: List<RouteLine>, val bounds: GeoBounds?) {
    companion object {
        val empty = RouteShape(emptyList(), null)
    }
}

/**
 * iOS: RouteMapView (splitIntoSegments, splitByKnownSpeed, simplifyWithSpeeds, groupBySpeedZone,
 * drawEpsilon, unknownSpeedZone). Pure, so it runs off the main thread: an eight-hour trip is tens of
 * thousands of points.
 *
 * Simplification happens HERE, at drawing, and never in storage: the track keeps every point it was
 * recorded with, and only the map thins it.
 */
object RouteGeometry {
    /** Gap in metres that splits a track into separately drawn segments. */
    val gapThreshold: Double = GeometryUtils.defaultGapThreshold

    /**
     * Render-time simplification threshold in degrees (~2 m). It used to be 0.0001 (7–11 m across),
     * which cut exactly the corners 0.6.5 started recording densely for; two metres is just above GPS
     * noise. There is deliberately NO coarser threshold for long trips: the cost is set by speed-zone
     * changes, not by the threshold, and a five times more detailed line costs about a fifth more.
     */
    const val drawEpsilon = 0.00002

    /** Zone of a point with unknown speed: its own zone, so it never merges with a coloured one. */
    const val unknownSpeedZone = -1

    /** Representative speed of one group of consecutive points in the same colour zone. */
    data class SpeedGroup(val coordinates: List<GeoPoint>, val speed: Double)

    /** Points and speeds after simplification, one speed per kept point. */
    data class SimplifiedRoute(val coordinates: List<GeoPoint>, val speeds: List<Double>)

    /**
     * The lines of a route. [speeds] are m/s per point (negative: unknown); without one speed per point
     * the route is drawn without colours. A [preview] polyline is one solid segment: its points are
     * sparse, and gap detection would shred it into single points that draw nothing.
     */
    fun build(coordinates: List<GeoPoint>, speeds: List<Double> = emptyList(), preview: Boolean = false): RouteShape {
        if (coordinates.isEmpty()) return RouteShape.empty
        val bounds = boundsOf(coordinates)
        if (coordinates.size < 2) return RouteShape(emptyList(), bounds)
        val hasSpeeds = speeds.size == coordinates.size
        val segments = if (preview) {
            listOf(coordinates to (if (hasSpeeds) speeds else emptyList()))
        } else {
            splitIntoSegments(coordinates, if (hasSpeeds) speeds else emptyList(), gapThreshold)
        }
        val lines = ArrayList<RouteLine>()
        for ((segment, segmentSpeeds) in segments) {
            if (segment.size < 2) continue
            if (segmentSpeeds.size == segment.size) {
                for ((runCoordinates, runSpeeds) in splitByKnownSpeed(segment, segmentSpeeds)) {
                    val simplified = simplifyWithSpeeds(runCoordinates, runSpeeds, drawEpsilon)
                    for (group in groupBySpeedZone(simplified)) lines += RouteLine(group.coordinates, group.speed)
                }
            } else {
                lines += RouteLine(GeometryUtils.simplifyRDP(segment, drawEpsilon), null)
            }
        }
        return RouteShape(lines, bounds)
    }

    /** Split points and speeds into continuous segments at gaps longer than [gapThreshold] metres. */
    fun splitIntoSegments(
        coordinates: List<GeoPoint>,
        speeds: List<Double>,
        gapThreshold: Double,
    ): List<Pair<List<GeoPoint>, List<Double>>> {
        if (coordinates.size < 2) return listOf(coordinates to speeds)
        val hasSpeeds = speeds.size == coordinates.size
        val segments = ArrayList<Pair<List<GeoPoint>, List<Double>>>()
        var current = arrayListOf(coordinates[0])
        var currentSpeeds = if (hasSpeeds) arrayListOf(speeds[0]) else arrayListOf()
        for (i in 1 until coordinates.size) {
            if (GeometryUtils.haversineDistance(coordinates[i - 1], coordinates[i]) > gapThreshold) {
                if (current.size >= 2) segments += current to currentSpeeds
                current = arrayListOf(coordinates[i])
                currentSpeeds = if (hasSpeeds) arrayListOf(speeds[i]) else arrayListOf()
            } else {
                current += coordinates[i]
                if (hasSpeeds) currentSpeeds += speeds[i]
            }
        }
        if (current.size >= 2) segments += current to currentSpeeds
        return segments
    }

    /**
     * Cuts a segment into runs of known and unknown speed. The dash runs from the last real point
     * before a hole to the first one after it: those are the hole's edges, shared with the neighbouring
     * runs, so the line does not break. This must happen BEFORE simplification, or a straight filled-in
     * piece on a straight road would merge with its neighbours into one coloured line.
     */
    fun splitByKnownSpeed(coordinates: List<GeoPoint>, speeds: List<Double>): List<Pair<List<GeoPoint>, List<Double>>> {
        if (coordinates.size != speeds.size || coordinates.size <= 1) return listOf(coordinates to speeds)
        val runs = ArrayList<Pair<List<GeoPoint>, List<Double>>>()
        var start = 0
        var index = 0
        while (index < coordinates.size) {
            if (!(speeds[index] < 0)) {
                index += 1
                continue
            }
            var end = index
            while (end + 1 < coordinates.size && speeds[end + 1] < 0) end += 1
            val left = maxOf(index - 1, 0)
            val right = minOf(end + 1, coordinates.size - 1)
            if (left > start) runs += coordinates.subList(start, left + 1).toList() to speeds.subList(start, left + 1).toList()
            runs += coordinates.subList(left, right + 1).toList() to List(right - left + 1) { -1.0 }
            start = right
            index = end + 1
        }
        if (start < coordinates.size - 1) {
            runs += coordinates.subList(start, coordinates.size).toList() to speeds.subList(start, speeds.size).toList()
        }
        return runs.filter { it.first.size > 1 }
    }

    /** RDP that keeps the speed of every kept point aligned with it. */
    fun simplifyWithSpeeds(coordinates: List<GeoPoint>, speeds: List<Double>, epsilon: Double): SimplifiedRoute {
        if (coordinates.size <= 2) return SimplifiedRoute(coordinates, speeds)
        val kept = GeometryUtils.simplifyIndices(coordinates, 0, coordinates.size - 1, epsilon).sorted()
        return SimplifiedRoute(kept.map { coordinates[it] }, kept.map { speeds[it] })
    }

    /**
     * Groups consecutive points of one colour zone into one line: the overlay count follows the zone
     * changes, not the points. A group closes ON the first point of the next zone, so the line is
     * continuous.
     */
    fun groupBySpeedZone(route: SimplifiedRoute): List<SpeedGroup> {
        if (route.coordinates.size < 2) return emptyList()
        val groups = ArrayList<SpeedGroup>()
        var currentZone = speedZone(route.speeds[0])
        var current = arrayListOf(route.coordinates[0])
        var currentSpeed = route.speeds[0]
        for (i in 1 until route.coordinates.size) {
            val zone = speedZone(route.speeds[i])
            if (zone == currentZone) {
                current += route.coordinates[i]
            } else {
                current += route.coordinates[i]
                groups += SpeedGroup(current, currentSpeed)
                currentZone = zone
                current = arrayListOf(route.coordinates[i])
                currentSpeed = route.speeds[i]
            }
        }
        if (current.size >= 2) groups += SpeedGroup(current, currentSpeed)
        return groups
    }

    /** Speed in m/s to its zone; unknown speed has its own. Delegates to [SpeedColorScale]. */
    fun speedZone(speedMs: Double): Int = if (speedMs < 0) unknownSpeedZone else SpeedColorScale.zone(speedMs)

    private fun boundsOf(coordinates: List<GeoPoint>): GeoBounds {
        val box = GeoBounds(minLat = 90.0, maxLat = -90.0, minLon = 180.0, maxLon = -180.0)
        for (c in coordinates) {
            box.minLat = minOf(box.minLat, c.latitude)
            box.maxLat = maxOf(box.maxLat, c.latitude)
            box.minLon = minOf(box.minLon, c.longitude)
            box.maxLon = maxOf(box.maxLon, c.longitude)
        }
        return box
    }
}
