package com.onezee.triptrack.logic

import com.onezee.triptrack.model.Journey
import com.onezee.triptrack.model.Trip
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

// iOS: Journey.swift
fun Journey.contains(trip: Trip): Boolean = contains(trip.id, trip.startDate)

fun Journey.contains(tripId: UUID, startDate: Instant): Boolean =
    tripId !in excludedTripIds &&
        startDate >= this.startDate &&
        (endDate?.let { startDate <= it } ?: true)

// iOS: PublicJourneyAdapter.swift (JourneyWindow), JourneyEditSheet.swift (pure date rules).
object JourneyWindow {
    // iOS: JourneyEditSheet.swift (clampedWindow return value).
    data class Window(val start: Instant, val end: Instant)

    fun days(
        startDate: Instant,
        endDate: Instant?,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int =
        ChronoUnit.DAYS.between(
                startDate.atZone(zone).toLocalDate(),
                maxOf(endDate ?: now, startDate).atZone(zone).toLocalDate(),
            )
            .toInt() + 1

    fun clampedWindow(
        start: Instant,
        end: Instant?,
        now: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Window {
        val a = notFuture(start, now, zone)
        val b = notFuture(end ?: now, now, zone)
        return Window(minOf(a, b), maxOf(a, b))
    }

    fun notFuture(date: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault()): Instant =
        if (date.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()) date
        else minOf(date, now)

    fun endOfDay(date: Instant, zone: ZoneId = ZoneId.systemDefault()): Instant =
        date.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().minusSeconds(1)

    fun startBounds(start: Instant, end: Instant, now: Instant): ClosedRange<Instant> =
        Instant.MIN..maxOf(start, minOf(end, now))

    fun endBounds(start: Instant, end: Instant, now: Instant): ClosedRange<Instant> =
        minOf(start, end)..maxOf(end, now)

    fun datesMoved(
        start: Instant,
        end: Instant,
        opened: Window,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean =
        minOf(start, end).atZone(zone).toLocalDate() != opened.start.atZone(zone).toLocalDate() ||
            maxOf(start, end).atZone(zone).toLocalDate() != opened.end.atZone(zone).toLocalDate()
}
