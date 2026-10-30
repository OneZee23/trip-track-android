package com.onezee.triptrack.logic

// iOS: Support/SyntheticDrives.swift
internal object SyntheticDrives {
    fun cityEvening(seconds: Int = 900, side: Double = 800.0): List<LocationFix> = (0..seconds).map { t ->
        val (east, north, course) = gridPosition(t * 10.0, side)
        val bad = t % 70 >= 40
        val wobble = ((t * 53) % 21 - 10).toDouble() * if (bad) 1.5 else 0.5
        TrackTestKit.fix(east + wobble, north - wobble, 10.0, course, t.toDouble(), if (bad) 90.0 else 45.0)
    }

    fun trueDistance(seconds: Int = 900): Double = seconds * 10.0

    fun tunnel(): List<LocationFix> = (0..300).filter { it !in 120..<180 }.map { t ->
        TrackTestKit.fix(north = t * 20.0, speed = 20.0, after = t.toDouble(), accuracy = 8.0)
    }

    fun suspension(): List<LocationFix> = (0..900).filter { it !in 300..<720 }.map { t ->
        TrackTestKit.fix(north = t * 16.0, speed = 16.0, after = t.toDouble(), accuracy = 12.0)
    }

    private fun gridPosition(metres: Double, side: Double): Triple<Double, Double, Double> {
        val d = metres % (side * 4)
        return when {
            d < side -> Triple(d, 0.0, 90.0)
            d < 2 * side -> Triple(side, d - side, 0.0)
            d < 3 * side -> Triple(side - (d - 2 * side), side, 270.0)
            else -> Triple(0.0, side - (d - 3 * side), 180.0)
        }
    }
}
