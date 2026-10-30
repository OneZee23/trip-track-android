package com.onezee.triptrack.map.route

import com.onezee.triptrack.logic.speed
import com.onezee.triptrack.model.DistanceUnit
import kotlin.math.max

/**
 * iOS: SpeedColorScale.swift. The single source of the speed → colour scale every route line paints
 * with, so no two renderers can disagree about which colour a speed is.
 *
 * Thresholds (km/h): 0–50 green · 50–90 yellow · 90–110 orange · 110+ red. They PAINT and are never
 * printed, so they stay metric like every other threshold. A legend in miles would need a redrawn
 * scale (30/60/70), not these numbers divided by 1.609.
 */
object SpeedColorScale {
    /** One colour band; `upperKmh == null` is the open top. Colours are ARGB. */
    data class Band(val upperKmh: Double?, val argb: Int)

    val bands: List<Band> = listOf(
        Band(50.0, argb(0x2E, 0xAE, 0x50, 0.9)),
        Band(90.0, argb(0xF5, 0xBE, 0x1E, 0.9)),
        Band(110.0, argb(0xEB, 0x57, 0x1E, 0.9)),
        Band(null, argb(0xDC, 0x3C, 0x32, 0.9)),
    )

    /** Zone of a speed in m/s, by the `< upper` cutoffs of [bands]. Negative clamps to the slowest. */
    fun zone(speedMs: Double): Int {
        val kmh = DistanceUnit.km.speed(fromMetresPerSecond = max(0.0, speedMs))
        for ((i, band) in bands.withIndex()) {
            val upper = band.upperKmh
            if (upper != null && kmh < upper) return i
        }
        return bands.size - 1
    }

    fun argb(speedMs: Double): Int = bands[zone(speedMs)].argb

    internal fun argb(red: Int, green: Int, blue: Int, alpha: Double): Int =
        (Math.round(alpha * 255).toInt() shl 24) or (red shl 16) or (green shl 8) or blue
}
