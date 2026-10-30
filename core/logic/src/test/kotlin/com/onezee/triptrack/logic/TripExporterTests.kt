package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.*
import org.junit.Test

// iOS: GPXExporterTests.swift
class GPXExporterTests {
    private val start = Instant.ofEpochSecond(1_700_000_000)
    private fun point(lat: Double = 45.0, lon: Double = 39.0, altitude: Double = 0.0, speed: Double = 0.0, offset: Long = 0) =
        TrackPoint(latitude = lat, longitude = lon, altitude = altitude, speed = speed, timestamp = start.plusSeconds(offset))
    private fun trip(title: String? = "Krasnodar → Sochi") = Trip(startDate = start, title = title)
    private fun gpx(points: List<TrackPoint>, title: String? = "Krasnodar → Sochi") = GPXExporter.gpx(trip(title), points)
    @Test fun testProducesWellFormedXML() {
        val xml = gpx(listOf(point(), point(45.1, 39.1, offset = 60)))
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
        assertEquals("gpx", document.documentElement.tagName)
    }
    @Test fun testTrackPointCountMatchesInputCount() { assertEquals(12, gpx((0..11).map { point(offset = it * 10L) }).split("<trkpt").size - 1) }
    @Test fun testCoordinatesUseADotUnderRussianLocale() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ru-RU"))
            val xml = gpx(listOf(point(45.123456, 39.654321)))
            assertContains(xml, "lat=\"45.123456\""); assertContains(xml, "lon=\"39.654321\""); assertFalse(xml.contains("45,123456"))
        } finally { Locale.setDefault(before) }
    }
    @Test fun testSixDecimalPlaces() { val xml = gpx(listOf(point(45.1, 39.2))); assertContains(xml, "45.100000"); assertContains(xml, "39.200000") }
    @Test fun testEscapesAmpersandAndLessThanInName() { val xml = gpx(listOf(point(1.0, 1.0)), "Home & <Away>"); assertContains(xml, "Home &amp; &lt;Away&gt;"); assertFalse(xml.contains("Home & <Away>")) }
    @Test fun testEmptyTrackHasNoTrkElement() { val xml = gpx(emptyList()); assertFalse(xml.contains("<trk>")); assertContains(xml, "<metadata>") }
    @Test fun testUnknownSpeedIsOmittedFromExtensions() {
        val xml = gpx(listOf(point(speed = 12.5), point(45.01, 39.01, speed = -1.0, offset = 30)))
        assertEquals(1, xml.split("<speed>").size - 1); assertContains(xml, "<speed>12.500000</speed>")
    }
    @Test fun testElevationOmittedWhenZero() { val xml = gpx(listOf(point(), point(45.01, 39.01, altitude = 88.0, offset = 30))); assertEquals(1, xml.split("<ele>").size - 1); assertContains(xml, "<ele>88.000000</ele>") }
    @Test fun testPointsAreOrderedByTime() { val xml = gpx(listOf(point(45.2, 39.2, offset = 120), point(45.1, 39.1))); assertTrue(xml.indexOf("45.100000") < xml.indexOf("45.200000")) }
    @Test fun testTimeIsUTCWithZSuffix() { assertContains(gpx(listOf(point(1.0, 1.0))), "2023-11-14T22:13:20Z") }
    @Test fun testFallsBackToDateWhenTitleIsNil() { assertEquals("14 Nov 2023", GPXExporter.displayName(trip(null))) }
    @Test fun testFallsBackToDateWhenTitleIsBlank() { assertEquals("14 Nov 2023", GPXExporter.displayName(trip("   "))) }
    @Test fun testInterpolatedPointsAreNotExported() { assertFalse(gpx(listOf(point().copy(isInterpolated = true))).contains("<trk>")) }
    @Test fun roundingAndSignedZeroMatchFoundationPrintf() {
        val xml = gpx(listOf(point(0.0000005, 1.2345665, altitude = 1.2345675, speed = -0.0)))
        assertContains(xml, "lat=\"0.000000\""); assertContains(xml, "lon=\"1.234566\"")
        assertContains(xml, "<ele>1.234568</ele>"); assertContains(xml, "<speed>-0.000000</speed>")
    }
}

// iOS: CSVExporterTests.swift
class CSVExporterTests {
    private val start = Instant.ofEpochSecond(1_700_000_000)
    private val trip = Trip(startDate = start, title = "Krasnodar → Sochi")
    private fun point(lat: Double = 45.0, lon: Double = 39.0, altitude: Double = 0.0, speed: Double = 0.0,
        course: Double = -1.0, horizontalAccuracy: Double = 0.0, offset: Long = 0) =
        TrackPoint(latitude = lat, longitude = lon, altitude = altitude, speed = speed, course = course, horizontalAccuracy = horizontalAccuracy, timestamp = start.plusSeconds(offset))
    private fun csv(points: List<TrackPoint>) = CSVExporter.csv(trip, points)
    private fun row(point: TrackPoint) = csv(listOf(point)).lines()[1].split(',')
    @Test fun testHeaderIsExact() { assertEquals("timestamp,latitude,longitude,altitude_m,speed_mps,course_deg,horizontal_accuracy_m", csv(emptyList()).lines()[0]) }
    @Test fun testRowCountIsPointsPlusHeader() { assertEquals(13, csv((0..11).map { point(offset = it * 10L) }).lines().size) }
    @Test fun testCoordinatesUseADotUnderRussianLocale() { val text = csv(listOf(point(45.123456, 39.654321))); assertContains(text, "45.123456"); assertContains(text, "39.654321"); assertFalse(text.contains("45,123456")) }
    @Test fun testUnknownSpeedIsEmptyField() { val fields = row(point(speed = -1.0)); assertEquals(7, fields.size); assertEquals("", fields[4]) }
    @Test fun testUnknownCourseIsEmptyField() { assertEquals("", row(point(course = -1.0))[5]) }
    @Test fun testZeroAltitudeIsEmptyField() { assertEquals("", row(point())[3]) }
    @Test fun testKnownValuesArePrinted() { assertEquals(listOf("88.000000", "12.500000", "270.000000", "5.000000"), row(point(altitude = 88.0, speed = 12.5, course = 270.0, horizontalAccuracy = 5.0)).drop(3)) }
    @Test fun testRowsAreOrderedByTime() { val text = csv(listOf(point(45.2, 39.2, offset = 120), point(45.1, 39.1))); assertTrue(text.indexOf("45.100000") < text.indexOf("45.200000")) }
    @Test fun testTimeIsUTCISO8601() { assertContains(csv(listOf(point(1.0, 1.0))), "2023-11-14T22:13:20Z") }
    @Test fun testEmptyTrackHasOnlyHeader() { assertEquals(1, csv(emptyList()).lines().size); assertEquals(CSVExporter.header, csv(emptyList())) }
    @Test fun testNoCommaInsideNumericValues() { assertEquals(6, csv(listOf(point(altitude = 12_345.0, speed = 1_234.5))).lines()[1].count { it == ',' }) }
    @Test fun testInterpolatedPointsAreNotExported() { assertEquals(CSVExporter.header, csv(listOf(point().copy(isInterpolated = true)))) }
    @Test fun roundingAndSignedZeroMatchFoundationPrintf() {
        assertEquals(listOf("0.000000", "1.234566", "1.234568", "-0.000000"), row(point(0.0000005, 1.2345665, altitude = 1.2345675, speed = -0.0)).drop(1).take(4))
    }
}
