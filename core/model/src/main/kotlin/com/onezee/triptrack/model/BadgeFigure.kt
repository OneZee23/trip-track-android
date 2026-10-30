// iOS: BadgeFigure.swift
package com.onezee.triptrack.model

// iOS: BadgeFigure.swift
data class BadgeFigure(
    val kind: Kind,
    val value: Double,
    val atLeast: Boolean,
    val style: Measure.Style,
) {

    // iOS: BadgeFigure.swift
    enum class Kind {
        distance,
        speed,
        elevation,
    }
}
