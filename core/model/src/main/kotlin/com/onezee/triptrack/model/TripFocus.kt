// iOS: TripFocus (TripDetailView.swift)
package com.onezee.triptrack.model

import java.util.UUID

/** An opening target travels with the trip destination until its moments are ready. */
sealed interface TripFocus {
    data object Top : TripFocus
    data object Comments : TripFocus
    data class Comment(val id: UUID) : TripFocus
    data class Checkpoint(val id: UUID) : TripFocus
}
