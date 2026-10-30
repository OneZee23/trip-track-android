// iOS: ProManageModel.swift
package com.onezee.triptrack.model

import java.time.Instant

// iOS: ProManageModel.swift
sealed interface ProManageLine {
    data class TrialRenews(val until: Instant, val price: String, val period: PlusProductInfo.Period) : ProManageLine
    data class Renews(val on: Instant, val price: String, val period: PlusProductInfo.Period) : ProManageLine
    data class EndsOn(val value: Instant) : ProManageLine
    data class Until(val value: Instant) : ProManageLine
    data object BillingProblem : ProManageLine
    data object Unknown : ProManageLine
}
