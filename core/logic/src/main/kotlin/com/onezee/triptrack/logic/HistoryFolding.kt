package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

// iOS: HistoryFolding.swift
val HistoryRow.id: UUID
    get() =
        when (this) {
            is HistoryRow.Trip -> value.id
            is HistoryRow.Journey -> value1.id
            is HistoryRow.Routine -> value.id
        }
val HistoryRow.date: Instant
    get() =
        when (this) {
            is HistoryRow.Trip -> value.startDate
            is HistoryRow.Journey -> legs.firstOrNull()?.startDate ?: value1.startDate
            is HistoryRow.Routine -> value.trips.first().startDate
        }

// iOS: ProfileRoutineTrips.swift
fun ProfileRoutineTrips.Companion.build(trips: List<Trip>): ProfileRoutineTrips {
    require(trips.isNotEmpty())
    return ProfileRoutineTrips(
        trips,
        trips.sumOf { it.distance },
        trips.asSequence().distinctBy { it.title ?: it.region ?: "" }.take(3).toList(),
    )
}

val ProfileRoutineTrips.id: UUID
    get() = trips.first().id

// iOS: HistoryFolding.swift
object HistoryFolding {
    // iOS: HistoryFolding.swift (journal return value).
    data class Journal(val pinned: List<Trip>, val rows: List<HistoryRow>)

    fun fold(
        trips: List<Trip>,
        journeys: List<Journey>,
        range: ClosedRange<Instant>? = null,
    ): List<HistoryRow> {
        val rows = mutableListOf<HistoryRow>()
        val taken = mutableSetOf<UUID>()
        for (journey in journeys) {
            val legs =
                trips
                    .filter { journey.contains(it) && it.id !in taken }
                    .sortedByDescending { it.startDate }
            if (
                legs.isEmpty() &&
                    range != null &&
                    !(journey.startDate < range.endInclusive &&
                        (journey.endDate ?: Instant.MAX) >= range.start)
            )
                continue
            taken.addAll(legs.map { it.id })
            rows += HistoryRow.Journey(journey, legs)
        }
        rows += trips.filter { it.id !in taken }.map { HistoryRow.Trip(it) }
        return rows.sortedByDescending { it.date }
    }

    fun journal(
        trips: List<Trip>,
        journeys: List<Journey>,
        pinnedTripIds: List<UUID>,
        range: ClosedRange<Instant>? = null,
        zone: ZoneId = ZoneId.systemDefault(),
        foldsRoutine: Boolean = true,
        keepsEmptyJourneys: Boolean = true,
    ): Journal {
        val byId = trips.groupBy { it.id }.mapValues { it.value.first() }
        val taken = mutableSetOf<UUID>()
        val pinned = pinnedTripIds.take(3).mapNotNull { id -> byId[id]?.takeIf { taken.add(id) } }
        val candidates = trips.filter { it.id !in taken }
        val folded =
            fold(candidates, journeys, range).filter {
                it !is HistoryRow.Journey || keepsEmptyJourneys || it.legs.isNotEmpty()
            }
        val routine =
            if (foldsRoutine) routineTripIds(trips, journeys, pinnedTripIds.toSet(), zone)
            else emptySet()
        val groups =
            candidates
                .filter { it.id in routine }
                .groupBy { monthStart(it.startDate, zone) }
                .mapValues { (_, trips) -> trips.sortedByDescending { it.startDate } }
        val inserted = mutableSetOf<Instant>()
        val rows = mutableListOf<HistoryRow>()
        for (row in folded) {
            if (row is HistoryRow.Trip && row.value.id in routine) {
                val month = monthStart(row.value.startDate, zone)
                if (inserted.add(month))
                    groups[month]?.let { rows += HistoryRow.Routine(ProfileRoutineTrips.build(it)) }
            } else rows += row
        }
        return Journal(pinned, rows)
    }

    fun routineTripIds(
        trips: List<Trip>,
        journeys: List<Journey> = emptyList(),
        pinnedTripIds: Set<UUID> = emptySet(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Set<UUID> {
        // iOS: HistoryFolding.swift (routineTripIds.Route).
        data class Route(val month: Instant, val a: String, val b: String)
        val groups = mutableMapOf<Route, MutableList<UUID>>()
        for (trip in trips) {
            if (
                trip.isDraft ||
                    trip.endDate == null ||
                    trip.photos.isNotEmpty() ||
                    trip.checkpoints.isNotEmpty() ||
                    trip.tripDescription
                        .orEmpty()
                        .trim { it.isFoundationWhitespace(true) }
                        .isNotEmpty() ||
                    trip.id in pinnedTripIds ||
                    journeys.any { it.contains(trip) }
            )
                continue
            val preview = trip.previewPolyline ?: continue
            val points = Trip.decodePolyline(preview)
            if (points.size < 2 || !points.first().isValid || !points.last().isValid) continue
            val start = GeohashEncoder.encode(points.first().latitude, points.first().longitude, 6)
            val end = GeohashEncoder.encode(points.last().latitude, points.last().longitude, 6)
            val key = Route(monthStart(trip.startDate, zone), minOf(start, end), maxOf(start, end))
            groups.getOrPut(key) { mutableListOf() }.add(trip.id)
        }
        return groups.values.filter { it.size >= 3 }.flatten().toSet()
    }

    fun dayRange(
        from: Instant?,
        to: Instant?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ClosedRange<Instant>? {
        if (from == null) return null
        val start = from.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        val end = (to ?: from).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        // The upper bound is exclusive when testing a journey intersection, as on iOS.
        return start..maxOf(start, end)
    }

    fun runs(rows: List<HistoryRow>): List<List<HistoryRow>> {
        val runs = mutableListOf<MutableList<HistoryRow>>()
        for (row in rows) {
            val last = runs.lastOrNull()
            if (row is HistoryRow.Trip && last?.firstOrNull() is HistoryRow.Trip) last.add(row)
            else runs += mutableListOf(row)
        }
        return runs
    }
}

internal fun monthStart(date: Instant, zone: ZoneId): Instant =
    date.atZone(zone).toLocalDate().withDayOfMonth(1).atStartOfDay(zone).toInstant()
