package com.onezee.triptrack.data

import com.onezee.triptrack.logic.TripHiddenPartsResolver
import com.onezee.triptrack.model.HiddenSection
import com.onezee.triptrack.model.TrackPoint
import com.onezee.triptrack.model.TripHiddenParts
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A middle section ends exactly at a track point. Build 15 stored its edges as doubles that came back
 * 18 ns early, and after a save that last point was no longer hidden (POCO, 5 Nov 2026).
 */
class HiddenPartsColumnTests {
    private val points = (0 until 12).map { index ->
        TrackPoint(latitude = 45.0 + index * 0.003, longitude = 39.0, speed = 15.0, horizontalAccuracy = 5.0,
            timestamp = Instant.parse("2026-11-02T17:05:22.594Z").plusSeconds(index * 40L))
    }

    @Test
    fun sectionEdgesSurviveTheColumnAndStillHideTheirPoints() {
        val parts = TripHiddenParts(sections = listOf(HiddenSection(UUID.randomUUID(), points[3].timestamp, points[8].timestamp)))
        val stored = JsonColumns.decodeHiddenParts(JsonColumns.encodeHiddenParts(parts))
        assertEquals(parts, stored)
        val before = TripHiddenPartsResolver.resolve(points, emptyList(), 0, 0, parts.sections, null).hidden
        val after = TripHiddenPartsResolver.resolve(points, emptyList(), 0, 0, stored!!.sections, null).hidden
        assertEquals(before, after)
        assertEquals((3..8).toList(), after.indices.filter { after[it] })
    }
}
