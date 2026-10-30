package com.onezee.triptrack.map.fog

import com.onezee.triptrack.logic.FogCellGrid
import com.onezee.triptrack.logic.FogVeilRenderer
import com.onezee.triptrack.logic.RevealedLayer

// iOS: FogMetalVeil.featherRatio, FogMesh.chunkSize, FogVeilRenderer.lod thresholds,
// VeilRevealMask.solidFraction, FogVeilRenderer.revealMetres. The corridor and cell numbers come from
// core:logic, where FogVeilRenderer and FogCellGrid are ported; the native layer receives all of them
// once (`nativeRules`), so the picture has one source of numbers.
object FogRules {
    // Feather width as a share of the half width, cut INWARD: half alpha lies at hw − feather / 2.
    const val FEATHER_RATIO = 0.4

    // FogVeilRenderer.lod in screen points per map point (MKZoomScale). core:logic writes them
    // inline; FogRulesTests checks both sides of each against it.
    const val LOD_FINE_ABOVE = 1.5e-3
    const val LOD_MID_ABOVE = 1.5e-4

    // A 32,768 map point square (about 5 km at the equator): Float32 offsets inside it are
    // millimetre precise, and culling throws away almost the whole world.
    const val CHUNK_SIZE = 32_768.0

    // Android only: the mid and far levels use 2^20 map point chunks (about 160 km). On GL every chunk
    // costs a uniform, an attribute pointer and a draw call. The fog spike's stress library had about
    // 900 chunks per level, which took 9-11 ms of CPU per zoomed-out frame on the emulator; nine
    // coarse chunks brought it to 0.53-0.56 ms. Float32 inside such a chunk is precise to 0.125 map
    // point, under a thousandth of a screen point at the zooms of these levels.
    const val COARSE_CHUNK_SIZE = 1_048_576.0

    // The hole around the car: open in full up to this share of its radius, then a linear fade.
    const val REVEAL_SOLID_FRACTION = 0.55

    // Radius of that hole at full reveal (iOS: FogVeilRenderer.revealMetres).
    const val REVEAL_METRES = 150.0

    fun chunkSize(lod: RevealedLayer.LOD): Double =
        if (lod == RevealedLayer.LOD.fine) CHUNK_SIZE else COARSE_CHUNK_SIZE

    /** Half width of the corridor in screen points, by FogVeilRenderer.haloHalfWidth. */
    fun halfWidthPoints(metresPerPoint: Double): Double =
        FogVeilRenderer.haloHalfWidth(metresPerPoint) / metresPerPoint

    /** The order fog_state.hpp's `Rules` reads them in. */
    internal fun nativeRules(): DoubleArray = doubleArrayOf(
        FogVeilRenderer.streetHalfWidthMetres,
        FogVeilRenderer.haloHalfWidthPoints,
        FEATHER_RATIO,
        LOD_FINE_ABOVE,
        LOD_MID_ABOVE,
        FogCellGrid.baseMapPoints,
        FogCellGrid.minScreenPoints,
        FogCellGrid.maxDoublings.toDouble(),
        REVEAL_SOLID_FRACTION,
    )
}
