package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.*
import java.time.temporal.WeekFields
import java.util.UUID
import kotlin.test.*

// iOS: BadgeDefinitionsTests.swift / BadgeUnitTests.swift (rules and metric figures).
class BadgeDefinitionsTests {
    private val now = Instant.parse("2026-10-26T12:00:00Z")
    private fun empty() = BadgeManager.computeStats(emptyList(), now, ZoneOffset.UTC, WeekFields.ISO)
    private fun badge(id: String) = BadgeDefinitions.all.first { it.id == id }
    @Test fun testKomsomolskyBadgeIsHiddenEpicExternalAndMatchesTheSecretId() {
        val badge = badge("secret_komsomolsky")
        assertTrue(badge.isHidden); assertEquals(BadgeCategory.exploration, badge.category); assertEquals(BadgeRarity.epic, badge.rarity)
        assertFalse(badge.checkUnlocked(empty())); assertTrue(badge.id in BadgeDefinitions.externallyUnlockedIds)
        assertEquals("secret_" + "komsomolsky", badge.id)
    }
    @Test fun testBadgeIdsAreUnique() {
        val ids = BadgeDefinitions.all.map { it.id }; assertEquals(58, ids.size); assertEquals(ids.size, ids.toSet().size)
    }
    @Test fun testThresholdsAreTheSameNumberOfKilometresForEveryone() {
        assertFalse(badge("equator").checkUnlocked(empty().copy(totalDistanceKm = 40_074.0)))
        assertTrue(badge("equator").checkUnlocked(empty().copy(totalDistanceKm = 40_075.0)))
        assertFalse(badge("marathon_100").checkUnlocked(empty().copy(longestTripKm = 99.0)))
        assertTrue(badge("marathon_100").checkUnlocked(empty().copy(longestTripKm = 100.0)))
        assertFalse(badge("speed_demon").checkUnlocked(empty().copy(maxSpeedKmh = 119.0)))
        assertTrue(badge("speed_demon").checkUnlocked(empty().copy(maxSpeedKmh = 120.0)))
        assertFalse(badge("mountain_goat").checkUnlocked(empty().copy(maxElevationGainSingleTrip = 999.0)))
        assertTrue(badge("mountain_goat").checkUnlocked(empty().copy(maxElevationGainSingleTrip = 1000.0)))
        assertFalse(badge("above_clouds").checkUnlocked(empty().copy(maxAltitude = 1999.0)))
        assertTrue(badge("above_clouds").checkUnlocked(empty().copy(maxAltitude = 2000.0)))
    }
    @Test fun testTheFigureInTheTextIsTheFigureInTheRule() {
        for ((id, n) in listOf("equator" to 40_075.0, "speed_demon" to 120.0, "mountain_goat" to 1000.0,
            "above_clouds" to 2000.0, "highway_wolf" to 300.0, "marathon_42" to 42.195))
            assertNumber(n, badge(id).figures.first().value, 1e-9)
    }
    @Test fun testBadgeFiguresUseMeasureAndKeepTheirAtLeastPlus() {
        fun shown(id: String, unit: DistanceUnit, lang: String = "en") = badge(id).figures.map { it.shown(unit, lang, IosMeasureWords) }
        assertEquals(listOf("26.2 mi"), shown("marathon_42", DistanceUnit.miles))
        assertEquals(listOf("24,901 mi"), shown("equator", DistanceUnit.miles))
        assertEquals(listOf("75 mph"), shown("speed_demon", DistanceUnit.miles))
        assertEquals(listOf("500+ km"), shown("iron_butt", DistanceUnit.km))
        assertEquals(listOf("311+ mi"), shown("iron_butt", DistanceUnit.miles))
        assertEquals(listOf("1,000 m"), shown("mountain_goat", DistanceUnit.km))
        assertEquals(listOf("3,281 ft"), shown("mountain_goat", DistanceUnit.miles))
        assertEquals(listOf("6,562+ ft"), shown("above_clouds", DistanceUnit.miles))
        assertEquals(listOf("10 m", "20 km"), shown("sea_level", DistanceUnit.km))
        assertEquals(listOf("33 ft", "12 mi"), shown("sea_level", DistanceUnit.miles))
        assertEquals(listOf("42.2 км"), shown("marathon_42", DistanceUnit.km, "ru"))
        assertEquals(listOf("2\u00a0000+ м"), shown("above_clouds", DistanceUnit.km, "uk"))
        assertEquals(listOf("1.000 m"), shown("mountain_goat", DistanceUnit.km, "de"))
        assertEquals(listOf("1.000 km"), shown("thousand", DistanceUnit.km, "de"))
        for (badge in BadgeDefinitions.all) for (figure in badge.figures) for (lang in UnitNumber.languages) {
            val text = figure.shown(DistanceUnit.miles, lang, IosMeasureWords)
            assertFalse(text.contains("км")); assertFalse(text.lowercase().contains("km")); assertFalse(text.contains('%'))
        }
    }
    @Test fun testAllMilestoneBoundariesAndNonStatisticalBadges() {
        val distance = mapOf("first_km" to 1.0, "century" to 100.0, "thousand" to 1000.0, "ten_thousand" to 10_000.0,
            "equator" to 40_075.0, "km_100k" to 100_000.0, "km_250k" to 250_000.0, "half_million" to 500_000.0, "million" to 1_000_000.0, "expedition" to 50_000.0)
        for ((id, threshold) in distance) {
            assertFalse(badge(id).checkUnlocked(empty().copy(totalDistanceKm = threshold - 0.001)), id)
            assertTrue(badge(id).checkUnlocked(empty().copy(totalDistanceKm = threshold)), id)
        }
        for ((id, threshold) in mapOf("first_trip" to 1, "road_regular" to 10, "road_warrior" to 50, "trips_200" to 200,
            "trips_500" to 500, "legion" to 1000, "centurion" to 100, "early_tester" to 1)) {
            assertFalse(badge(id).checkUnlocked(empty().copy(totalTrips = threshold - 1)), id)
            assertTrue(badge(id).checkUnlocked(empty().copy(totalTrips = threshold)), id)
        }
        for ((id, threshold) in mapOf("explorer_5" to 5, "explorer_10" to 10, "explorer_25" to 25, "explorer_50" to 50, "explorer_100" to 100)) {
            assertFalse(badge(id).checkUnlocked(empty().copy(uniqueRegions = threshold - 1)), id)
            assertTrue(badge(id).checkUnlocked(empty().copy(uniqueRegions = threshold)), id)
        }
        for ((id, threshold) in mapOf("streak_3" to 3, "streak_7" to 7, "streak_14" to 14, "streak_30" to 30)) {
            assertFalse(badge(id).checkUnlocked(empty().copy(bestStreak = threshold - 1)), id)
            assertTrue(badge(id).checkUnlocked(empty().copy(bestStreak = threshold)), id)
        }
        val rich = empty().copy(totalTrips = 10000, totalDistanceKm = 1_000_000.0, uniqueRegions = 1000, bestStreak = 1000)
        for (id in BadgeDefinitions.externallyUnlockedIds + "veteran_2026") assertFalse(badge(id).checkUnlocked(rich))
    }
}

// iOS: BadgeNightWindowTests.swift / BadgeManager.swift.
class BadgeManagerTests {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-12-31T12:00:00Z")
    private fun stats(trips: List<Trip>) = BadgeManager.computeStats(trips, now, zone, WeekFields.ISO)
    private fun trip(hour: Int, durationHours: Double) = Trip(startDate = LocalDate.of(2026, 6, 10).atTime(hour, 0).toInstant(zone),
        endDate = LocalDate.of(2026, 6, 10).atTime(hour, 0).toInstant(zone).addingSeconds(durationHours * 3600), distance = 50_000.0)
    @Test fun testNightAndEarlyMorningWindowsTouchTheWholeTrip() {
        assertTrue(stats(listOf(trip(20, 11.0))).hasNightTrip)
        assertTrue(stats(listOf(trip(21, 3.0))).hasNightTrip)
        assertFalse(stats(listOf(trip(10, 3.0))).hasNightTrip)
        assertTrue(stats(listOf(trip(20, 11.0))).hasEarlyMorningTrip)
        assertTrue(stats(listOf(trip(10, 24.0))).hasLateNightTrip)
        assertTrue(stats(listOf(trip(10, 24.0))).hasAfterMidnightTrip)
        val end = Instant.parse("2026-06-10T22:00:00Z")
        val boundary = Trip(startDate = end.minusSeconds(1), endDate = end)
        assertTrue(stats(listOf(boundary)).hasNightTrip)
        assertFalse(stats(listOf(boundary.copy(endDate = end.minusSeconds(1)))).hasNightTrip)
    }
    @Test fun testAltitudeAndSeaLevelReuseTheMeasuredPointsGate() {
        val t = TrackTestKit.trip(listOf(TrackTestKit.PointSpec(north = 0.0, seconds = 0.0, altitude = 100.0),
            TrackTestKit.PointSpec(north = 10.0, seconds = 1.0, accuracy = 140.0, altitude = 900.0),
            TrackTestKit.PointSpec(north = 20.0, seconds = 2.0, altitude = 100.0)))
        assertNumber(100, stats(listOf(t)).maxAltitude)
        val low = t.trackPoints.first().copy(altitude = 5.0)
        val points = List(4) { low } + low.copy(altitude = 15.0)
        assertFalse(stats(listOf(t.copy(distance = 20_000.0, trackPoints = points))).hasSeaLevelTrip)
        assertTrue(stats(listOf(t.copy(distance = 20_000.0, trackPoints = points + low))).hasSeaLevelTrip)
        assertFalse(stats(listOf(t.copy(distance = 19_999.0, trackPoints = listOf(low)))).hasSeaLevelTrip)
    }
    @Test fun testWrappedYearVehicleCountsAndWeekendStreak() {
        val id = UUID.randomUUID()
        val year = (1..12).map { month -> Trip(startDate = LocalDate.of(2026, month, 1).atStartOfDay(zone).toInstant(),
            endDate = LocalDate.of(2026, month, 1).atTime(1, 0).toInstant(zone), vehicleId = id) }
        assertEquals(12, stats(year).distinctMonthsWithTrips)
        assertEquals(12, stats(year).maxTripsOnSingleVehicle)
        assertEquals(12, stats(year).privateTripCount)
        assertEquals(11, stats(year.dropLast(1) + year.last().copy(startDate = Instant.parse("2025-12-01T00:00:00Z"))).distinctMonthsWithTrips)
        val weekends = (0..3).map { Trip(startDate = LocalDate.of(2026, 6, 6).plusWeeks(it.toLong()).atStartOfDay(zone).toInstant()) }
        assertTrue(stats(weekends).hasWeekendWarrior)
        assertFalse(stats(weekends.take(3)).hasWeekendWarrior)
        assertFalse(stats(weekends.dropLast(1) + weekends.last().copy(startDate = weekends.last().startDate.plusSeconds(14 * 86400))).hasWeekendWarrior)
    }
    @Test fun testExternalBadgesSurviveRecomputationAndUnlockOnce() {
        val empty = stats(emptyList())
        val unlocked = BadgeManager.unlock("secret_komsomolsky", BadgeLedger())
        assertEquals(listOf("secret_komsomolsky"), unlocked.allEarned.map { it.id })
        assertTrue(BadgeManager.unlock("secret_komsomolsky", unlocked.ledger).allEarned.isEmpty())
        assertTrue(BadgeManager.unlock("unknown", unlocked.ledger).allEarned.isEmpty())
        assertEquals(setOf("secret_komsomolsky"), BadgeManager.checkNewBadges(empty, unlocked.ledger).ledger.unlockedIds)
        assertTrue(BadgeManager.unlockedBadges(empty, setOf("veteran_2026")).isEmpty())
    }
    @Test fun testRepeatableCountsMilestoneDiffAndManualTripGuard() {
        val t = trip(10, 1.0).copy(distance = 100_000.0)
        val first = BadgeManager.evaluateBadgesForTrip(t, listOf(t), BadgeLedger(), now, zone, WeekFields.ISO)
        assertTrue("first_trip" in first.allEarned.map { it.id }); assertEquals(1, first.repeatedBadgeCounts["marathon_42"])
        assertEquals(1, first.repeatedBadgeCounts["marathon_100"])
        val second = BadgeManager.evaluateBadgesForTrip(t, listOf(t), first.ledger, now, zone, WeekFields.ISO)
        assertFalse("first_trip" in second.allEarned.map { it.id }); assertEquals(2, second.repeatedBadgeCounts["marathon_42"])
        val manual = BadgeManager.evaluateBadgesForTrip(t.copy(source = TripOrigin.manual), listOf(t), second.ledger, now, zone, WeekFields.ISO)
        assertTrue(manual.allEarned.isEmpty()); assertTrue(manual.repeatedBadgeCounts.isEmpty()); assertEquals(second.ledger, manual.ledger)
        val later = t.copy(startDate = t.startDate.plusSeconds(86_400), earnedBadgeIds = listOf("marathon_42"))
        val dates = BadgeManager.lastEarnedDates(setOf("marathon_42"), listOf(later, t.copy(earnedBadgeIds = listOf("marathon_42"))))
        assertEquals(mapOf("marathon_42" to later.startDate), dates)
    }
}

// iOS: DiscoveryProcessorTests.swift (badge persistence rules, without track discovery or storage).
class DiscoveryBadgeTests {
    @Test fun testDiscoveryBadgeSurvivesARecomputeOfTheStatsBadges() {
        val empty = BadgeManager.computeStats(emptyList(), Instant.EPOCH, ZoneOffset.UTC, WeekFields.ISO)
        val opened = BadgeManager.unlock("riddle_first", BadgeLedger())
        assertEquals("riddle_first", opened.allEarned.single().id)
        assertTrue(BadgeManager.unlock("riddle_first", opened.ledger).allEarned.isEmpty())
        assertTrue(BadgeManager.unlock("no-such-badge", opened.ledger).allEarned.isEmpty())
        val refreshed = BadgeManager.checkNewBadges(empty, opened.ledger)
        assertTrue("riddle_first" in refreshed.ledger.unlockedIds)
        assertTrue(BadgeManager.unlockedBadges(empty, refreshed.ledger.unlockedIds).any { it.id == "riddle_first" })
    }
    @Test fun testDiscoveryBadgesAreHiddenExplorationBadges() {
        val empty = BadgeManager.computeStats(emptyList(), Instant.EPOCH, ZoneOffset.UTC, WeekFields.ISO)
        for (id in BadgeDefinitions.externallyUnlockedIds) {
            val badge = BadgeDefinitions.all.first { it.id == id }
            assertTrue(badge.isHidden); assertEquals(BadgeCategory.exploration, badge.category); assertFalse(badge.checkUnlocked(empty))
        }
    }
}
