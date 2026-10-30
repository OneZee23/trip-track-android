package com.onezee.triptrack.map.route

import com.onezee.triptrack.model.GeoPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.*
import org.junit.Test

private val origin = GeoPoint(45.0355, 38.9753)

private fun c(north: Double, east: Double = 0.0) = GeoPoint(
    origin.latitude + north / 111_320.0,
    origin.longitude + east / (111_320.0 * cos(origin.latitude * PI / 180)),
)

// iOS: RouteUnknownSpeedTests.swift. The line style follows from knowing the speed: only a filled-in
// gap has none, and it is drawn as a grey dash.
class RouteUnknownSpeedTests {
    // The dash runs from gap edge to gap edge: the boundary points are shared, the line does not break,
    // and every speed of the dash run is unknown.
    @Test
    fun testSplitRunsFromGapEdgeToGapEdge() {
        val coords = (0 until 6).map { c(it * 10.0) }
        val runs = RouteGeometry.splitByKnownSpeed(coords, listOf(10.0, 10.0, -1.0, -1.0, 12.0, 12.0))
        assertEquals(3, runs.size)
        assertEquals(listOf(10.0, 10.0), runs[0].second)
        assertEquals(coords[1].latitude, runs[1].first.first().latitude)
        assertEquals(coords[4].latitude, runs[1].first.last().latitude)
        assertTrue(runs[1].second.all { it < 0 })
        assertEquals(listOf(12.0, 12.0), runs[2].second)
    }

    @Test
    fun testAllKnownIsOneRun() {
        val coords = (0 until 4).map { c(it * 10.0) }
        assertEquals(1, RouteGeometry.splitByKnownSpeed(coords, listOf(5.0, 6.0, 7.0, 8.0)).size)
    }

    // On its own this passes on the old code too; the mixed case pins it: without a zone of its own
    // for unknown speed both halves would fall into zone 0 and merge into one group.
    @Test
    fun testUnknownSpeedGetsItsOwnGroup() {
        val groups = RouteGeometry.groupBySpeedZone(
            RouteGeometry.SimplifiedRoute(listOf(c(0.0), c(10.0), c(20.0)), listOf(-1.0, -1.0, -1.0)),
        )
        assertEquals(1, groups.size)
        assertTrue(groups[0].speed < 0)

        val mixed = RouteGeometry.groupBySpeedZone(
            RouteGeometry.SimplifiedRoute(listOf(c(0.0), c(10.0), c(20.0), c(30.0)), listOf(0.5, 0.5, -1.0, -1.0)),
        )
        assertEquals(2, mixed.size)
        assertTrue(mixed[0].speed >= 0)
        assertTrue(mixed[1].speed < 0)
    }

    // A dash must survive simplification: split first, simplify each run after (iOS comment on
    // splitByKnownSpeed). A straight filled-in piece on a straight road would otherwise vanish.
    @Test
    fun aStraightFilledGapStaysADash() {
        val coords = (0 until 30).map { c(it * 20.0) }
        val speeds = List(30) { i -> if (i in 10..19) -1.0 else 12.0 }
        val shape = RouteGeometry.build(coords, speeds)
        val dashes = shape.lines.filter { it.isUnknownSpeed }
        assertEquals(1, dashes.size)
        assertEquals(coords[9], dashes[0].coordinates.first())
        assertEquals(coords[20], dashes[0].coordinates.last())
        assertEquals(3, shape.lines.size)
    }
}

// iOS: SpeedColorScaleTests.swift. The thresholds must stay those the renderer always used.
class SpeedColorScaleTests {
    private fun legacyZone(kmh: Double): Int = when {
        kmh < 50 -> 0
        kmh < 90 -> 1
        kmh < 110 -> 2
        else -> 3
    }

    @Test
    fun testZoneMatchesLegacyThresholdsAcrossRange() {
        for (tenthsKmh in 0..2000) {
            val kmh = tenthsKmh / 10.0
            val ms = kmh / 3.6
            assertEquals(legacyZone(maxOf(0.0, ms) * 3.6), SpeedColorScale.zone(ms), "zone drift at $kmh km/h")
        }
    }

    @Test
    fun testInteriorSpeedsLandInExpectedZones() {
        assertEquals(0, SpeedColorScale.zone(12.5))
        assertEquals(1, SpeedColorScale.zone(15.277))
        assertEquals(2, SpeedColorScale.zone(26.388))
        assertEquals(3, SpeedColorScale.zone(36.111))
    }

    @Test
    fun testNegativeAndZeroSpeedClampToSlowestZone() {
        assertEquals(0, SpeedColorScale.zone(0.0))
        assertEquals(0, SpeedColorScale.zone(-10.0), "unknown/negative speed must not fall through to the fast band")
    }

    @Test
    fun testColourMatchesZoneBand() {
        assertEquals(SpeedColorScale.bands[0].argb, SpeedColorScale.argb(12.5))
        assertEquals(SpeedColorScale.bands[3].argb, SpeedColorScale.argb(36.111))
        // #2EAE50 at 0.9, the iPhone's green.
        assertEquals(0xE62EAE50.toInt(), SpeedColorScale.bands[0].argb)
    }
}

// iOS: RouteOverlayCostTests.swift. What drawing a route costs: one line per run of one colour zone,
// so the threshold is measured, not moved by eye.
class RouteOverlayCostTests {
    // Three hours of city driving at 1 Hz: traffic lights, starts, yards. The speed walks through every
    // zone, the worst case for grouping. The noise matters: without it RDP collapses the line four
    // times more eagerly than a real track and the measure lies the pleasant way.
    private fun cityTrip(hours: Double, noise: Double = 1.5): Pair<List<GeoPoint>, List<Double>> {
        val count = (hours * 3600).toInt()
        val coords = ArrayList<GeoPoint>(count)
        val speeds = ArrayList<Double>(count)
        var lat = 45.03
        var lon = 38.97
        var state = 20_260_906UL
        fun jitter(): Double {
            state = state * 6_364_136_223_846_793_005UL + 1_442_695_040_888_963_407UL
            return ((state shr 33) % 10_000UL).toDouble() / 10_000.0 * 2 - 1
        }
        for (i in 0 until count) {
            val t = i.toDouble()
            val phase = (t / 90) % 1.0
            val speed = if (phase < 0.7) 4 + 12 * sin(phase / 0.7 * PI) else 0.5
            val heading = sin(t / 200) * 2
            lat += speed * cos(heading) / 111_320
            lon += speed * sin(heading) / (111_320 * cos(45.03 * PI / 180))
            coords += GeoPoint(lat + jitter() * noise / 111_320, lon + jitter() * noise / (111_320 * cos(45.03 * PI / 180)))
            speeds += speed
        }
        return coords to speeds
    }

    private fun overlayCount(coords: List<GeoPoint>, speeds: List<Double>, epsilon: Double): Pair<Int, Int> {
        val simplified = RouteGeometry.simplifyWithSpeeds(coords, speeds, epsilon)
        return RouteGeometry.groupBySpeedZone(simplified).size to simplified.coordinates.size
    }

    // The longest trip of a day draws at the same threshold as a half-hour one: that is why there is
    // NO "simplify long tracks coarser" ceiling.
    @Test
    fun testEightHoursDrawsAtTheSameThreshold() {
        val (coords, speeds) = cityTrip(8.0)
        val (overlays, points) = overlayCount(coords, speeds, RouteGeometry.drawEpsilon)
        println("8 h: ${coords.size} points → $points, $overlays lines")
        assertTrue(overlays < 2_000, "eight hours give $overlays lines")
    }

    // Detail is cheap because speed zones dominate: five times more points cost about a fifth more.
    @Test
    fun testDetailIsCheapBecauseSpeedZonesDominate() {
        val (coords, speeds) = cityTrip(3.0)
        val fine = overlayCount(coords, speeds, RouteGeometry.drawEpsilon)
        val before065 = overlayCount(coords, speeds, 0.0001)
        println("2 m: ${fine.second} points / ${fine.first} lines · 11 m (before 0.6.5): ${before065.second} / ${before065.first}")
        assertTrue(fine.second > before065.second * 3.0, "detail did not grow: the threshold was lowered for nothing")
        assertTrue(fine.first < before065.first * 1.6, "the cost of detail grew out of proportion")
    }
}

// iOS: RouteMapView.makeUIView, the part before MapKit: segments, preview handling, plain lines.
class RouteShapeTests {
    @Test
    fun aRecordingGapSplitsTheRouteIntoSeparateLines() {
        val near = (0 until 10).map { c(it * 50.0) }
        val far = (0 until 10).map { c(5_000 + it * 50.0) }
        val shape = RouteGeometry.build(near + far, List(20) { 10.0 })
        assertEquals(2, shape.lines.size)
        assertTrue(shape.lines.none { line -> line.coordinates.any { it in near } && line.coordinates.any { it in far } })
    }

    // A preview is sparse: gap detection would shred it into singletons that draw nothing.
    @Test
    fun aPreviewIsOneSolidSegment() {
        val sparse = (0 until 10).map { c(it * 2_000.0, it * 300.0) }
        val shape = RouteGeometry.build(sparse, preview = true)
        assertEquals(1, shape.lines.size)
        assertNull(shape.lines[0].speed)
        assertTrue(RouteGeometry.build(sparse).lines.isEmpty(), "without the preview flag every step is a gap")
    }

    @Test
    fun aRouteWithoutSpeedsIsOnePlainLinePerSegment() {
        val coords = (0 until 50).map { c(it * 10.0, if (it % 2 == 0) 0.0 else 3.0) }
        val shape = RouteGeometry.build(coords)
        assertEquals(1, shape.lines.size)
        assertNull(shape.lines[0].speed)
        assertEquals(RouteAppearance.ACCENT, RouteAppearance.plain.argb(shape.lines[0]))
    }

    @Test
    fun boundsCoverEveryPointAndASinglePointHasNoLines() {
        val coords = listOf(c(0.0), c(100.0, 50.0), c(-30.0, -80.0))
        val bounds = assertNotNull(RouteGeometry.build(coords).bounds)
        assertEquals(coords.minOf { it.latitude }, bounds.minLat)
        assertEquals(coords.maxOf { it.longitude }, bounds.maxLon)
        val single = RouteGeometry.build(listOf(c(0.0)))
        assertTrue(single.lines.isEmpty())
        assertNotNull(single.bounds)
    }

    // The colours: speed bands, accent without speeds, grey for unknown, the override for all but grey.
    @Test
    fun linesTakeTheirColours() {
        val fast = RouteLine(listOf(c(0.0), c(10.0)), 40.0)
        val unknown = RouteLine(listOf(c(0.0), c(10.0)), -1.0)
        val plain = RouteLine(listOf(c(0.0), c(10.0)), null)
        val looks = RouteAppearance.plain
        assertEquals(SpeedColorScale.bands[3].argb, looks.argb(fast))
        assertEquals(RouteAppearance.UNKNOWN_SPEED, looks.argb(unknown))
        assertEquals(RouteAppearance.ACCENT, looks.argb(plain))
        val violet = looks.copy(lineColorOverride = 0xF28B5CF6.toInt())
        assertEquals(0xF28B5CF6.toInt(), violet.argb(fast))
        assertEquals(RouteAppearance.UNKNOWN_SPEED, violet.argb(unknown), "a PRO colour never repaints a filled gap")
    }

    @Test
    fun overFogRoutesAreThinnerAndCased() {
        val dark = RouteAppearance.overFog(com.onezee.triptrack.map.fog.FogPalette.NIGHT)
        assertEquals(3.2, dark.widthPoints)
        assertEquals(RouteAppearance.CASING_DARK, dark.casingArgb)
        assertEquals(RouteAppearance.CASING_LIGHT, RouteAppearance.overFog(com.onezee.triptrack.map.fog.FogPalette.MIST).casingArgb)
        assertNull(RouteAppearance.plain.casingArgb)
        assertEquals(4.0, RouteAppearance.plain.widthPoints)
    }

    @Test
    fun cssColoursKeepTheAlpha() {
        assertEquals("rgba(46, 174, 80, 0.902)", RouteLayers.rgba(SpeedColorScale.bands[0].argb))
        assertEquals("rgba(255, 69, 58, 1.000)", RouteLayers.rgba(RouteAppearance.END))
    }
}
