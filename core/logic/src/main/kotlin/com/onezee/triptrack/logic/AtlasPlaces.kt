package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.Instant
import java.time.ZoneId

// iOS: AtlasPresentation.swift. Foundation DateInterval.contains includes its end.
fun AtlasPeriod.interval(
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): ClosedRange<Instant>? {
    val today = now.atZone(zone).toLocalDate()
    return when (this) {
        AtlasPeriod.AllTime -> null
        AtlasPeriod.ThisYear ->
            today.withDayOfYear(1).atStartOfDay(zone).toInstant()..today
                    .withDayOfYear(1)
                    .plusYears(1)
                    .atStartOfDay(zone)
                    .toInstant()
        AtlasPeriod.Last30Days ->
            today.minusDays(29).atStartOfDay(zone).toInstant()..today
                    .plusDays(1)
                    .atStartOfDay(zone)
                    .toInstant()
        is AtlasPeriod.Custom ->
            minOf(start, end).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()..maxOf(
                        start,
                        end,
                    )
                    .atZone(zone)
                    .toLocalDate()
                    .plusDays(1)
                    .atStartOfDay(zone)
                    .toInstant()
    }
}

fun AtlasPeriod.contains(
    date: Instant,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): Boolean = interval(now, zone)?.let { date >= it.start && date < it.endInclusive } ?: true

// iOS: AtlasPlaces.swift
fun AtlasPlaces.build(
    seeds: List<AtlasPlaces.Seed>,
    period: AtlasPeriod,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<AtlasPlacePin> {
    val window = period.interval(now, zone)
    return seeds
        .map { seed ->
            val inside =
                if (window == null) seed.passDates else seed.passDates.filter { it in window }
            val inPeriod = window == null || inside.isNotEmpty()
            AtlasPlacePin(
                seed.id,
                seed.coordinate,
                seed.name,
                (if (inPeriod) inside else seed.passDates).maxOrNull(),
                seed.passDates.size,
                seed.usual,
                inPeriod,
            )
        }
        .sortedWith(compareByDescending { it.lastAt ?: Instant.MIN })
}

fun AtlasPlaces.inPeriodCount(pins: List<AtlasPlacePin>): Int = pins.count { it.inPeriod }

// iOS: AtlasHome.swift
object AtlasHome {
    const val radius = 150.0

    fun tripsStarted(home: GeoPoint, starts: List<GeoPoint>, radius: Double = this.radius): Int =
        starts.count { GeometryUtils.haversineDistance(it, home) <= radius }
}
