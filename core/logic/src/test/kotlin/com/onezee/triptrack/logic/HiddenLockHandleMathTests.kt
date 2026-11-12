package com.onezee.triptrack.logic

import com.onezee.triptrack.model.HiddenSection
import com.onezee.triptrack.model.TrackPoint
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// iOS: HiddenPartsEditorModelTests.swift.
class HiddenLockHandleMathTests {
    @Test
    fun findsParkedHeadAndTailOnlyAfterTwoMinutesInsideSixtyMetres() {
        val points = listOf(
            point(0.0, 0), point(0.0, 60), point(0.0, 120), point(10.0, 180),
            point(100.0, 240), point(100.0, 300), point(100.0, 420),
        )
        assertEquals(HiddenLockHandleMath.ParkedStops(3, 4), HiddenLockHandleMath.parkedStops(points))
        assertEquals(HiddenLockHandleMath.ParkedStops(null, null), HiddenLockHandleMath.parkedStops(points.mapIndexed { i, p ->
            p.copy(timestamp = Instant.EPOCH.plusSeconds((i * 30).toLong()))
        }))
    }

    @Test
    fun parkedRunStopsWhenAnyPointLeavesTheRadius() {
        val points = listOf(point(0.0, 0), point(10.0, 30), point(61.0, 60), point(10.0, 180), point(0.0, 210), point(0.0, 270), point(0.0, 330))
        assertNull(HiddenLockHandleMath.parkedStops(points).departureIndex)
        assertEquals(3, HiddenLockHandleMath.parkedStops(points).arrivalIndex)
    }

    @Test
    fun resolverPrefixIsSharedAndKeepsRecordingBreaks() {
        val points = listOf(point(0.0, 0), point(10.0, 10), point(20.0, 20), point(100.0, 30), point(110.0, 40))
        val breaks = listOf(Instant.EPOCH.plusSeconds(30))
        val result = TripHiddenPartsResolver.resolve(points, breaks, 0, 0, emptyList(), null)
        assertEquals(TripHiddenPartsResolver.odometerPrefix(points, breaks), result.odometerPrefix)
        assertEquals(result.odometerPrefix[2], result.odometerPrefix[3])
    }

    @Test
    fun storedStartAndEndThresholdsKeepTheirBoundaryPointsVisible() {
        val points = (0..40).map { point(it * 10.0, it.toLong()) }
        val prefix = TripHiddenPartsResolver.odometerPrefix(points, emptyList())
        val startIndex = 12
        val start = HiddenLockHandleMath.startMetresForBoundary(prefix, startIndex)
        val startResult = resolve(points, start, 0)
        assertEquals((0 until startIndex).toSet(), startResult.hidden.indices.filter { startResult.hidden[it] }.toSet())
        assertFalse(startResult.hidden[startIndex])

        val endIndex = 28
        val end = HiddenLockHandleMath.endMetresForBoundary(prefix, endIndex)
        val endResult = resolve(points, 0, end)
        assertEquals((endIndex + 1..points.lastIndex).toSet(), endResult.hidden.indices.filter { endResult.hidden[it] }.toSet())
        assertFalse(endResult.hidden[endIndex])

        assertTrue(resolve(points, 0, 0).hidden.none { it })
    }

    @Test
    fun equalPrefixesCanonicalizeToTheVisibleSideOfAZeroLengthStep() {
        val points = listOf(point(0.0, 0), point(0.0, 1), point(0.0, 2), point(10.0, 3), point(10.0, 4), point(20.0, 5))
        val prefix = TripHiddenPartsResolver.odometerPrefix(points, emptyList())
        val startMetres = HiddenLockHandleMath.startMetresForBoundary(prefix, 4)
        val startIndex = HiddenLockHandleMath.startBoundaryIndex(prefix, startMetres)
        assertEquals(3, startIndex)
        val startResult = resolve(points, startMetres, 0)
        assertEquals(setOf(0, 1, 2), startResult.hidden.indices.filter { startResult.hidden[it] }.toSet())
        assertFalse(startResult.hidden[startIndex])

        val endMetres = HiddenLockHandleMath.endMetresForBoundary(prefix, 1)
        val endIndex = HiddenLockHandleMath.endBoundaryIndex(prefix, endMetres)
        assertEquals(2, endIndex)
        val endResult = resolve(points, 0, endMetres)
        assertEquals(setOf(3, 4, 5), endResult.hidden.indices.filter { endResult.hidden[it] }.toSet())
        assertFalse(endResult.hidden[endIndex])
    }

    @Test
    fun parkedHeadAndTailThresholdsHideExactlyTheirIntervals() {
        val head = listOf(point(0.0, 0), point(0.0, 60), point(0.0, 120)) +
            (1..10).map { point(it * 10.0, 120L + it * 10) }
        val headStops = HiddenLockHandleMath.parkedStops(head)
        assertEquals(8, headStops.departureIndex)
        val headPrefix = TripHiddenPartsResolver.odometerPrefix(head, emptyList())
        val startParking = HiddenLockHandleMath.startMetresForParking(headPrefix, headStops.departureIndex)
        val headResult = resolve(head, startParking, 0)
        assertEquals((0..8).toSet(), headResult.hidden.indices.filter { headResult.hidden[it] }.toSet())

        val tail = (0..30).map { point(it * 10.0, it * 20L) }
        val tailStops = HiddenLockHandleMath.parkedStops(tail)
        assertEquals(24, tailStops.arrivalIndex)
        val tailPrefix = TripHiddenPartsResolver.odometerPrefix(tail, emptyList())
        val endParking = HiddenLockHandleMath.endMetresForParking(tailPrefix, tailStops.arrivalIndex)
        val tailResult = resolve(tail, 0, endParking)
        assertEquals((24..30).toSet(), tailResult.hidden.indices.filter { tailResult.hidden[it] }.toSet())
    }

    @Test
    fun detentsAndMinimumVisibleGapUseTheResolverPrefix() {
        val prefix = (0..100).map { it * 10.0 }
        assertEquals(20, HiddenLockHandleMath.detentIndex(prefix, 21, 0, 100, 0.0, fromEnd = false))
        assertEquals(80, HiddenLockHandleMath.detentIndex(prefix, 79, 0, 100, 0.0, fromEnd = true))
        assertEquals(25, HiddenLockHandleMath.detentIndex(prefix, 25, 0, 100, 0.0, fromEnd = false))
        assertEquals(10, HiddenLockHandleMath.allowedStartMaximum(prefix, 20))
        assertEquals(10, HiddenLockHandleMath.allowedEndMinimum(prefix, 0))
    }

    @Test
    fun parkingHandleTurnsToGhostOnlyPastTheStopAndAwayFromTheRouteBehindIt() {
        val finger = HiddenLockHandleMath.ScreenPoint(-40.0, 0.0)
        val stop = HiddenLockHandleMath.ScreenPoint(0.0, 0.0)
        assertFalse(HiddenLockHandleMath.isOutwardPastStop(finger, stop, HiddenLockHandleMath.ScreenPoint(-50.0, 0.0)))
        assertTrue(HiddenLockHandleMath.isOutwardPastStop(finger, stop, HiddenLockHandleMath.ScreenPoint(50.0, 0.0)))
        assertFalse(HiddenLockHandleMath.isOutwardPastStop(HiddenLockHandleMath.ScreenPoint(-30.0, 0.0), stop, HiddenLockHandleMath.ScreenPoint(50.0, 0.0)))
    }

    @Test
    fun nearestPointKeepsAnOutAndBackDragOnItsCurrentPass() {
        val projected = buildList {
            repeat(80) { add(HiddenLockHandleMath.ScreenPoint(it.toDouble(), 0.0)) }
            repeat(80) { add(HiddenLockHandleMath.ScreenPoint((79 - it + 3).toDouble(), 3.0)) }
        }
        val found = HiddenLockHandleMath.nearestPoint(
            project = projected::getOrNull,
            currentIndex = 40,
            lowerBound = 0,
            upperBound = projected.lastIndex,
            finger = HiddenLockHandleMath.ScreenPoint(43.0, 2.0),
            pointCount = projected.size,
        )
        assertEquals(43, found?.index)
    }

    @Test
    fun localDragOnTenThousandPointsProjectsOnlyTheNinetyOnePointWindow() {
        var projections = 0
        val found = HiddenLockHandleMath.nearestPoint(
            project = { index ->
                projections++
                HiddenLockHandleMath.ScreenPoint(index.toDouble(), 0.0)
            },
            currentIndex = 5_000,
            lowerBound = 0,
            upperBound = 9_999,
            finger = HiddenLockHandleMath.ScreenPoint(5_001.0, 1.0),
            pointCount = 10_000,
        )
        assertEquals(5_001, found?.index)
        assertEquals(91, projections)
    }

    @Test
    fun sectionCreationClampsAndRejectsOverlapOrShortSections() {
        val prefix = (0..100).map { it * 10.0 }
        assertEquals(0..12, HiddenLockHandleMath.createSection(prefix, 0, 0, 100, emptyList()))
        assertEquals(88..100, HiddenLockHandleMath.createSection(prefix, 100, 0, 100, emptyList()))
        assertNull(HiddenLockHandleMath.createSection(prefix, 50, 0, 100, listOf(40..60)))
        assertNull(HiddenLockHandleMath.createSection(prefix, 2, 0, 3, emptyList()))
    }

    @Test
    fun shortSectionReleaseCanBeUndoneWithItsOriginalTimeRangeAndPosition() {
        val points = (0..20).map { point(it * 10.0, it.toLong()) }
        val prefix = TripHiddenPartsResolver.odometerPrefix(points, emptyList())
        val range = HiddenLockHandleMath.createSection(prefix, 10, 0, 20, emptyList()) ?: error("section")
        val original = HiddenSection(UUID.randomUUID(), points[range.first].timestamp, points[range.last].timestamp)
        val short = HiddenSection(UUID.randomUUID(), points[1].timestamp, points[4].timestamp)
        val sections = listOf(original, short)
        val removal = HiddenLockHandleMath.sectionRemovalOnRelease(prefix, points, sections, short.id) ?: error("removal")
        val remaining = sections.filterNot { it.id == removal.section.id }
        assertEquals(listOf(original), remaining)
        assertEquals(sections, HiddenLockHandleMath.restoreSection(remaining, removal))
        assertTrue(HiddenLockHandleMath.sectionDistanceMetres(prefix, range) >= HiddenLockHandleMath.minimumSectionMetres)
        assertTrue(HiddenLockHandleMath.shouldRemoveSection(prefix, 1..4))
    }

    private fun resolve(points: List<TrackPoint>, startMetres: Int, endMetres: Int) =
        TripHiddenPartsResolver.resolve(points, emptyList(), startMetres, endMetres, emptyList(), null)

    private fun point(distanceMetres: Double, seconds: Long): TrackPoint = TrackPoint(
        latitude = 0.0,
        longitude = distanceMetres / 111_195.0,
        timestamp = Instant.EPOCH.plusSeconds(seconds),
    )
}
