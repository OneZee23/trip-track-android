package com.onezee.triptrack.logic

import com.onezee.triptrack.model.TrackPoint
import com.onezee.triptrack.model.Trip
import kotlin.test.*

// iOS: RecordingBreaksPersistenceTests.swift (pure boundary/annotation rules).
class RecordingBreaksTests {
    private val start = java.time.Instant.ofEpochSecond(1_700_000_000)
    @Test fun testBoundaryIncludesItsTimestampAndSurvivesAccuracyFiltering() {
        val boundary = start.plusSeconds(10)
        val points = listOf(TrackPoint(latitude = 45.0, longitude = 39.0, timestamp = start),
            TrackPoint(latitude = 45.0, longitude = 40.0, horizontalAccuracy = 100.0, timestamp = boundary),
            TrackPoint(latitude = 45.0, longitude = 40.01, timestamp = boundary.plusSeconds(1)))
        val annotated = RecordingBreaks.annotate(points, listOf(boundary, boundary))
        assertEquals(listOf(0, 1, 1), annotated.map { it.recordingSegmentIndex })
        assertEquals(listOf(0, 1), annotated.filter { it.countsForDistance }.map { it.recordingSegmentIndex })
        assertTrue(RecordingBreaks.crosses(start, boundary, listOf(boundary)))
        assertFalse(RecordingBreaks.crosses(boundary, boundary.plusSeconds(1), listOf(boundary)))
        assertEquals(listOf(0, 0, 0), points.map { it.recordingSegmentIndex })
    }
    @Test fun normalizationSortsAndDeduplicatesAndBinarySearchUsesUpperBound() {
        val breaks = listOf(start.plusSeconds(20), start, start.plusSeconds(10), start)
        assertEquals(listOf(start, start.plusSeconds(10), start.plusSeconds(20)), RecordingBreaks.normalized(breaks))
        assertEquals(0, RecordingBreaks.segmentIndex(start.minusNanos(1), breaks))
        assertEquals(1, RecordingBreaks.segmentIndex(start, breaks))
        assertEquals(2, RecordingBreaks.segmentIndex(start.plusSeconds(10), breaks))
        assertEquals(3, RecordingBreaks.segmentIndex(start.plusSeconds(20), breaks))
        assertEquals(0, RecordingBreaks.segmentIndex(start, emptyList()))
    }
    @Test fun tripBoundaryAppliesNormalizationAndRebuildsDerivedIndices() {
        val point = TrackPoint(latitude = 45.0, longitude = 39.0, timestamp = start, recordingSegmentIndex = 7)
        val trip = Trip(trackPoints = listOf(point)).withRecordingBreaks(listOf(start, start))
        assertEquals(listOf(start), trip.recordingBreaks)
        assertEquals(1, trip.trackPoints.single().recordingSegmentIndex)
        val cleared = trip.withRecordingBreaks(emptyList())
        assertEquals(0, cleared.trackPoints.single().recordingSegmentIndex)
        assertEquals(1, trip.trackPoints.single().recordingSegmentIndex)
    }
}
