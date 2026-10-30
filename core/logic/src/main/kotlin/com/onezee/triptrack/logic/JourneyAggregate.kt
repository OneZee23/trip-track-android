package com.onezee.triptrack.logic

import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.JourneyAggregate
import com.onezee.triptrack.model.Trip
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// iOS: JourneyAggregate.swift
val JourneyAggregate.Companion.localRadius: Double
    get() = 30_000.0

fun JourneyAggregate.Companion.startCoordinate(trip: Trip): GeoPoint? =
    trip.trackPoints.firstOrNull()?.coordinate ?: trip.previewCoordinates.firstOrNull()

fun JourneyAggregate.Companion.endCoordinate(trip: Trip): GeoPoint? =
    trip.trackPoints.lastOrNull()?.coordinate ?: trip.previewCoordinates.lastOrNull()

fun JourneyAggregate.Companion.build(
    trips: List<Trip>,
    zone: ZoneId = ZoneId.systemDefault(),
): JourneyAggregate {
    val ordered = trips.sortedBy { it.startDate }
    val first =
        ordered.firstOrNull() ?: return JourneyAggregate(emptyList(), 0, 0.0, 0.0, 0, emptyList())
    val day0 = first.startDate.atZone(zone).toLocalDate()
    val days = mutableListOf<JourneyAggregate.Day>()
    var anchor = endCoordinate(first)
    var legs = 0
    var farthest: GeoPoint? = null
    var farthestDistance = -1.0
    val origin = startCoordinate(first)
    fun append(item: JourneyAggregate.Item, number: Int, trip: Trip) {
        val last = days.lastOrNull()
        if (last?.number == number) last.items = last.items + item
        else days += JourneyAggregate.Day(number, trip.startDate, listOf(item))
    }
    for (trip in ordered) {
        val number =
            ChronoUnit.DAYS.between(day0, trip.startDate.atZone(zone).toLocalDate()).toInt() + 1
        val start = startCoordinate(trip)
        val end = endCoordinate(trip)
        val stay = anchor
        val local =
            stay != null &&
                start != null &&
                end != null &&
                start.distanceFrom(stay) <= localRadius &&
                end.distanceFrom(stay) <= localRadius
        if (local) {
            val last = days.lastOrNull()
            val item = last?.items?.lastOrNull() as? JourneyAggregate.Item.Local
            if (last != null && item != null) {
                last.items =
                    last.items.dropLast(1) +
                        item.copy(value1 = item.value1 + trip, lastDayNumber = number)
            } else append(JourneyAggregate.Item.Local(listOf(trip), stay, number), number, trip)
        } else {
            legs++
            append(JourneyAggregate.Item.Leg(trip), number, trip)
            anchor = end ?: anchor
            if (origin != null && end != null) {
                val distance = origin.distanceFrom(end)
                if (distance > farthestDistance) {
                    farthest = end
                    farthestDistance = distance
                }
            }
        }
    }
    val last = ordered.last()
    return JourneyAggregate(
        days,
        legs,
        ordered.sumOf { it.distance },
        ordered.sumOf { it.duration },
        ChronoUnit.DAYS.between(day0, (last.endDate ?: last.startDate).atZone(zone).toLocalDate())
            .toInt() + 1,
        ordered.mapNotNull { it.region }.distinct(),
        origin,
        farthest,
    )
}

val JourneyAggregate.localTripCount: Int
    get() =
        days.sumOf { day ->
            day.items.sumOf { (it as? JourneyAggregate.Item.Local)?.value1?.size ?: 0 }
        }

fun JourneyAggregate.defaultTitle(startName: String?, farthestName: String?): String? =
    if (startName.isNullOrEmpty() || farthestName.isNullOrEmpty() || startName == farthestName)
        startName ?: farthestName
    else "$startName — $farthestName"
