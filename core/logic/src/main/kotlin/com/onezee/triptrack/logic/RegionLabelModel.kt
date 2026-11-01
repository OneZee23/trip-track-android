package com.onezee.triptrack.logic

import com.onezee.triptrack.model.DistanceUnit
import com.onezee.triptrack.model.GeoBounds
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.model.RegionAtlas.Region
import java.util.Locale

// iOS: RegionLabelAnnotation.swift. Text and the geographic anchor are installed together;
// changing the camera changes visibility, never the source list or its centroid.
data class RegionLabelAnnotation(
    val labelId: String,
    val coordinate: GeoPoint,
    val name: String,
    val kmLine: String?,
    val bounds: GeoBounds,
)

// iOS: RegionLabelAnnotation.swift (RegionLabelModel).
object RegionLabelModel {
    fun regionLabels(
        regions: List<Region>,
        revealed: RevealedLayer,
        visitedRegionIds: Set<String>,
        unit: DistanceUnit,
        language: String,
        words: MeasureWords,
    ): List<RegionLabelAnnotation> = regions.mapNotNull { region ->
        if (region.id !in visitedRegionIds) return@mapNotNull null
        RegionLabelAnnotation(
            region.id, region.center, region.localizedName(language).uppercase(Locale.forLanguageTag(language)),
            Measure.distanceKm(revealed.openedKm(region.id), unit, language, words), region.bounds,
        )
    }
}
