// iOS: TipMoment.swift
package com.onezee.triptrack.model

import java.time.Instant

// iOS: TipMoment.swift
object TipMoment {

    // iOS: TipMoment.swift
    data class Input(
        var now: Instant,
        var storefrontHidesPlus: Boolean,
        var tripDistanceMetres: Double,
        var isDraft: Boolean,
        var tripCount: Int,
        var firstLaunchAt: Instant? = null,
        var lastAskedAt: Instant? = null,
        var declines: Int,
        var tippedAt: Instant? = null,
        var afterFailure: Boolean,
    )
}
