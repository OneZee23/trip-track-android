// iOS: PreparedProfileHistory.swift
package com.onezee.triptrack.model

import java.util.UUID

// iOS: PreparedProfileHistory.swift
data class ProfileHistoryLibrary(
    val trips: List<Trip>,
    val historicalLevels: Map<UUID, Int>? = null,
)

// iOS: PreparedProfileHistory.swift
data class PreparedProfileHistory(
    val visibleTrips: List<Trip>,
    val rows: List<HistoryRow>,
    val gridRuns: List<List<HistoryRow>>,
    val historicalLevels: Map<UUID, Int>? = null,
    val pinned: List<Trip>,
    val chapters: List<ProfileHistoryMonth>,
) {
    companion object {}
}
