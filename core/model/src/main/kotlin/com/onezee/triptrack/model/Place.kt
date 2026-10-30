// iOS: Place.swift
package com.onezee.triptrack.model

import java.time.Instant
import java.util.UUID

// iOS: Place.swift
data class Place(
    val id: UUID,
    val cell: String,
    var latitude: Double,
    var longitude: Double,
    var name: String? = null,
    val createdAt: Instant,
) {
    companion object {
        val namespace: UUID = UUID.fromString("3C7A9C2E-5D3B-4F4E-8A1B-6E2F0D9C7B5A")
        const val cellPrecision = 7
        fun id(forCell: String): UUID = uuidV5(namespace, forCell)
    }
}
