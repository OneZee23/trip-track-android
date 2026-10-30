// iOS: PhotoMetadata.swift
package com.onezee.triptrack.model

import java.time.Instant

// iOS: PhotoMetadata.swift
object PhotoMetadata {

    // iOS: PhotoMetadata.swift
    data class Fields(
        var capturedAt: Instant? = null,
        var latitude: Double? = null,
        var longitude: Double? = null,
    )
}
