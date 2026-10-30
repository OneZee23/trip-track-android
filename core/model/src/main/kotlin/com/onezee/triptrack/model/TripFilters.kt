// iOS: TripFilters.swift
package com.onezee.triptrack.model

import java.time.Instant

// iOS: TripFilters.swift
data class TripFilters(
    var region: String? = null,
    var dateFrom: Instant? = null,
    var dateTo: Instant? = null,
)
