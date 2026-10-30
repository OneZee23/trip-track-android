// iOS: PlusAttachQueue.swift
package com.onezee.triptrack.model

import java.time.Instant

// iOS: PlusAttachQueue.swift
object PlusAttachQueue {

    // iOS: PlusAttachQueue.swift
    data class Item(
        val key: String,
        val jws: String,
        var attempts: Int = 0,
        var notBefore: Instant? = null,
        var rejectedBuilds: List<String> = emptyList(),
    )

    // iOS: PlusAttachQueue.swift
    sealed interface Outcome {
        data object Delivered : Outcome
        data class Rejected(val value: String) : Outcome
        data object Deferred : Outcome
    }
}
