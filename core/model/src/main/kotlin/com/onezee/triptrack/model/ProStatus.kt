// iOS: ProStatus.swift
package com.onezee.triptrack.model

import java.time.Instant

// iOS: ProStatus.swift
enum class ProRowDestination {
    paywall,
    manageSubscription,
    nothing,
    none,
}

// iOS: ProStatus.swift
sealed interface ProStatus {
    companion object {}

    data object None : ProStatus
    data class Trial(val until: Instant?) : ProStatus
    data class Active(val until: Instant?) : ProStatus
    data class Expired(val on: Instant?) : ProStatus
    data object Grace : ProStatus
    data object Pending : ProStatus
    data object HiddenStorefront : ProStatus
    data class ProWithoutStore(val until: Instant?) : ProStatus
}
