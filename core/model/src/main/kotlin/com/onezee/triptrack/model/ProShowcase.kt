// iOS: ProShowcase.swift
package com.onezee.triptrack.model

// iOS: ProShowcase.swift
enum class ProShowcaseKind(val rawValue: String) {
    profileBackground("profileBackground"),
    avatarFrame("avatarFrame"),
    vehicleCard("vehicleCard"),
    routeLine("routeLine");

    companion object {
        fun fromRawValue(value: String): ProShowcaseKind? = entries.find { it.rawValue == value }
    }
}

// iOS: ProShowcase.swift
enum class ProShowcaseAction {
    done,
    tryFreeWeek,
    getPro,
    renew,
}

// iOS: ProShowcase.swift
data class Tile(
    val id: String,
    val isPremium: Boolean,
    val name: String,
)

// iOS: ProShowcase.swift
object ProShowcase {

    // iOS: ProShowcase.swift
    data class State(
        var isPlus: Boolean,
        var storefrontHidesPlus: Boolean,
        var tryingOnPremium: Boolean,
        var proHasEnded: Boolean,
        var freeWeekAvailable: Boolean,
    )
}
