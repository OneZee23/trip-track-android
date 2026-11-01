package com.onezee.triptrack.map.fog

import com.onezee.triptrack.logic.RevealedLayer
import com.onezee.triptrack.map.MapPoints
import com.onezee.triptrack.model.GeoPoint
import kotlin.math.abs
import kotlin.test.*
import org.junit.Test

// iOS: FogMesh.swift. MapLibre's repeated worlds require nearby segment endpoints before chunking.
class FogAntimeridianMeshTests {
    private fun mesh(vararg longitudes: Double): FogMeshLod {
        val run = longitudes.map { GeoPoint(0.0, it) }
        return FogMesh.build(RevealedLayer.empty.copy(fine = listOf(run))).fine
    }

    @Test fun eastboundCrossingRemainsATwoHundredMetreCorridor() {
        val mesh = mesh(179.999, -179.999)
        assertEquals(1, mesh.segmentCount)
        val dx = mesh.segments[2] - mesh.segments[0]
        assertEquals(MapPoints.WORLD * .002 / 360, dx.toDouble(), .01)
        assertTrue(dx > 0)
        assertTrue(mesh.maxX(0) - mesh.minX(0) < 2_000)
    }

    @Test fun westboundCrossingKeepsItsDirectionAndShortLength() {
        val mesh = mesh(-179.999, 179.999)
        val dx = mesh.segments[2] - mesh.segments[0]
        assertEquals(-MapPoints.WORLD * .002 / 360, dx.toDouble(), .01)
        assertTrue(mesh.maxX(0) - mesh.minX(0) < 2_000)
    }

    @Test fun repeatedCrossingsNeverGrowAGlobalSegment() {
        val mesh = mesh(-179.999, 179.999, -179.999, 179.999)
        assertEquals(3, mesh.segmentCount)
        for (segment in 0 until mesh.segmentCount) {
            assertTrue(abs(mesh.segments[4 * segment + 2] - mesh.segments[4 * segment]) < 2_000)
        }
    }
}
