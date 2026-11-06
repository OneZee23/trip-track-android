// iOS: ManualTripDateDefaults.swift, ManualTripDurationPolicy.swift, ManualTripBuilder.swift,
// ManualTripActiveField.swift, ManualTripPreset.swift
package com.onezee.triptrack.logic

import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.RoadFillState
import com.onezee.triptrack.model.TrackPoint
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.model.TripDistanceGate
import com.onezee.triptrack.model.TripOrigin
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ceil
import kotlin.math.sin

object ManualTripRules {
    const val MINIMUM_DURATION_SECONDS = 60.0
    const val MAXIMUM_DURATION_SECONDS = 604800.0
    const val MAX_PLAUSIBLE_AVERAGE_METRES_PER_SECOND = 50.0
    const val MAXIMUM_AGE_SECONDS = 630720000.0
    const val END_BUFFER_SECONDS = 300L
    const val DURATION_STEP_SECONDS = 900.0

    fun untouchedStart(current: Instant, durationSeconds: Double, now: Instant): Instant =
        minOf(current, now.minusMillis(((durationSeconds + END_BUFFER_SECONDS) * 1000).toLong()))

    fun defaultStart(day: LocalDate, now: Instant, zone: ZoneId): Instant =
        minOf(day.atTime(9, 0).atZone(zone).toInstant(), now)

    fun bounds(current: Instant, now: Instant, zone: ZoneId): ClosedRange<Instant> {
        val earliest = now.atZone(zone).minusYears(20).toInstant()
        return minOf(earliest, current)..maxOf(now, current)
    }

    fun duration(current: Double, suggested: Double?, touched: Boolean, step: Double): Double =
        if (!touched && suggested != null && suggested > 0.0) kotlin.math.floor(suggested / step + 0.5) * step else current

    fun activeField(from: Any?, to: Any?): ManualTripField = when {
        from == null -> ManualTripField.from
        to == null -> ManualTripField.to
        else -> ManualTripField.to
    }

    fun feasibleDuration(routeMetres: Double): Double =
        maxOf(MINIMUM_DURATION_SECONDS, routeMetres / MAX_PLAUSIBLE_AVERAGE_METRES_PER_SECOND)

    fun minimumDuration(routeMetres: Double): Double =
        ceil(feasibleDuration(routeMetres) / DURATION_STEP_SECONDS) * DURATION_STEP_SECONDS

    fun hasEnded(start: Instant, durationSeconds: Double, now: Instant): Boolean =
        start.plusMillis((durationSeconds * 1000).toLong()) <= now

    fun title(from: String?, to: String?): String? {
        val a = from?.trim().orEmpty()
        val b = to?.trim().orEmpty()
        return when {
            a.isEmpty() && b.isEmpty() -> null
            a.isEmpty() -> b
            b.isEmpty() || a.equals(b, ignoreCase = true) -> a
            else -> "$a → $b"
        }
    }

    fun summaryLine(distance: String, duration: String, whenText: String): String =
        listOf(distance, duration, whenText).joinToString(" · ")

    fun build(coordinates: List<GeoPoint>, start: Instant, durationSeconds: Double,
              now: Instant, vehicleId: UUID?, title: String?): Trip? {
        if (coordinates.size < 2 || durationSeconds <= 0.0 || start > now ||
            start < now.minusSeconds(MAXIMUM_AGE_SECONDS.toLong()) ||
            !hasEnded(start, durationSeconds, now)) return null
        val stamps = timestamps(coordinates.size, start, durationSeconds)
        val samples = coordinates.zip(stamps).map { (point, time) ->
            TripDistanceGate.Sample(point.latitude, point.longitude, time)
        }
        val distance = TripDistanceGate.totalDistance(samples)
        val speed = distance / durationSeconds
        val points = coordinates.mapIndexed { index, point ->
            TrackPoint(latitude = point.latitude, longitude = point.longitude, altitude = 0.0,
                speed = speed, course = bearing(coordinates[minOf(index, coordinates.lastIndex - 1)], coordinates[minOf(index + 1, coordinates.lastIndex)]),
                horizontalAccuracy = -1.0, timestamp = stamps[index])
        }
        return Trip(startDate = start, endDate = start.plusMillis((durationSeconds * 1000).toLong()),
            distance = distance, maxSpeed = speed, averageSpeed = speed, trackPoints = points,
            title = title?.trim()?.takeIf(String::isNotEmpty), titleIsCustom = !title.isNullOrBlank(),
            elevation = 0.0, isPrivate = true, vehicleId = vehicleId, source = TripOrigin.manual,
            roadFillState = RoadFillState.done)
    }

    fun timestamps(count: Int, start: Instant, durationSeconds: Double): List<Instant> {
        if (count <= 1) return listOf(start)
        val stepNanos = (durationSeconds * 1_000_000_000.0) / (count - 1)
        return (0 until count).map { start.plusNanos((it * stepNanos).toLong()) }
    }

    private fun bearing(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }
}

enum class ManualTripField { from, to }
