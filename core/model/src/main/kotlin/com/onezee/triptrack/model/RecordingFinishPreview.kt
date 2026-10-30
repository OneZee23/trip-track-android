// iOS: RecordingFinishPreview.swift
package com.onezee.triptrack.model

import java.time.Instant
import java.util.UUID

// iOS: RecordingFinishPreview.swift
data class RecordingFinishPreview(
    val tripID: UUID,
    val endDate: Instant,
    val distance: Double,
    val duration: Double,
    val maxSpeed: Double,
)
