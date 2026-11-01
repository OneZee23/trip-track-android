package com.onezee.triptrack.location.world

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.onezee.triptrack.data.RecordingStore
import com.onezee.triptrack.data.TripRepository
import com.onezee.triptrack.data.sync.SyncAction
import com.onezee.triptrack.data.sync.SyncEntityType
import com.onezee.triptrack.data.sync.SyncRequest
import com.onezee.triptrack.data.sync.SyncRequestSink
import com.onezee.triptrack.logic.GeohashEncoder
import com.onezee.triptrack.logic.distanceFrom
import com.onezee.triptrack.model.GeoPoint
import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** iOS: CLPlacemark fields used by trip titles and checkpoint names. */
data class Placemark(val locality: String?, val subAdministrativeArea: String?, val administrativeArea: String?,
    val subLocality: String? = null, val name: String? = null)

/** iOS: CLGeocoder.reverseGeocodeLocation. Null on any failure, as the completion's empty array. */
fun interface ReverseGeocoder {
    suspend fun placemark(at: GeoPoint): Placemark?
}

/** The platform geocoder; on a phone without a geocoder backend every answer is null. */
class AndroidReverseGeocoder(private val context: Context, private val locale: () -> Locale = Locale::getDefault) : ReverseGeocoder {
    override suspend fun placemark(at: GeoPoint): Placemark? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, locale())
        val address: Address? = if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(at.latitude, at.longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                    }

                    override fun onError(errorMessage: String?) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(at.latitude, at.longitude, 1)?.firstOrNull() }.getOrNull()
            }
        }
        return address?.let { Placemark(it.locality, it.subAdminArea, it.adminArea, it.subLocality, it.featureName) }
    }
}

/**
 * iOS: TripManager.geocodeAndNameTrip. «Krasnodar → Dzhubga» from the ends of the track, through
 * the geocoder cache first; a round trip (ends within 20 km) is named by its start. Offline, a trip
 * keeps no title and is retried when the network returns.
 */
class TripNamer(
    private val trips: TripRepository,
    private val recording: RecordingStore,
    private val geocoder: ReverseGeocoder,
    private val sync: SyncRequestSink,
    private val isOffline: () -> Boolean,
    /** iOS: TripAutoTitle.generate, the start date in the app's language. */
    private val autoTitle: (Instant) -> String,
) {
    suspend fun name(tripId: UUID, startDate: Instant) {
        val points = recording.trackPoints(tripId)
        val first = points.firstOrNull() ?: return
        val last = points.last()
        val start = GeoPoint(first.latitude, first.longitude)
        val end = GeoPoint(last.latitude, last.longitude)
        val startKey = key(start)
        val endKey = key(end)
        val isCircular = start.distanceFrom(end) < CIRCULAR_METRES
        val cachedStart = trips.cachedGeocode(startKey)
        val cachedEnd = trips.cachedGeocode(endKey)
        if (cachedStart != null) {
            if (isCircular) return apply(tripId, cachedStart.locality ?: autoTitle(startDate), cachedStart.region)
            if (cachedEnd != null) {
                return apply(tripId, title(cachedStart.locality, cachedEnd.locality, startDate), cachedStart.region)
            }
        }
        if (isOffline()) return
        val startMark = geocoder.placemark(start)
        val startName = locality(startMark)
        val startRegion = startMark?.administrativeArea
        // iOS caches the geocoder's silence as an answer too (`locality == nil`).
        trips.saveGeocode(startKey, startName, startRegion)
        if (isCircular) return apply(tripId, startName ?: autoTitle(startDate), startRegion)
        if (cachedEnd != null) return apply(tripId, title(startName, cachedEnd.locality, startDate), startRegion)
        val endMark = geocoder.placemark(end)
        val endName = locality(endMark)
        trips.saveGeocode(endKey, endName, endMark?.administrativeArea)
        apply(tripId, title(startName, endName, startDate), startRegion)
    }

    /** iOS: retryGeocodingForUntitledTrips. One request at a time, a second and a half apart. */
    suspend fun retryUntitled(startDateOf: suspend (UUID) -> Instant?, pause: suspend () -> Unit) {
        if (isOffline()) return
        for (id in recording.untitledTripIds()) {
            val start = startDateOf(id) ?: continue
            name(id, start)
            pause()
        }
    }

    private suspend fun apply(tripId: UUID, title: String, region: String?) {
        if (recording.nameTrip(tripId, title, region)) {
            sync.enqueue(SyncRequest(SyncEntityType.trip, tripId, SyncAction.update))
        }
    }

    private fun title(start: String?, end: String?, startDate: Instant): String = when {
        start != null && end != null -> "$start → $end"
        start != null -> start
        end != null -> end
        else -> autoTitle(startDate)
    }

    private fun locality(mark: Placemark?): String? =
        mark?.locality ?: mark?.subAdministrativeArea ?: mark?.administrativeArea

    private fun key(point: GeoPoint) = GeohashEncoder.encode(point.latitude, point.longitude, 5)

    private companion object {
        const val CIRCULAR_METRES = 20_000.0
    }
}
