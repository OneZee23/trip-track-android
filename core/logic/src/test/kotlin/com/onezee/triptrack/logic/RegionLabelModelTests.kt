package com.onezee.triptrack.logic

import com.onezee.triptrack.model.DistanceUnit
import com.onezee.triptrack.model.GeoBounds
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.model.RegionAtlas.Region
import kotlin.test.*
import org.junit.Test

// iOS: RegionLabelModelTests.swift, unchanged regions, distances, languages and expectations.
class RegionLabelModelTests {
    private val kuban = Region("RU-KDA", "RU", "Краснодарский край", "Krasnodar Krai",
        GeoPoint(45.0, 39.0), GeoBounds(44.0, 46.5, 36.5, 41.0), emptyList())

    private fun layer(km: Double) = RevealedLayer.empty.copy(regionKm = mapOf("RU-KDA" to km))
    private fun labels(km: Double = 100.0, visited: Set<String> = setOf("RU-KDA"),
                       unit: DistanceUnit = DistanceUnit.km, language: String = "ru") =
        RegionLabelModel.regionLabels(listOf(kuban), layer(km), visited, unit, language, IosMeasureWords)

    @Test fun testRegionNameRussian() = assertEquals("КРАСНОДАРСКИЙ КРАЙ", labels().first().name)
    @Test fun testRegionNameEnglish() = assertEquals("KRASNODAR KRAI", labels(language = "en").first().name)
    @Test fun testRegionNameFallsBackToEnglishForOtherLanguages() = assertEquals("KRASNODAR KRAI", labels(language = "de").first().name)
    @Test fun testUnvisitedRegionGetsNoLabel() = assertTrue(labels(km = 0.0, visited = emptySet()).isEmpty())
    @Test fun testVisitedRegionUsesBundleCentroid() {
        assertEquals(kuban.center, labels().first().coordinate)
        assertEquals(kuban.bounds, labels().first().bounds)
    }
    @Test fun testKmLineUsesMeasureInKilometres() = assertEquals(
        Measure.distanceKm(143.2, DistanceUnit.km, "ru", IosMeasureWords), labels(km = 143.2).first().kmLine)
    @Test fun testKmLineUsesMeasureInMiles() {
        val line = labels(km = 143.2, unit = DistanceUnit.miles, language = "en").first().kmLine
        assertEquals(Measure.distanceKm(143.2, DistanceUnit.miles, "en", IosMeasureWords), line)
        assertNotEquals(Measure.distanceKm(143.2, DistanceUnit.km, "en", IosMeasureWords), line)
    }
}
