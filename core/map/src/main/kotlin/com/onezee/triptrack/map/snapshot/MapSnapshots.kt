package com.onezee.triptrack.map.snapshot

import android.content.Context
import android.graphics.Bitmap
import com.onezee.triptrack.map.MapLabels
import com.onezee.triptrack.map.MapStyles
import com.onezee.triptrack.model.GeoBounds
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter

/** A picture of the map and the tile source's attribution line, tags stripped. */
class MapPicture(val bitmap: Bitmap, val attribution: String)

/**
 * iOS: MKMapSnapshotter as the Atlas poster uses it. The map of [bounds] in a [widthPoints] ×
 * [heightPoints] frame at [scale], without the logo: no layer of ours is in it, like MKMapSnapshotter
 * draws no overlays. Null when the snapshot failed (no network: the tiles come from it).
 */
object MapSnapshots {
    suspend fun take(
        context: Context,
        bounds: GeoBounds,
        widthPoints: Int,
        heightPoints: Int,
        scale: Float,
        styleUri: String = MapStyles.LIGHT,
    ): MapPicture? = withContext(Dispatchers.Main) {
        MapLibre.getInstance(context.applicationContext)
        val options = MapSnapshotter.Options(widthPoints, heightPoints)
            .withPixelRatio(scale)
            .withRegion(LatLngBounds.from(bounds.maxLat, bounds.maxLon, bounds.minLat, bounds.minLon))
            .withStyleBuilder(Style.Builder().fromUri(styleUri))
            .withLogo(false)
        val snapshotter = MapSnapshotter(context, options)
        MapLabels.localizeOnLoad(snapshotter, context.resources.configuration.locales[0].language)
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { runCatching { snapshotter.cancel() } }
            snapshotter.start({ result ->
                val attribution = result.attributions.joinToString(" ") { it.replace(Regex("<[^>]*>"), "").trim() }.trim()
                if (continuation.isActive) continuation.resume(MapPicture(result.bitmap, attribution))
            }, { if (continuation.isActive) continuation.resume(null) })
        }
    }
}
