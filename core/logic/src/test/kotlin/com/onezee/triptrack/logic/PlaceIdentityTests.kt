package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.Instant
import java.util.UUID
import kotlin.test.*

// iOS: PlaceIdentityTests.swift. UUID implementation and its frozen vectors already live in core:model.
class PlaceIdentityTests {
    @Test fun testCellIsGeohash7() {
        assertEquals("szgs0u4", Place.cell(44.3196, 38.7089))
        assertEquals("szgs0u4", Place.cell(44.3201, 38.7089))
        assertEquals("szgs0uf", Place.cell(44.3232, 38.7089))
    }
    @Test fun testPassKnowsWhetherItHasACourse() {
        fun pass(course: Double) = PlacePass(placeId = UUID.randomUUID(), tripId = UUID.randomUUID(), timestamp = Instant.now(),
            elapsedFromStart = 1.0, distanceFromStart = 1.0, course = course)
        assertTrue(pass(271.0).hasCourse)
        assertFalse(pass(PlacePass.unknownCourse).hasCourse)
    }
}

// iOS: GeohashEncoder.swift. Additional vectors obtained by executing the Swift source.
class GeohashEncoderTests {
    @Test fun frozenBoundsCentersAndNeighbours() {
        val box = GeohashEncoder.decode("szgs0u4")
        assertEquals(44.31884765625, box.minLat)
        assertEquals(44.320220947265625, box.maxLat)
        assertEquals(38.70758056640625, box.minLon)
        assertEquals(38.708953857421875, box.maxLon)
        assertEquals(listOf("szgs0gc", "szgs0gf", "szgs0gg", "szgs0u1", "szgs0u5", "szgs0u3", "szgs0u6", "szgs0u7"),
            GeohashEncoder.neighbors("szgs0u4"))
        val center = GeohashEncoder.centerCoordinate("szgs0u4")
        assertEquals("szgs0u4", GeohashEncoder.encode(center.latitude, center.longitude, 7))
        assertEquals(GeohashEncoder.decode("szgs0u4"), GeohashEncoder.decode("SZ!GS0U4"))
    }
    @Test fun poleAndAntimeridianNeighboursMatchSwiftClamping() {
        assertEquals(listOf("zzzzzzw", "zzzzzzx", "zzzzzzx", "zzzzzzy", "zzzzzzz", "zzzzzzy", "zzzzzzz", "zzzzzzz"),
            GeohashEncoder.neighbors("zzzzzzz"))
        assertEquals(listOf("0000000", "0000000", "0000001", "0000000", "0000001", "0000002", "0000002", "0000003"),
            GeohashEncoder.neighbors("0000000"))
        assertEquals(8, GeohashEncoder.neighbors("").size)
        assertTrue(GeohashEncoder.neighbors("").all { it.isEmpty() })
        assertEquals(GeoPoint(0.0, 0.0), GeohashEncoder.centerCoordinate(""))
        assertEquals("", GeohashEncoder.encode(0.0, 0.0, 0))
        assertEquals("s00000", GeohashEncoder.encode(0.0, 0.0))
    }
}
