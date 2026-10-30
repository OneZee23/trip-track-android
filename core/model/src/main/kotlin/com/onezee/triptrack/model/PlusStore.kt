// iOS: PlusStore.swift
package com.onezee.triptrack.model

// iOS: PlusStore.swift
object PlusStore {

    // iOS: PlusStore.swift
    enum class State(val rawValue: String) {
        none("none"),
        trial("trial"),
        active("active"),
        grace("grace"),
        expired("expired");

        companion object {
            fun fromRawValue(value: String): State? = entries.find { it.rawValue == value }
        }
    }

    // iOS: PlusStore.swift
    enum class RenewalSummary {
        subscribed,
        inGracePeriod,
        inBillingRetryPeriod,
        expired,
        revoked,
    }

    // iOS: PlusStore.swift
    sealed interface PurchaseOutcome {
        data object Success : PurchaseOutcome
        data object Cancelled : PurchaseOutcome
        data object Pending : PurchaseOutcome
        data class Failed(val value: Throwable) : PurchaseOutcome
    }

    // iOS: PlusStore.swift
    enum class PurchaseMessage {
        none,
        pending,
        failed,
    }
}
