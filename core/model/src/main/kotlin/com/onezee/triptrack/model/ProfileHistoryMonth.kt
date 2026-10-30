// iOS: ProfileHistoryMonth.swift
package com.onezee.triptrack.model

import java.time.Instant

// iOS: ProfileHistoryMonth.swift
data class ProfileHistoryMonth(
    val id: Instant,
    val heading: String,
    val rows: List<HistoryRow>,
    val distance: Double,
    val tripCount: Int,
    val regionCount: Int,
)
