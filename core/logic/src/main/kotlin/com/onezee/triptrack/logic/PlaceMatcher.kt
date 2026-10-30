package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.Instant
import java.util.UUID
import kotlin.math.*

// iOS: Place.swift
fun Place.Companion.cell(latitude: Double, longitude: Double): String =
    GeohashEncoder.encode(latitude, longitude, cellPrecision)
val Place.coordinate: GeoPoint get() = GeoPoint(latitude, longitude)

// iOS: PlacePass.swift
val PlacePass.hasCourse: Boolean get() = course >= 0

// iOS: PlaceMatcher.swift
object PlaceMatcher {
    const val passRadius = 100.0
    val passGap: Double get() = TripRouteLocator.distinctPassGap
    const val prefilterPrecision = 5

    fun cells(coordinates: List<GeoPoint>): Set<String> = coordinates.map {
        GeohashEncoder.encode(it.latitude, it.longitude, prefilterPrecision)
    }.toSet()

    fun isCandidate(place: Place, tripCells: Set<String>): Boolean {
        if (tripCells.isEmpty()) return true
        val cell5 = place.cell.take(prefilterPrecision)
        return cell5 in tripCells || GeohashEncoder.neighbors(cell5).any { it in tripCells }
    }

    fun passes(through: Place, tripId: UUID, points: List<TrackPoint>, startDate: Instant): List<PlacePass> {
        val measured = points.filter { it.countsForDistance }
        return TripRouteLocator.passes(through.coordinate, measured, passRadius, startDate).map { fix ->
            PlacePass(placeId = through.id, tripId = tripId, timestamp = fix.timestamp,
                elapsedFromStart = fix.elapsedFromStart, distanceFromStart = fix.distanceFromStart,
                course = course(fix.index, measured))
        }
    }

    fun course(at: Int, points: List<TrackPoint>): Double {
        if (at !in points.indices) return PlacePass.unknownCourse
        if (points[at].course >= 0) return points[at].course
        val from = points[max(0, at - 1)].coordinate
        val to = points[min(points.lastIndex, at + 1)].coordinate
        return if (from == to) PlacePass.unknownCourse else bearing(from, to)
    }

    fun bearing(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = from.latitude * PI / 180
        val lat2 = to.latitude * PI / 180
        val dLon = (to.longitude - from.longitude) * PI / 180
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (atan2(y, x) * 180 / PI + 360) % 360
    }
}
