package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

// iOS: Trip.swift
val Trip.duration: Double
    get() = (endDate ?: Instant.now()).secondsSince(startDate)

// iOS: TripHistoryFilter.swift. The presentation adapter supplies translated region names.
object TripHistoryFilter {
    fun apply(
        trips: List<Trip>,
        query: String,
        from: Instant?,
        to: Instant?,
        locale: Locale,
        zone: ZoneId = ZoneId.systemDefault(),
        localizedRegion: (String?) -> String? = { it },
    ): List<Trip> {
        val words = query.split(Regex("(?U)\\s+")).filter { it.isNotEmpty() }
        if (from == null && words.isEmpty()) return trips
        val start = from?.atZone(zone)?.toLocalDate()
        val end = (to ?: from)?.atZone(zone)?.toLocalDate()
        fun folded(value: String): String =
            Normalizer.normalize(value.lowercase(locale), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
        return trips.filter { trip ->
            val day = trip.startDate.atZone(zone).toLocalDate()
            val inDates =
                start == null || end == null || day in minOf(start, end)..maxOf(start, end)
            if (!inDates || words.isEmpty()) return@filter inDates
            val text =
                folded(
                    listOfNotNull(trip.title, trip.region, localizedRegion(trip.region))
                        .joinToString(" ")
                )
            inDates && words.all { folded(it) in text }
        }
    }
}

// iOS: ProfileHistoryMonth.swift
fun ProfileHistoryMonth.title(
    locale: Locale,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): String = profileMonthTitle(id, locale, now, zone)

private fun profileMonthTitle(date: Instant, locale: Locale, now: Instant, zone: ZoneId): String {
    val pattern = if (date.atZone(zone).year == now.atZone(zone).year) "LLLL" else "LLLL yyyy"
    return DateTimeFormatter.ofPattern(pattern, locale)
        .withZone(zone)
        .format(date)
        .replaceFirstChar { it.titlecase(locale) }
}

fun ProfileHistoryMonth.Companion.build(
    rows: List<HistoryRow>,
    trips: List<Trip>,
    locale: Locale,
    zone: ZoneId = ZoneId.systemDefault(),
    now: Instant = Instant.now(),
): List<ProfileHistoryMonth> {
    val originals = trips.groupBy { monthStart(it.startDate, zone) }
    val groups =
        rows.groupBy {
            monthStart(if (it is HistoryRow.Journey) it.value1.startDate else it.date, zone)
        }
    return groups.keys.sortedDescending().map { month ->
        val source = originals[month].orEmpty()
        ProfileHistoryMonth(
            month,
            profileMonthTitle(month, locale, now, zone),
            groups.getValue(month),
            source.sumOf { it.distance },
            source.size,
            source.mapNotNull { it.region?.takeIf(String::isNotEmpty) }.toSet().size,
        )
    }
}

// iOS: PreparedProfileHistory.swift. Historical levels are supplied by the library's award layer.
fun PreparedProfileHistory.Companion.build(
    library: ProfileHistoryLibrary,
    journeys: List<Journey>,
    query: String,
    from: Instant?,
    to: Instant?,
    locale: Locale,
    zone: ZoneId = ZoneId.systemDefault(),
    pinnedTripIds: List<UUID> = emptyList(),
    foldsRoutine: Boolean = true,
    keepsEmptyJourneys: Boolean = true,
    foldsJourneysInGrid: Boolean = true,
    localizedRegion: (String?) -> String? = { it },
    now: Instant = Instant.now(),
): PreparedProfileHistory {
    val visible =
        TripHistoryFilter.apply(library.trips, query, from, to, locale, zone, localizedRegion)
    val searching = query.trim { it.isFoundationWhitespace(true) }.isNotEmpty()
    val range = HistoryFolding.dayRange(from, to, zone)
    val journal =
        HistoryFolding.journal(
            visible,
            if (searching) emptyList() else journeys,
            pinnedTripIds,
            range,
            zone,
            foldsRoutine && !searching,
            keepsEmptyJourneys,
        )
    val grid =
        HistoryFolding.journal(
            visible,
            if (foldsJourneysInGrid && !searching) journeys else emptyList(),
            pinnedTripIds,
            range,
            zone,
            false,
            keepsEmptyJourneys,
        )
    return PreparedProfileHistory(
        visible,
        journal.rows,
        HistoryFolding.runs(grid.rows),
        library.historicalLevels,
        journal.pinned,
        ProfileHistoryMonth.build(journal.rows, visible, locale, zone, now),
    )
}
