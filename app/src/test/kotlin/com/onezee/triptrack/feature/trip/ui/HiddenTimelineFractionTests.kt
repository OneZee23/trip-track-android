package com.onezee.triptrack.feature.trip.ui

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** The editor bar places colours, hatch and handles by time, like iOS `HiddenTimelineAxis`. */
class HiddenTimelineFractionTests {
    private val first = Instant.ofEpochSecond(1_800_000_080)
    private val last = Instant.ofEpochSecond(1_800_000_530)

    @Test
    fun edgesOfTheRecordingAreTheEdgesOfTheBar() {
        assertEquals(0f, hiddenTimelineFraction(first, first, last), 0f)
        assertEquals(1f, hiddenTimelineFraction(last, first, last), 0f)
    }

    @Test
    fun aPointSitsWhereItsTimeIsNotWhereItsNumberIs() {
        // Sixty dense points in the first minute of seven and a half.
        assertEquals(60f / 450f, hiddenTimelineFraction(first.plusSeconds(60), first, last), 0.0001f)
    }

    @Test
    fun timesOutsideTheRecordingAreClampedAndAnEmptySpanIsZero() {
        assertEquals(0f, hiddenTimelineFraction(first.minusSeconds(80), first, last), 0f)
        assertEquals(1f, hiddenTimelineFraction(last.plusSeconds(30), first, last), 0f)
        assertEquals(0f, hiddenTimelineFraction(first, first, first), 0f)
    }
}
