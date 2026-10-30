// iOS: HistoryFolding.swift
package com.onezee.triptrack.model

// iOS: HistoryFolding.swift
sealed interface HistoryRow {
    data class Trip(val value: com.onezee.triptrack.model.Trip) : HistoryRow
    data class Journey(val value1: com.onezee.triptrack.model.Journey, val legs: List<com.onezee.triptrack.model.Trip>) : HistoryRow
    data class Routine(val value: ProfileRoutineTrips) : HistoryRow
}
