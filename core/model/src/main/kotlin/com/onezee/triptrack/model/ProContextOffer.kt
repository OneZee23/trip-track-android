// iOS: ProContextOffer.swift
package com.onezee.triptrack.model

import java.time.Instant

// iOS: ProContextOffer.swift
enum class ProOfferMoment(val rawValue: String) {
    tenTrips("tenTrips"),
    oneMonth("oneMonth"),
    expired("expired");

    companion object {
        fun fromRawValue(value: String): ProOfferMoment? = entries.find { it.rawValue == value }
    }
}

// iOS: ProContextOffer.swift
object ProContextOffer {

    // iOS: ProContextOffer.swift
    data class Input(
        var now: Instant,
        var isPlus: Boolean,
        var storefrontHidesPlus: Boolean,
        var tripCount: Int,
        var firstLaunchAt: Instant? = null,
        var proExpiredAt: Instant? = null,
        var lastOfferAt: Instant? = null,
        var consecutiveDeclines: Int,
        var totalDeclines: Int,
        var shownMoments: Set<ProOfferMoment>,
        var isFirstSession: Boolean,
        var recordedTripThisSession: Boolean,
        var hasNetwork: Boolean,
        var onEligibleScreen: Boolean,
        var alreadyShownThisSession: Boolean,
        var entitlementsKnown: Boolean,
        var afterFailure: Boolean,
        var fromPush: Boolean,
    )
}
