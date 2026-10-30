package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.WeekFields
import java.util.UUID
import kotlin.test.*

// iOS: ScoringStaysMetricTests.swift, VehicleUnitsStayOutOfRewardsTests.swift (pure intent).
class ScoringStaysMetricTests {
    private val start = Instant.ofEpochSecond(1_700_000_000)
    private val zone = ZoneOffset.UTC
    private fun longTrip() = Trip(startDate = start, endDate = start.plusSeconds(3 * 3600), distance = 200_000.0,
        maxSpeed = 33.0, averageSpeed = 18.5, region = "Krasnodar Krai")
    private fun stats(trips: List<Trip>) = BadgeManager.computeStats(trips, start, zone, WeekFields.ISO)
    @Test fun testXPAndCompletionStayMetricWhileDisplayChanges() {
        val trip = longTrip()
        val xp = GamificationManager.calculateXP(trip, listOf(trip), zone)
        assertNumber(200, xp.base); assertNumber(200, xp.longTripBonus); assertNumber(570, xp.total)
        assertNumber(1570, 1000 + xp.total)
        assertNumber(700, GamificationManager.completedVehicleOdometer(500.0, trip))
        assertEquals(4, GamificationManager.completedVehicleLevel(700.0, 0.0))
        assertEquals("200 km", Measure.distance(trip.distance, DistanceUnit.km, "en", IosMeasureWords, Measure.Style.grouped))
        assertEquals("124 mi", Measure.distance(trip.distance, DistanceUnit.miles, "en", IosMeasureWords, Measure.Style.grouped))
        assertNumber(200, trip.scoringKm, 0.0001); assertNumber(200, trip.rewardKm, 0.0001)
        assertNumber(200, trip.copy(source = TripOrigin.manual).scoringKm, 0.0001)
        assertNumber(0, trip.copy(source = TripOrigin.manual).rewardKm)
        val s = stats(listOf(trip)); assertNumber(200, s.totalDistanceKm, 0.0001)
        assertNumber(200, s.longestTripKm, 0.0001); assertNumber(118.8, s.maxSpeedKmh, 0.0001)
    }
    @Test fun testVehicleAndBadgeMathCannotReceiveADisplayUnit() {
        val banned = setOf(DistanceUnit::class.java, DashboardUnits::class.java, ConsumptionUnit::class.java,
            ElectricUnit::class.java, Powertrain::class.java, EnergyEstimate::class.java, Vehicle::class.java)
        for (type in listOf(GamificationManager::class.java, LevelSystem::class.java, VehicleLevelSystem::class.java,
            VehicleOdometer::class.java, BadgeManager::class.java))
            for (method in type.declaredMethods) assertTrue(method.parameterTypes.none { it in banned }, method.name)
        val car = UUID.randomUUID()
        val six = (0 until 6).map { Trip(startDate = start.plusSeconds(it * 86_400L), endDate = start.plusSeconds(it * 86_400L + 3600), distance = 100_000.0, vehicleId = car) }
        assertNumber(600, VehicleOdometer.tracked(six, car), 0.0001)
        assertEquals(4, VehicleLevelSystem.level(VehicleOdometer.tracked(six, car)))
        val marathon = Trip(startDate = start, endDate = start.plusSeconds(2600), distance = 42_200.0, maxSpeed = 30.0, averageSpeed = 16.0)
        val expected = BadgeManager.unlockedBadges(stats(listOf(marathon)), emptySet()).map { it.id }.toSet()
        assertTrue("marathon_42" in expected)
        for (dashboard in DashboardUnits.entries) {
            val vehicle = Vehicle(name = "Тойота", odometerKm = 100_000.0, manualOdometerKm = 142_000.0,
                level = VehicleLevelSystem.level(100_000.0), dashboardUnits = dashboard)
            assertNumber(100_000, vehicle.levelSourceKm); assertNotEquals(vehicle.manualOdometerKm, vehicle.levelSourceKm)
            assertEquals(VehicleLevelSystem.progressToNext(100_000.0, vehicle.level), vehicle.progressToNextLevel)
            assertEquals(VehicleLevelSystem.kmToNextLevel(100_000.0, vehicle.level), vehicle.kmToNextLevel)
            assertEquals(expected, BadgeManager.unlockedBadges(stats(listOf(marathon.copy(vehicleId = vehicle.id))), emptySet()).map { it.id }.toSet())
        }
        assertNotEquals(VehicleLevelSystem.level(100_000.0), VehicleLevelSystem.level(142_000.0))
        assertNumber(120, VehicleOdometer.tracked(listOf(
            Trip(startDate = start, endDate = start.plusSeconds(3600), distance = 50_000.0, vehicleId = car),
            Trip(startDate = start, endDate = start.plusSeconds(7200), distance = 70_000.0, vehicleId = car)), car), 0.000001)
    }
    @Test fun testManualTripsCountInOdometersButNotRewards() {
        val car = UUID.randomUUID()
        val hand = longTrip().copy(distance = 300_000.0, source = TripOrigin.manual)
        assertNumber(0, GamificationManager.calculateXP(hand, listOf(hand), zone).total)
        assertTrue(GamificationManager.calculateXP(hand.copy(source = TripOrigin.recorded), listOf(hand), zone).total > 0)
        val manual = hand.copy(distance = 600_000.0, maxSpeed = 40.0, averageSpeed = 40.0)
        assertNumber(0, stats(listOf(manual)).totalDistanceKm); assertNumber(0, stats(listOf(manual)).maxSpeedKmh)
        assertTrue(stats(listOf(manual.copy(source = TripOrigin.recorded))).totalDistanceKm > 0)
        assertTrue(stats(listOf(manual.copy(source = TripOrigin.recorded))).maxSpeedKmh > 0)
        assertNumber(120, VehicleOdometer.trackedByVehicle(listOf(hand.copy(distance = 120_000.0, vehicleId = car)))[car], 0.001)
        val trips = listOf(hand.copy(distance = 5_000_000.0, vehicleId = car), longTrip().copy(distance = 300_000.0, vehicleId = car))
        assertNumber(5300, VehicleOdometer.trackedByVehicle(trips)[car], 0.001)
        assertNumber(300, VehicleOdometer.rewardByVehicle(trips)[car], 0.001)
        assertTrue(VehicleLevelSystem.level(5300.0) > VehicleLevelSystem.level(300.0))
        assertNumber(1200, Vehicle(odometerKm = 1200.0).levelSourceKm, 0.001)
        assertNumber(300, Vehicle(odometerKm = 5300.0, rewardOdometerKm = 300.0).levelSourceKm, 0.001)
    }
    @Test fun testFirstDayAndNewRegionRulesUseOtherTrips() {
        val trip = longTrip()
        val other = trip.copy(id = UUID.randomUUID(), startDate = start.minusSeconds(60))
        assertNumber(0, GamificationManager.calculateXP(trip, listOf(other, trip), zone).firstTripOfDay)
        assertNumber(0, GamificationManager.calculateXP(trip, listOf(other, trip), zone).regionDiscovery)
        val tiny = trip.copy(distance = 400.0, region = null)
        assertNumber(1, GamificationManager.calculateXP(tiny, listOf(tiny), zone).base)
        // The original backfill counts distance and region discovery, without the +20 finish bonus.
        assertNumber(550, GamificationManager.backfillXP(listOf(trip)))
        assertNumber(0, GamificationManager.backfillXP(listOf(trip.copy(source = TripOrigin.manual))))
        assertEquals(21, GamificationManager.newProfileLevel(21, 0))
    }
}

// iOS: VehicleLevelSystemTests.swift (math part).
class VehicleLevelSystemTests {
    @Test fun testSpecExamplesAndNoMaximum() {
        assertNumber(0, VehicleLevelSystem.kmForLevel(1)); assertNumber(100, VehicleLevelSystem.kmForLevel(2))
        assertEquals(1, VehicleLevelSystem.level(99.9)); assertEquals(2, VehicleLevelSystem.level(100.0))
        assertNumber(1000, VehicleLevelSystem.kmForLevel(11) - VehicleLevelSystem.kmForLevel(10))
        assertEquals(28, VehicleLevelSystem.level(38_420.0)); assertEquals(2, VehicleLevelSystem.level(295.0)); assertEquals(3, VehicleLevelSystem.level(300.0))
        val km = 50.0 * 200 * 199
        assertEquals(200, VehicleLevelSystem.level(km)); assertTrue(VehicleLevelSystem.kmToNextLevel(km, 200) > 0)
        assertTrue(VehicleLevelSystem.progressToNext(km, 200) < 1)
    }
    @Test fun testThresholdsRoundTripExactly() {
        for (level in 1..120) {
            val km = VehicleLevelSystem.kmForLevel(level); assertEquals(level, VehicleLevelSystem.level(km))
            if (level > 1) assertEquals(level - 1, VehicleLevelSystem.level(km - 0.001))
        }
    }
    @Test fun testProgressSpansTheStepAndZeroOdometer() {
        val start = VehicleLevelSystem.kmForLevel(5); val end = VehicleLevelSystem.kmForLevel(6)
        assertNumber(0, VehicleLevelSystem.progressToNext(start, 5), 0.0001)
        assertNumber(0.5, VehicleLevelSystem.progressToNext((start + end) / 2, 5), 0.0001)
        assertNumber(1, VehicleLevelSystem.progressToNext(end, 5), 0.0001)
        assertEquals(1, VehicleLevelSystem.level(0.0)); assertNumber(0, VehicleLevelSystem.progressToNext(0.0, 1))
        assertNumber(100, VehicleLevelSystem.kmToNextLevel(0.0, 1))
    }
}

// iOS: GamificationModels.swift, DriverRankTests.swift (rank mapping).
class LevelSystemTests {
    @Test fun testFrozenLevelsAndExtendedCurve() {
        val frozen = listOf(0, 50, 150, 300, 500, 800, 1200, 1700, 2300, 3000, 4000, 5200, 6600, 8200, 10000,
            12000, 14500, 17000, 19500, 22000, 25000, 28500, 32000, 35500, 39000, 43000, 48000, 53000, 58000, 63000)
        for ((index, xp) in frozen.withIndex()) assertEquals(xp, LevelSystem.xpForLevel(index + 1))
        assertEquals(68_600, LevelSystem.xpForLevel(31)); assertEquals(2_407_000, LevelSystem.xpForLevel(110))
        assertEquals(LevelSystem.xpForLevel(110), LevelSystem.xpForLevel(111))
        for (level in 1..110) {
            assertEquals(level, LevelSystem.level(LevelSystem.xpForLevel(level)))
            if (level > 1) assertEquals(level - 1, LevelSystem.level(LevelSystem.xpForLevel(level) - 1))
        }
        assertEquals(110, LevelSystem.level(Int.MAX_VALUE)); assertEquals(1, LevelSystem.level(-1))
        assertNumber(0.5, LevelSystem.progressToNextLevel(100, 2)); assertNumber(1, LevelSystem.progressToNextLevel(0, 110))
        assertEquals(2_407_000, LevelSystem.xpForNextLevel(110))
        for ((level, rank) in listOf(1 to DriverRank.novice, 5 to DriverRank.driver, 10 to DriverRank.traveler,
            15 to DriverRank.explorer, 20 to DriverRank.navigator, 25 to DriverRank.trucker, 30 to DriverRank.legend)) assertEquals(rank, DriverRank.from(level))
        assertEquals(DriverRank.eternal, DriverRank.entries.last())
    }
}

// iOS: TransferAndOdometerTests.swift / VehicleOdometerTests.swift (pure recomputation).
class VehicleOdometerTests {
    private fun trip(km: Double, vehicle: UUID?, transfer: Boolean = false) = Trip(distance = km * 1000, vehicleId = vehicle, isTransfer = transfer)
    @Test fun testTransfersAndOrdinaryTrips() {
        val car = UUID.randomUUID()
        assertNumber(100, VehicleOdometer.tracked(listOf(trip(100.0, car), trip(400.0, car, true)), car), 0.01)
        assertNumber(150, VehicleOdometer.tracked(listOf(trip(100.0, car), trip(50.0, car)), car), 0.01)
        assertNumber(0, VehicleOdometer.tracked(listOf(trip(300.0, null, true)), car), 0.01)
        assertTrue(VehicleOdometer.rewardByVehicle(listOf(trip(400.0, car, true))).isEmpty())
    }
    @Test fun testReassignmentRemovalAndRecomputation() {
        val a = UUID.randomUUID(); val b = UUID.randomUUID(); val t = trip(300.0, a)
        assertNumber(300, VehicleOdometer.tracked(listOf(t), a), 0.001)
        assertNumber(0, VehicleOdometer.tracked(listOf(t.copy(vehicleId = b)), a), 0.001)
        assertNumber(300, VehicleOdometer.tracked(listOf(t.copy(vehicleId = b)), b), 0.001)
        assertNumber(0, VehicleOdometer.tracked(listOf(trip(120.0, null)), a), 0.001)
        assertNumber(250, VehicleOdometer.tracked(listOf(trip(250.0, a)), a), 0.001)
        assertEquals(VehicleLevelSystem.level(250.0), VehicleLevelSystem.level(VehicleOdometer.tracked(listOf(trip(250.0, a)), a)))
        val keep = trip(100.0, a); val drop = trip(400.0, a)
        assertNumber(500, VehicleOdometer.tracked(listOf(keep, drop), a), 0.001)
        assertNumber(100, VehicleOdometer.tracked(listOf(keep), a), 0.001)
        val map = VehicleOdometer.trackedByVehicle(listOf(trip(1000.0, a), trip(500.0, a), trip(800.0, b)))
        assertNumber(1500, map[a], 0.001); assertNumber(800, map[b], 0.001)
    }
    @Test fun testManualDashboardOdometerAndGap() {
        val v = Vehicle(odometerKm = 12_000.0, manualOdometerKm = 143_500.0)
        assertNumber(143_500, v.displayOdometerKm, 0.01); assertNumber(12_000, v.odometerKm, 0.01)
        assertNumber(131_500, v.untrackedKm, 0.01)
        val plain = Vehicle(odometerKm = 12_000.0)
        assertNull(plain.manualOdometerKm); assertNumber(12_000, plain.displayOdometerKm, 0.01); assertNull(plain.untrackedKm)
        assertNull(Vehicle(odometerKm = 20_000.0, manualOdometerKm = 15_000.0).untrackedKm)
        assertNumber(500, Vehicle(odometerKm = 500.0, manualOdometerKm = 250_000.0).levelSourceKm, 0.01)
    }
}

// iOS: TripLevelHistoryTests.swift
class TripLevelHistoryTests {
    private val now = Instant.ofEpochSecond(1_800_000_000)
    private fun trip(daysAgo: Int, xp: Int) = Trip(startDate = now.minusSeconds(daysAgo * 86400L), xpEarned = xp)
    @Test fun testLevelsRiseWithTheHistoryAndDoNotDependOnInputOrder() {
        val oldest = trip(30, 60); val middle = trip(20, 100); val newest = trip(10, 200)
        val levels = assertNotNull(TripLevelHistory.levels(listOf(newest, oldest, middle)))
        assertEquals(2, levels[oldest.id]); assertEquals(3, levels[middle.id]); assertEquals(4, levels[newest.id])
        assertEquals(TripLevelHistory.levels(listOf(oldest, middle, newest)), TripLevelHistory.levels(listOf(newest, middle, oldest)))
    }
    @Test fun testATripThatLevelsYouUpShowsTheNewLevel() {
        val first = trip(2, 49); val second = trip(1, 1)
        val levels = assertNotNull(TripLevelHistory.levels(listOf(first, second)))
        assertEquals(1, levels[first.id]); assertEquals(2, levels[second.id])
    }
    @Test fun testAHistoryWithoutXPReturnsNull() {
        assertNull(TripLevelHistory.levels(listOf(trip(3, 0), trip(1, 0))))
        assertNull(TripLevelHistory.levels(emptyList()))
    }
    @Test fun testAPartiallyStampedHistoryStillReconstructs() {
        val old = trip(5, 0); val new = trip(1, 200)
        val levels = assertNotNull(TripLevelHistory.levels(listOf(old, new)))
        assertEquals(1, levels[old.id]); assertEquals(LevelSystem.level(200), levels[new.id])
    }
    @Test fun testNegativeXPCannotPullTheLevelDown() {
        val a = trip(2, 200); val b = trip(1, -500)
        val levels = assertNotNull(TripLevelHistory.levels(listOf(a, b)))
        assertEquals(levels[a.id], levels[b.id])
    }
}

// iOS: GamificationManager.swift, updateStreak. Backdated recovery must not rewind the streak.
class GamificationStreakTests {
    private val date = Instant.parse("2026-06-10T12:00:00Z")
    @Test fun testSameDayNextDayGapAndBackdatedTrip() {
        val first = GamificationManager.updateStreak(TripStreak(), date, ZoneOffset.UTC)
        assertEquals(TripStreak(1, 1, date), first)
        assertEquals(first, GamificationManager.updateStreak(first, date.plusSeconds(60), ZoneOffset.UTC))
        assertEquals(first, GamificationManager.updateStreak(first, date.minusSeconds(86400), ZoneOffset.UTC))
        val next = GamificationManager.updateStreak(first, date.plusSeconds(86400), ZoneOffset.UTC)
        assertEquals(2, next.current); assertEquals(2, next.best)
        val gap = GamificationManager.updateStreak(next, date.plusSeconds(4 * 86400), ZoneOffset.UTC)
        assertEquals(1, gap.current); assertEquals(2, gap.best)
    }
}
