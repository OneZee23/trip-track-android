// iOS: TripRevealedLineTests.swift, RevealSweepTests.swift, SlideToStartTests.swift.
package com.onezee.triptrack.recording

import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.TripDiscoveries
import com.onezee.triptrack.model.TrackPoint
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RecordingPresentationTests {
    private fun strings(lang: AppLanguage = AppLanguage.ru) = AppStrings(RuntimeEnvironment.getApplication(), lang)
    private val sep = "\u00A0· "

    @Test fun testAllPartsGoInOneFixedOrder() { assertEquals("42 км нового пути${sep}1 секрет${sep}1 загадка${sep}1 веха", TripRevealedLine.compose(strings(), "42 км", 1, 1, 1)) }
    @Test fun testZeroPartsAreOmittedEntirely() { val line = TripRevealedLine.compose(strings(), "42 км", 0, 0, 2); assertEquals("42 км нового пути${sep}2 вехи", line); assertFalse(line.contains("0")) }
    @Test fun testNoKilometresLeavesTheCountsAlone() { assertEquals("1 секрет", TripRevealedLine.compose(strings(), null, 1, 0, 0)) }
    @Test fun testEmptyKilometreStringCountsAsNoKilometres() { assertEquals("1 загадка", TripRevealedLine.compose(strings(), "", 0, 1, 0)) }
    @Test fun testNothingFoundGivesAnEmptyLine() { assertEquals("", TripRevealedLine.compose(strings(), null, 0, 0, 0)) }
    @Test fun testOrderIsKilometresThenSecretsThenRiddlesThenMilestones() {
        val parts = TripRevealedLine.compose(strings(AppLanguage.en), "26 mi", 2, 3, 4).split(sep)
        assertEquals(4, parts.size)
        listOf("26 mi", "secret", "riddle", "milestone").forEachIndexed { i, word -> assertTrue(parts[i].contains(word), parts[i]) }
    }
    @Test fun testRussianCountedNounsTakeAllThreeForms() {
        val s = strings()
        assertEquals(listOf("секрет", "секрета", "секретов"), listOf(1, 2, 5).map(s::nounSecrets))
        assertEquals(listOf("веха", "вехи", "вех"), listOf(1, 2, 5).map(s::nounMilestones))
    }
    @Test fun testRussianLineDeclinesWithTheCount() {
        assertEquals("5 секретов", TripRevealedLine.compose(strings(), null, 5, 0, 0))
        assertEquals("11 вех", TripRevealedLine.compose(strings(), null, 0, 0, 11))
    }
    @Test fun testKilometreTextIsPassedThroughUntouched() { assertTrue(TripRevealedLine.compose(strings(AppLanguage.en), "8.4 miles", 0, 0, 0).contains("8.4 miles")) }
    @Test fun testEveryLanguageKeepsThePlaceholderAndTheTitleIsTranslated() {
        AppLanguage.entries.forEach { language ->
            val s = strings(language)
            assertTrue(s.revealedNewPath("42 km").contains("42 km"), language.code)
            assertFalse(s.revealedNewPath("42 km").contains("{km}"), language.code)
            assertTrue(s.revealedTitle().isNotEmpty()); assertNotEquals("revealedTitle", s.revealedTitle())
        }
    }
    @Test fun shippedDiscoveriesAreHiddenWhileNewPathAndRegionsRemainVisible() {
        assertFalse(DiscoveriesAvailability.isEnabled)
        val found = RecordingFixtures.found.visible()
        assertEquals(4.2, found.newKm); assertEquals(listOf("region-fixture"), found.newRegionIds)
        assertTrue(found.secrets.isEmpty() && found.riddles.isEmpty() && found.milestones.isEmpty())
        assertFalse(found.isEmpty)
        assertTrue(found.copy(newKm = .499, newRegionIds = emptyList()).isEmpty)
        assertFalse(found.copy(newKm = .5, newRegionIds = emptyList()).isEmpty)
    }
    @Test fun slideUsesTheDeclaredTwoThirdsThresholdAndRubberBand() {
        assertFalse(SlideToStartPolicy.commits(65.999f, 100f)); assertTrue(SlideToStartPolicy.commits(66f, 100f))
        assertFalse(SlideToStartPolicy.commits(1f, 0f)); assertEquals(0f, SlideToStartPolicy.offset(-1f, 100f))
        assertEquals(108f, SlideToStartPolicy.offset(116f, 100f))
    }
    @Test fun gpsQualityHasTheSameFiveStatesAndBoundaries() {
        assertEquals(GPSSignal.searching, GPSSignal.of(0.0, false))
        assertEquals(GPSSignal.accurate, GPSSignal.of(10.0, false))
        assertEquals(GPSSignal.medium, GPSSignal.of(10.001, false))
        assertEquals(GPSSignal.medium, GPSSignal.of(35.0, false))
        assertEquals(GPSSignal.weak, GPSSignal.of(35.001, false))
        assertEquals(GPSSignal.lost, GPSSignal.of(0.0, true))
    }
    @Test fun aManualPauseNeverDrawsALineAcrossTheParkedStretch() {
        val points = listOf(TrackPoint(latitude = 45.0, longitude = 38.0, speed = 10.0, recordingSegmentIndex = 0),
            TrackPoint(latitude = 45.0001, longitude = 38.0, speed = 10.0, recordingSegmentIndex = 0),
            TrackPoint(latitude = 45.0002, longitude = 38.0, speed = 10.0, recordingSegmentIndex = 1),
            TrackPoint(latitude = 45.0003, longitude = 38.0, speed = 10.0, recordingSegmentIndex = 1))
        val shape = recordingRoute(points)
        assertEquals(2, shape.lines.size)
        assertEquals(listOf(45.0, 45.0001), shape.lines.first().coordinates.map { it.latitude })
        assertEquals(listOf(45.0002, 45.0003), shape.lines.last().coordinates.map { it.latitude })
    }
    @Test fun summaryTimeDropsSecondsOnlyAfterAMinute() {
        assertEquals("59 сек", compactSummaryDuration(59.0, strings()))
        assertEquals("45 мин", compactSummaryDuration(45 * 60.0 + 20, strings()))
        assertEquals("2 ч", compactSummaryDuration(7200.0, strings()))
        assertEquals("2 ч 14 мин", compactSummaryDuration(8040.0, strings()))
    }
}

// iOS: RevealSweepTests.swift. Same clock, coordinates and expectations as the Swift cases.
class RevealSweepTests {
    @Test fun testProgressIsClampedToTheUnitInterval() {
        assertEquals(0.0, RevealSweep.value(-1.0)); assertEquals(0.0, RevealSweep.value(0.0))
        assertEquals(.5, RevealSweep.value(RevealSweep.duration / 2), .001)
        assertEquals(1.0, RevealSweep.value(RevealSweep.duration)); assertEquals(1.0, RevealSweep.value(10.0))
    }
    @Test fun testProgressOnlyEverGrows() {
        val sweep = RevealSweep(); sweep.begin(false, 100.0); sweep.advance(100 + RevealSweep.duration / 2)
        val half = sweep.progress; assertTrue(half > 0); sweep.advance(100.01); assertEquals(half, sweep.progress)
    }
    @Test fun testSweepReachesOneAndStopsItself() {
        val sweep = RevealSweep(); sweep.begin(false, 0.0); sweep.advance(RevealSweep.duration + .1)
        assertEquals(1.0, sweep.progress); sweep.advance(RevealSweep.duration + 5); assertEquals(1.0, sweep.progress)
    }
    @Test fun testReduceMotionJumpsStraightToOne() {
        val sweep = RevealSweep(); assertFalse(sweep.begin(true, 0.0)); assertEquals(1.0, sweep.progress)
        sweep.advance(10.0); assertEquals(1.0, sweep.progress)
    }
    @Test fun testCancelStopsTheSweepWhereItStood() {
        val sweep = RevealSweep(); sweep.begin(false, 0.0); sweep.advance(RevealSweep.duration / 4)
        val stopped = sweep.progress; sweep.cancel(); sweep.advance(RevealSweep.duration); assertEquals(stopped, sweep.progress)
    }
    @Test fun testSecondStartDoesNotRewindAFinishedSweep() { val sweep = RevealSweep(); sweep.begin(true, 0.0); sweep.begin(false, 100.0); assertEquals(1.0, sweep.progress) }
    @Test fun testSweepRunsAlongTheRoad() {
        val east = RevealSweep.axis(listOf(GeoPoint(45.0, 38.0), GeoPoint(45.0, 39.0)))
        assertEquals(0f, east.first.x, .001f); assertEquals(1f, east.second.x, .001f); assertEquals(.5f, east.first.y, .001f)
        val north = RevealSweep.axis(listOf(GeoPoint(45.0, 38.0), GeoPoint(46.0, 38.0)))
        assertEquals(1f, north.first.y, .001f); assertEquals(0f, north.second.y, .001f)
    }
    @Test fun testStandingStillAndEmptyTrackFallBackToASideWipe() {
        val still = GeoPoint(45.0, 38.0)
        listOf(emptyList(), listOf(still), listOf(still, still)).forEach {
            val axis = RevealSweep.axis(it); assertEquals(androidx.compose.ui.geometry.Offset(0f, .5f), axis.first); assertEquals(androidx.compose.ui.geometry.Offset(1f, .5f), axis.second)
        }
    }
    @Test fun testVeilCoversEverythingAtZeroAndNothingAtOne() { assertEquals(0f, RevealSweep.stops(0.0).first, .001f); assertEquals(1f, RevealSweep.stops(1.0).first, .001f) }
    @Test fun testEdgeNeverInverts() { (0..20).forEach { val (clear, veil) = RevealSweep.stops(it / 20.0); assertTrue(clear <= veil && veil <= 1f) } }
}
