package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.Instant
import java.time.ZoneId

// iOS: JourneyAggregateTests.swift / JourneySuggesterTests.swift fixtures.
internal object JourneyTestKit {
    val zone: ZoneId = ZoneId.systemDefault()
    val t0: Instant =
        Instant.ofEpochSecond(1_760_000_000)
            .atZone(zone)
            .toLocalDate()
            .atStartOfDay(zone)
            .toInstant()
    val krd = GeoPoint(45.03, 38.98)
    val vld = GeoPoint(43.02, 44.68)
    val tbs = GeoPoint(41.72, 44.79)

    fun trip(
        day: Int,
        hour: Double,
        from: GeoPoint,
        to: GeoPoint,
        km: Double,
        hours: Double,
        region: String? = null,
    ): Trip {
        val start = t0.addingSeconds(day * 86_400.0 + hour * 3_600)
        val end = start.addingSeconds(hours * 3_600)
        return Trip(
            startDate = start,
            endDate = end,
            distance = km * 1_000,
            maxSpeed = 30.0,
            averageSpeed = 25.0,
            trackPoints =
                listOf(
                    TrackPoint(
                        latitude = from.latitude,
                        longitude = from.longitude,
                        timestamp = start,
                    ),
                    TrackPoint(latitude = to.latitude, longitude = to.longitude, timestamp = end),
                ),
            region = region,
        )
    }

    fun georgia(): List<Trip> =
        listOf(
            trip(0, 9.0, krd, vld, 480.0, 5.2, "Краснодарский край"),
            trip(1, 9.0, vld, tbs, 210.0, 4.8, "Северная Осетия"),
            trip(2, 11.0, tbs, GeoPoint(41.75, 44.80), 8.0, 0.4),
            trip(3, 12.0, tbs, GeoPoint(41.84, 44.72), 22.0, 0.7),
            trip(4, 9.0, tbs, vld, 210.0, 4.7),
            trip(5, 9.0, vld, krd, 480.0, 5.2),
        )
}
