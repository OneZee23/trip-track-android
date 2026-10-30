package com.onezee.triptrack.logic

import com.onezee.triptrack.model.GeoPoint
import java.time.Duration
import java.time.Instant
import kotlin.math.*

// iOS: CLLocation, read by TripManager.swift and KalmanLocationFilter.swift.
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val horizontalAccuracy: Double = -1.0,
    val verticalAccuracy: Double = -1.0,
    val speed: Double = -1.0,
    val speedAccuracy: Double = -1.0,
    val course: Double = -1.0,
    val courseAccuracy: Double = -1.0,
    val timestamp: Instant = Instant.now(),
) {
    val coordinate: GeoPoint get() = GeoPoint(latitude, longitude)
    fun distanceFrom(other: LocationFix): Double = coordinate.distanceFrom(other.coordinate)
}

internal fun Instant.secondsSince(other: Instant): Double {
    val duration = Duration.between(other, this)
    return duration.seconds.toDouble() + duration.nano / 1e9
}

internal fun Instant.addingSeconds(seconds: Double): Instant {
    val whole = floor(seconds).toLong()
    return plusSeconds(whole).plusNanos(((seconds - whole) * 1e9).roundToLong())
}

internal val GeoPoint.isValid: Boolean
    get() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

// iOS: CLLocation.distance(from:). WGS84 inverse distance, independent of Android APIs.
fun GeoPoint.distanceFrom(other: GeoPoint): Double {
    if (!isValid || !other.isValid) return Double.NaN
    if (this == other) return 0.0
    val major = 6_378_137.0
    val flattening = 1.0 / 298.257223563
    val minor = major * (1 - flattening)
    val u1 = atan((1 - flattening) * tan(Math.toRadians(latitude)))
    val u2 = atan((1 - flattening) * tan(Math.toRadians(other.latitude)))
    val longitudeDelta = Math.toRadians(other.longitude - longitude)
    var lambda = longitudeDelta
    repeat(200) {
        val sinSigma = hypot(cos(u2) * sin(lambda), cos(u1) * sin(u2) - sin(u1) * cos(u2) * cos(lambda))
        if (sinSigma == 0.0) return 0.0
        val cosSigma = sin(u1) * sin(u2) + cos(u1) * cos(u2) * cos(lambda)
        val sigma = atan2(sinSigma, cosSigma)
        val sinAlpha = cos(u1) * cos(u2) * sin(lambda) / sinSigma
        val cosSqAlpha = 1 - sinAlpha * sinAlpha
        val cos2SigmaM = if (cosSqAlpha == 0.0) 0.0 else cosSigma - 2 * sin(u1) * sin(u2) / cosSqAlpha
        val c = flattening / 16 * cosSqAlpha * (4 + flattening * (4 - 3 * cosSqAlpha))
        val next = longitudeDelta + (1 - c) * flattening * sinAlpha *
            (sigma + c * sinSigma * (cos2SigmaM + c * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)))
        if (abs(next - lambda) < 1e-12) {
            val uSq = cosSqAlpha * (major * major - minor * minor) / (minor * minor)
            val a = 1 + uSq / 16384 * (4096 + uSq * (-768 + uSq * (320 - 175 * uSq)))
            val b = uSq / 1024 * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)))
            val correction = b * sinSigma * (cos2SigmaM + b / 4 *
                (cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM) - b / 6 * cos2SigmaM *
                    (-3 + 4 * sinSigma * sinSigma) * (-3 + 4 * cos2SigmaM * cos2SigmaM)))
            return minor * a * (sigma - correction)
        }
        lambda = next
    }
    // The inverse iteration can fail near antipodes. Such a segment is far
    // beyond recording/fill limits; retain a finite distance for its rejection.
    val phi1 = Math.toRadians(latitude)
    val phi2 = Math.toRadians(other.latitude)
    val h = sin((phi2 - phi1) / 2).pow(2) + cos(phi1) * cos(phi2) * sin(longitudeDelta / 2).pow(2)
    return 6_371_008.8 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

// iOS: GeometryUtils.swift
fun courseDelta(a: Double, b: Double): Double {
    if (!(a >= 0 && b >= 0)) return 0.0
    val difference = abs(a - b) % 360
    return min(difference, 360 - difference)
}
