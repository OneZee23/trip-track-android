// iOS: TripRepository.swift (TripPreviewRef), GeocodeCacheResult.swift,
// ManualTripTrackLoader.Snapshot, fetchTripStats tuple
package com.onezee.triptrack.data

import java.time.Duration
import java.time.Instant
import java.util.UUID

/** iOS: TripPreviewRef. A trip for the places pre-filter: no points, photos or checkpoints. */
data class TripPreviewRef(val id: UUID, val startDate: Instant, val previewPolyline: ByteArray?) {
    override fun equals(other: Any?): Boolean = other is TripPreviewRef && other.id == id &&
        other.startDate == startDate && previewPolyline.contentEqualsOrBothNull(other.previewPolyline)

    override fun hashCode(): Int = id.hashCode()
}

/** iOS: GeocodeCacheResult. The reverse geocoder's answer for one geohash-5 cell. */
data class GeocodeCacheResult(val locality: String?, val region: String?) {
    companion object {
        /** One TTL for every read path: 90 days. */
        val ttl: Duration = Duration.ofDays(90)
    }
}

/** iOS: the `(count:, totalDistance:)` tuple of `fetchTripStats`. Metres. */
data class TripStats(val count: Int, val totalDistance: Double)

/**
 * iOS: ManualTripTrackLoader.Snapshot. A server summary of a manual trip whose route has never been
 * downloaded; the late network answer is adopted only if the row still looks exactly like this.
 */
data class ManualTrackSnapshot(val id: UUID, val conflictVersion: Int, val lastModifiedAt: Instant?)

internal fun ByteArray?.contentEqualsOrBothNull(other: ByteArray?): Boolean = when {
    this == null -> other == null
    other == null -> false
    else -> contentEquals(other)
}
