// iOS: Measure.swift
package com.onezee.triptrack.model

// iOS: Measure.swift
object Measure {

    // iOS: Measure.swift
    data class Parts(
        val value: String,
        val unit: String,
    )

    // iOS: Measure.swift
    enum class Style {
        tenths,
        grouped,
        adaptive,
    }
}
