package com.onezee.triptrack.map.label

import com.onezee.triptrack.logic.RegionLabelLOD
import com.onezee.triptrack.map.MapPoints
import com.onezee.triptrack.model.GeoBounds
import kotlin.math.min
import kotlin.test.*
import org.junit.Test

// iOS: RegionLabelAnnotation.swift, GeoBounds.minSidePt. The 140 pt decision uses the bundle's
// Mercator box, without rotating or replacing it with the revealed corridor's bounds.
class RegionLabelGeometryTests {
    @Test fun bundleBoxCrossesTheThresholdContinuously() {
        val bounds = GeoBounds(44.0, 46.5, 36.5, 41.0)
        val box = MapPoints.rect(bounds)
        val side = min(box.width, box.height)
        val atThreshold = MapPoints.mapLibreZoom(140.0 / side)
        assertEquals(140.0, side * MapPoints.pointsPerMapPoint(atThreshold), 1e-9)
        assertFalse(RegionLabelLOD.level(side * MapPoints.pointsPerMapPoint(atThreshold - 0.001)))
        assertTrue(RegionLabelLOD.level(side * MapPoints.pointsPerMapPoint(atThreshold + 0.001)))
    }

    @Test fun smallerProjectedSideDecides() {
        val wide = MapPoints.rect(GeoBounds(-1.0, 1.0, -10.0, 10.0))
        val narrow = MapPoints.rect(GeoBounds(-10.0, 10.0, -1.0, 1.0))
        assertEquals(wide.height, min(wide.width, wide.height))
        assertEquals(narrow.width, min(narrow.width, narrow.height))
    }
}
