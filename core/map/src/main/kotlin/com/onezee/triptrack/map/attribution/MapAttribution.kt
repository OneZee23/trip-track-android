package com.onezee.triptrack.map.attribution

import android.content.Context
import android.view.View
import org.maplibre.android.attribution.AttributionParser
import org.maplibre.android.maps.AttributionDialogManager
import org.maplibre.android.maps.MapLibreMap

// iOS: MyMapRepresentable.swift (MapKit's Legal entry). Provider names/URLs are licence metadata.
data class MapAttribution(val title: String, val url: String) {
    companion object {
        // Keep credits reachable even before TileJSON arrives (or when loading it fails offline).
        val openFreeMap = listOf(
            MapAttribution("OpenFreeMap", "https://openfreemap.org/"),
            MapAttribution("© OpenMapTiles", "https://openmaptiles.org/"),
            MapAttribution("© OpenStreetMap contributors", "https://www.openstreetmap.org/copyright"),
        )

        fun fromSources(context: Context, sources: List<String>): List<MapAttribution> {
            val parsed = AttributionParser.Options(context)
                .withCopyrightSign(true)
                .withImproveMap(true)
                .withAttributionData(*sources.toTypedArray())
                .build().attributions.map { MapAttribution(it.title, it.url) }
            // The loaded style remains authoritative; fallbacks fill only missing providers.
            return parsed + openFreeMap.filter { fallback ->
                parsed.none {
                    it.url.trimEnd('/') == fallback.url.trimEnd('/') ||
                        it.title.removePrefix("© ") == fallback.title.removePrefix("© ")
                }
            }
        }
    }
}

// iOS: CLAUDE.md «Dialogs — always ours». Replace the SDK entry point, keeping its visible button.
internal class HouseAttributionManager(
    private val context: Context,
    private val map: MapLibreMap,
    private val show: (List<MapAttribution>) -> Unit,
) : AttributionDialogManager(context, map) {
    override fun onClick(view: View) {
        show(MapAttribution.fromSources(context, map.style?.sources.orEmpty().map { it.attribution }))
    }
}
