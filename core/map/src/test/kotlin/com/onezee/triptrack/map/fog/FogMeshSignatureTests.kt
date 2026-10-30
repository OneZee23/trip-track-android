package com.onezee.triptrack.map.fog

import com.onezee.triptrack.logic.RevealedLayer
import com.onezee.triptrack.model.GeoPoint
import kotlin.test.*
import org.junit.Test

// iOS: FogMeshSignatureTests.swift. The signature is how FogLayer.setRevealed tells "this world is
// already built" from "build it". There are three levels and three sets of GPU buffers: keyed by
// `fine` alone, a layer whose `mid` and `far` changed would never reach the GPU, and the country zoom
// would show yesterday's fog.
class FogMeshSignatureTests {
    // The same layer keeps its signature: otherwise every repeated setRevealed would rebuild hundreds
    // of thousands of segments.
    @Test
    fun testTheSameLayerKeepsItsSignature() {
        val world = layer(fine = listOf(line(0)), mid = listOf(line(1)), far = listOf(line(2)))
        assertEquals(FogMeshSignature.of(world), FogMeshSignature.of(world))
    }

    // Only the coarse level changed: the signature must change.
    @Test
    fun testACoarseOnlyChangeChangesTheSignature() {
        val fine = line(0)
        val mid = line(1)
        val before = layer(fine = listOf(fine), mid = listOf(mid), far = listOf(line(2)))
        val after = layer(fine = listOf(fine), mid = listOf(mid), far = listOf(line(3)))
        assertNotEquals(FogMeshSignature.of(before), FogMeshSignature.of(after), "the country would show yesterday's fog")
    }

    // An empty fine with a non-empty far is a legal layer and its signature is not empty. Keyed by
    // fine alone, two such layers would be the same, and the second would never be built.
    @Test
    fun testAnEmptyFineLodStillCarriesASignature() {
        val sparse = layer(fine = emptyList(), mid = emptyList(), far = listOf(line(4)))
        assertEquals(1, FogMeshSignature.of(sparse).count)
        assertNotEquals(
            FogMeshSignature.of(sparse),
            FogMeshSignature.of(layer(fine = emptyList(), mid = emptyList(), far = listOf(line(5)))),
        )
    }

    // The signature covers all three levels; the count shows it most directly.
    @Test
    fun testSignatureCoversEveryLod() {
        val world = layer(fine = listOf(line(0), line(1)), mid = listOf(line(2)), far = listOf(line(3)))
        assertEquals(4, FogMeshSignature.of(world).count)
    }

    // Identity, not content: iOS keys by ObjectIdentifier, so a rebuilt layer with equal points is a
    // new layer (it came from new storage reads), while the same objects are the same layer.
    @Test
    fun equalPointsInNewListsAreANewSignature() {
        val a = layer(fine = listOf(line(7)), mid = emptyList(), far = emptyList())
        val b = layer(fine = listOf(line(7)), mid = emptyList(), far = emptyList())
        assertEquals(a.fine, b.fine)
        assertNotEquals(FogMeshSignature.of(a), FogMeshSignature.of(b))
    }

    // Every call gives a NEW list: the signature looks at identity, the geography is irrelevant.
    private fun line(seed: Int): List<GeoPoint> =
        listOf(GeoPoint(45.0 + seed * 1e-4, 38.0), GeoPoint(45.001 + seed * 1e-4, 38.001))

    private fun layer(fine: List<List<GeoPoint>>, mid: List<List<GeoPoint>>, far: List<List<GeoPoint>>) =
        RevealedLayer(fine, mid, far, cellCount = fine.size + mid.size + far.size, openedKm = 0.0, regionIds = emptySet())
}
