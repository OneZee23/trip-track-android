package com.onezee.triptrack.fogspike.fog

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

// iOS: FogVeilRenderer.swift (street floor, halo points, lod), FogMetalVeil.featherRatio,
// FogMesh.chunkSize. Every candidate in the spike reads its numbers from here, and the native
// layer receives them once, so the three pictures differ only by how they are drawn.
object FogRules {
    // Measured on iOS with a prototype on a real street grid: at ±50 m two driven streets 195 m
    // apart keep a strip of fog between them; at ±75 m it closes.
    const val STREET_HALF_WIDTH_METRES = 50.0

    // Once metres stop being visible the corridor is 18 screen points per side, so a city reads
    // as a patch and a highway as a band.
    const val HALO_HALF_WIDTH_POINTS = 18.0

    // Feather width as a share of the half width, cut inward.
    const val FEATHER_RATIO = 0.4

    // Level of detail by screen points per map point (MKZoomScale).
    const val LOD_FINE_ABOVE = 1.5e-3
    const val LOD_MID_ABOVE = 1.5e-4

    // A 32768 map point square (about 5 km): Float32 offsets inside it are millimetre precise.
    const val CHUNK_SIZE = 32768.0

    // Map points across the world, as MKMapSize.world.
    const val WORLD = 268435456.0

    private const val EARTH_CIRCUMFERENCE = 40075016.68557849

    fun haloHalfWidthMetres(metresPerPoint: Double): Double =
        max(STREET_HALF_WIDTH_METRES, HALO_HALF_WIDTH_POINTS * metresPerPoint)

    /** 0 fine, 1 mid, 2 far. */
    fun lod(pointsPerMapPoint: Double): Int = when {
        pointsPerMapPoint > LOD_FINE_ABOVE -> 0
        pointsPerMapPoint > LOD_MID_ABOVE -> 1
        else -> 2
    }

    fun mapX(lon: Double): Double = (lon + 180.0) / 360.0 * WORLD

    fun mapY(lat: Double): Double {
        val s = sin(lat.coerceIn(-85.0511, 85.0511) * PI / 180.0)
        return (0.5 - ln((1 + s) / (1 - s)) / (4 * PI)) * WORLD
    }

    fun lonOf(x: Double): Double = x / WORLD * 360.0 - 180.0

    fun latOf(y: Double): Double {
        val n = PI - 2.0 * PI * y / WORLD
        return 180.0 / PI * atan(0.5 * (exp(n) - exp(-n)))
    }

    fun metresPerMapPoint(lat: Double): Double = cos(lat * PI / 180.0) * EARTH_CIRCUMFERENCE / WORLD

    /** MapLibre draws 512 dp tiles, so its zoom z shows 512 * 2^z dp across the world. */
    fun pointsPerMapPoint(maplibreZoom: Double): Double = 512.0 * 2.0.pow(maplibreZoom) / WORLD

    fun metresPerPoint(lat: Double, maplibreZoom: Double): Double =
        metresPerMapPoint(lat) / pointsPerMapPoint(maplibreZoom)
}
