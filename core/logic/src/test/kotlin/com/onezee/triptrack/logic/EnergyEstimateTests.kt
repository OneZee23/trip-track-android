package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.Instant
import kotlin.test.*

// iOS: EnergyEstimateTests.swift. Inputs and expected quantities match the iPhone fixtures.
class EnergyEstimateTests {
    private val cityMS = 30 / 3.6
    private fun petrol() = Vehicle(powertrain = Powertrain.fuel, cityConsumption = 10.0, highwayConsumption = 6.0, fuelPrice = 56.0)
    private fun electric() = petrol().copy(powertrain = Powertrain.electric, electricConsumption = 18.0, electricityPrice = 8.0)
    private fun hybrid(rangeKm: Double = 50.0) = electric().copy(powertrain = Powertrain.pluginHybrid, electricRangeKm = rangeKm)
    private fun estimate(v: Vehicle, metres: Double, mode: TripEnergyMode = TripEnergyMode.auto, used: Double = 0.0) =
        EnergyEstimate.resolve(v, metres, cityMS, mode, used)
    private fun trip(minutes: Int, km: Double, mode: TripEnergyMode = TripEnergyMode.auto) =
        Trip(startDate = Instant.ofEpochSecond(1_780_000_000).plusSeconds(minutes * 60L), distance = km * 1000, averageSpeed = cityMS, energyMode = mode)

    @Test fun testFuelVehicleReproducesTheOldNumbersExactly() {
        val v = petrol(); val old = v.fuelCost(42_000.0, cityMS); val new = estimate(v, 42_000.0)
        assertNumber(old.liters, new.litres, 1e-12); assertNumber(old.cost, new.fuelCost, 1e-12)
        assertNumber(42_000, new.fuelMetres); assertNumber(0, new.electricMetres); assertNumber(0, new.kWh); assertNull(new.electricCost)
    }
    @Test fun testFuelVehicleKeepsTheHighwayMix() {
        assertNumber(6, EnergyEstimate.resolve(petrol(), 100_000.0, 80 / 3.6, TripEnergyMode.auto).litres, 1e-9)
    }
    @Test fun testFuelAndElectricVehiclesIgnoreTheTripMode() {
        for (mode in TripEnergyMode.entries) {
            val fuel = estimate(petrol(), 10_000.0, mode)
            assertNumber(0, fuel.electricMetres); assertNumber(10_000, fuel.fuelMetres)
            assertNumber(10_000, estimate(electric(), 10_000.0, mode).electricMetres)
        }
    }
    @Test fun testElectricVehicleSpendsEverythingOnTheBattery() {
        val e = estimate(electric(), 100_000.0)
        assertNumber(100_000, e.electricMetres); assertNumber(0, e.fuelMetres); assertNumber(18, e.kWh, 1e-9)
        assertNumber(144, e.electricCost, 1e-9); assertNumber(0, e.litres); assertNull(e.fuelCost)
    }
    @Test fun testHybridFirstTripOfTheDayFitsInTheRange() {
        val e = estimate(hybrid(), 30_000.0)
        assertNumber(30_000, e.electricMetres); assertNumber(0, e.fuelMetres); assertNumber(5.4, e.kWh, 1e-9)
        assertNumber(0, e.litres); assertNull(e.fuelCost)
    }
    @Test fun testHybridSecondTripTakesWhatIsLeft() {
        val e = estimate(hybrid(), 45_000.0, used = 20_000.0)
        assertNumber(30_000, e.electricMetres); assertNumber(15_000, e.fuelMetres)
    }
    @Test fun testHybridTripLongerThanTheRangeSplitsAtTheRange() {
        val v = hybrid(); val e = estimate(v, 300_000.0)
        assertNumber(50_000, e.electricMetres); assertNumber(250_000, e.fuelMetres); assertNumber(9, e.kWh, 1e-9)
        assertNumber(v.fuelCost(250_000.0, cityMS).liters, e.litres, 1e-12)
    }
    @Test fun testHybridWithoutRemainingRangeRunsOnFuel() {
        for (e in listOf(estimate(hybrid(), 40_000.0, used = 90_000.0), estimate(hybrid(0.0), 40_000.0))) {
            assertNumber(0, e.electricMetres); assertNumber(40_000, e.fuelMetres); assertNumber(0, e.kWh)
        }
    }
    @Test fun testHybridExplicitModesIgnoreTheRange() {
        val electric = estimate(hybrid(), 300_000.0, TripEnergyMode.electric, 40_000.0)
        assertNumber(300_000, electric.electricMetres); assertNumber(0, electric.fuelMetres); assertNumber(0, electric.litres)
        val fuel = estimate(hybrid(), 20_000.0, TripEnergyMode.fuel)
        assertNumber(0, fuel.electricMetres); assertNumber(20_000, fuel.fuelMetres); assertNumber(0, fuel.kWh)
    }
    @Test fun testDaySpendsTheRangeInStartOrder() {
        val late = trip(600, 30.0); val early = trip(60, 30.0)
        val result = EnergyEstimate.day(hybrid(), listOf(late, early))
        assertNumber(30_000, result[early.id]?.electricMetres); assertNumber(20_000, result[late.id]?.electricMetres)
        assertNumber(10_000, result[late.id]?.fuelMetres)
    }
    @Test fun testDayNeverSpendsMoreThanTheRangeOnAuto() {
        val trips = listOf(trip(60, 20.0), trip(200, 20.0), trip(400, 20.0), trip(600, 20.0))
        assertNumber(50_000, EnergyEstimate.day(hybrid(), trips).values.sumOf { it.electricMetres }, 1e-9)
    }
    @Test fun testEarlyElectricTripEatsTheRangeForTheRest() {
        val first = trip(60, 80.0, TripEnergyMode.electric); val second = trip(600, 30.0)
        val result = EnergyEstimate.day(hybrid(), listOf(first, second))
        assertNumber(80_000, result[first.id]?.electricMetres); assertNumber(0, result[second.id]?.electricMetres)
        assertNumber(30_000, result[second.id]?.fuelMetres)
    }
    @Test fun testEarlyFuelTripLeavesTheRangeAlone() {
        val first = trip(60, 40.0, TripEnergyMode.fuel); val second = trip(600, 30.0)
        val result = EnergyEstimate.day(hybrid(), listOf(first, second))
        assertNumber(0, result[first.id]?.electricMetres); assertNumber(30_000, result[second.id]?.electricMetres)
    }
    @Test fun testDayIsDecidedByTheCaller() {
        val overnight = trip(23 * 60 + 30, 45.0)
        assertNumber(45_000, EnergyEstimate.day(hybrid(), listOf(overnight))[overnight.id]?.electricMetres)
    }
    @Test fun testMissingElectricityPriceGivesNoCost() {
        val e = estimate(hybrid().copy(electricityPrice = 0.0), 30_000.0)
        assertNumber(5.4, e.kWh, 1e-9); assertNull(e.electricCost); assertNull(e.totalCost)
    }
    @Test fun testOnlyThePricedPartCounts() {
        val e = estimate(hybrid().copy(electricityPrice = 0.0), 300_000.0)
        assertNull(e.electricCost); assertNotNull(e.fuelCost); assertEquals(e.fuelCost, e.totalCost)
    }
    @Test fun testBothPricesGiveTheSum() {
        val e = estimate(hybrid(), 300_000.0)
        assertNumber((e.electricCost ?: 0.0) + (e.fuelCost ?: 0.0), e.totalCost, 1e-9)
    }
    @Test fun testMissingConsumptionGivesNoEnergy() {
        val e = estimate(hybrid().copy(electricConsumption = 0.0), 30_000.0)
        assertNumber(30_000, e.electricMetres); assertNumber(0, e.kWh); assertNull(e.electricCost)
    }
    @Test fun testZeroDistanceGivesNothing() { assertEquals(EnergyEstimate.none, estimate(hybrid(), 0.0)) }
    @Test fun testPartsAlwaysAddUpToTheDistance() {
        for ((v, mode, metres) in listOf(Triple(petrol(), TripEnergyMode.auto, 12_345.0), Triple(electric(), TripEnergyMode.auto, 12_345.0),
            Triple(hybrid(), TripEnergyMode.auto, 12_345.0), Triple(hybrid(), TripEnergyMode.auto, 120_000.0),
            Triple(hybrid(), TripEnergyMode.electric, 120_000.0), Triple(hybrid(), TripEnergyMode.fuel, 120_000.0), Triple(hybrid(0.0), TripEnergyMode.auto, 12_345.0))) {
            val e = estimate(v, metres, mode); assertNumber(metres, e.electricMetres + e.fuelMetres, 1e-9)
        }
    }
}
