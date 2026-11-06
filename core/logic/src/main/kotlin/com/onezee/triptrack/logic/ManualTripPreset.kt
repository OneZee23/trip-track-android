// iOS: ManualTripPreset.swift
package com.onezee.triptrack.logic

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class ManualTripPreset(
    val from: ManualTripSuggestionPoint? = null,
    val to: ManualTripSuggestionPoint? = null,
    val vehicleId: UUID? = null,
    val startDate: Instant? = null,
    val durationSeconds: Double? = null,
) {
    companion object {
        fun forDay(day: LocalDate, now: Instant, zone: ZoneId) = ManualTripPreset(
            startDate = ManualTripRules.defaultStart(day, now, zone),
        )

        fun returnTrip(
            from: ManualTripSuggestionPoint,
            to: ManualTripSuggestionPoint,
            vehicleId: UUID?,
            firstTripEnd: Instant,
            now: Instant,
        ) = ManualTripPreset(
            from = to,
            to = from,
            vehicleId = vehicleId,
            startDate = minOf(firstTripEnd.plusSeconds(3600), now),
        )
    }
}
