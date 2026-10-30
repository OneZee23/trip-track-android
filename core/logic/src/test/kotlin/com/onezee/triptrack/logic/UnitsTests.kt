package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.util.Locale
import kotlin.math.abs
import kotlin.test.*

// iOS: DistanceUnitTests.swift, OdometerFieldTests.swift, VehicleConsumptionUnitTests.swift.
class DistanceUnitTests {
    @Test fun testStoredContractAndExactConstants() {
        assertEquals(listOf("km", "miles"), DistanceUnit.entries.map { it.rawValue })
        assertEquals("distanceUnit", DistanceUnit.storageKey)
        for (raw in listOf("kilometres", "", "MILES")) assertNull(DistanceUnit.fromRawValue(raw))
        assertNumber(1609.344, DistanceUnit.metresPerMile)
        assertNumber(0.3048, DistanceUnit.metresPerFoot)
    }
    @Test fun testDistanceConversions() {
        assertNumber(1, DistanceUnit.miles.distance(1609.344), 1e-12)
        assertNumber(26.2188, DistanceUnit.miles.distance(42_195.0), 0.0001)
        assertNumber(62.137, DistanceUnit.miles.distance(100_000.0), 0.001)
        assertNumber(1.2345, DistanceUnit.km.distance(1234.5))
        assertNumber(1234.5, DistanceUnit.km.metres(1.2345))
        for (unit in DistanceUnit.entries) {
            for (m in listOf(0.0, 1.0, 5.0, 400.0, 999.5, 1609.344, 12_345.6, 100_000.0, 987_654.321, 40_075_000.0, -5000.0))
                assertNumber(m, unit.metres(unit.distance(m)), maxOf(1e-9, abs(m) * 1e-12))
            assertNumber(0, unit.distance(0.0)); assertNumber(0, unit.metres(0.0))
            assertTrue(unit.distance(-5000.0) < 0)
        }
    }
    @Test fun testSpeedAndElevation() {
        assertNumber(3.6, DistanceUnit.km.speed(1.0))
        assertNumber(2.23694, DistanceUnit.miles.speed(1.0), 0.00001)
        assertNumber(62.137, DistanceUnit.miles.speed(100 / 3.6), 0.001)
        for (unit in DistanceUnit.entries) for (ms in listOf(0.0, 1.0, 5.5, 13.9, 27.8, 83.0))
            assertNumber(ms, unit.metresPerSecond(unit.speed(ms)), 1e-9)
        for (m in listOf(0.0, 1.0, 640.0, 2500.0, -12.0)) assertEquals(m, DistanceUnit.km.elevation(m))
        assertNumber(1, DistanceUnit.miles.elevation(0.3048), 1e-12)
        assertNumber(3280.84, DistanceUnit.miles.elevation(1000.0), 0.01)
        assertNumber(2099.74, DistanceUnit.miles.elevation(640.0), 0.01)
    }
    @Test fun testThresholdAndRegionGuesses() {
        assertNumber(10, DistanceUnit.km.tenthsBelow); assertNumber(6, DistanceUnit.miles.tenthsBelow)
        assertNumber(DistanceUnit.miles.tenthsBelow, DistanceUnit.miles.distance(10_000.0), 0.3)
        for (region in listOf("US", "GB", "LR", "MM")) assertEquals(DistanceUnit.miles, DistanceUnit.guessFromRegion(Locale.forLanguageTag("en-$region")))
        for (region in listOf("AU", "CA", "IN", "IE", "NZ", "ZA", "RU", "DE", "FR", "KZ", "UA", "BR", "TR", "ID", ""))
            assertEquals(DistanceUnit.km, DistanceUnit.guessFromRegion(Locale.forLanguageTag("en-$region")))
        assertEquals(DistanceUnit.km, DistanceUnit.guessFromRegion(Locale.ROOT))
    }
    @Test fun testDashboardResolutionAndTolerantParsing() {
        for (app in DistanceUnit.entries) {
            assertEquals(app, DashboardUnits.app.resolved(app))
            assertEquals(DistanceUnit.km, DashboardUnits.metric.resolved(app))
            assertEquals(DistanceUnit.miles, DashboardUnits.imperial.resolved(app))
            assertEquals(ConsumptionUnit.forDashboard(app), Vehicle().consumptionUnit(app))
        }
        assertEquals(DashboardUnits.metric, DashboardUnits.parse(" METRIC "))
        assertEquals(DashboardUnits.imperial, DashboardUnits.parse("imperial"))
        for (raw in listOf(null, "", "imperial_uk")) assertNull(DashboardUnits.parse(raw))
    }
}

// iOS: OdometerFieldTests.swift
class OdometerFieldTests {
    @Test fun testTheFieldUsesTheVehicleDashboard() {
        val toyota = Vehicle(name = "Тойота", dashboardUnits = DashboardUnits.metric)
        assertNumber(142_000, OdometerField.storedKm("142000", toyota.dashboardUnit(DistanceUnit.miles)))
        assertNotEquals(OdometerField.storedKm("142000", DistanceUnit.km), OdometerField.storedKm("142000", DistanceUnit.miles))
        val mustang = Vehicle(name = "Мустанг", dashboardUnits = DashboardUnits.imperial)
        assertNumber(142_000, OdometerField.storedKm("88235", mustang.dashboardUnit(DistanceUnit.km)), 1.0)
        assertNumber(142_000, OdometerField.storedKm("142000", Vehicle().dashboardUnit(DistanceUnit.km)))
        assertNumber(228_527, OdometerField.storedKm("142000", Vehicle().dashboardUnit(DistanceUnit.miles)), 1.0)
    }
    @Test fun testParseAndDisplay() {
        assertNumber(0, OdometerField.storedKm("0", DistanceUnit.km))
        assertNumber(38_420, OdometerField.storedKm("38420", DistanceUnit.km))
        assertNumber(1.609344, OdometerField.storedKm("1", DistanceUnit.miles), 1e-9)
        assertNumber(100_000, OdometerField.storedKm("62137", DistanceUnit.miles), 1.0)
        for (text in listOf("", "   ", "—", "12 000")) for (unit in DistanceUnit.entries) assertNull(OdometerField.storedKm(text, unit))
        assertEquals("142000", OdometerField.fieldText(142_000.0, DistanceUnit.km))
        assertEquals("88235", OdometerField.fieldText(142_000.0, DistanceUnit.miles))
        assertEquals("", OdometerField.fieldText(null, DistanceUnit.km))
        for (unit in DistanceUnit.entries) for (km in listOf(0.0, 1.0, 38_420.0, 100_000.0, 142_000.0, 999_999.0))
            assertNumber(km, OdometerField.storedKm(OdometerField.fieldText(km, unit), unit), unit.metres(1.0) / 1000)
        val back = OdometerField.storedKm(OdometerField.fieldText(100_000.0, DistanceUnit.miles), DistanceUnit.miles)
        assertNotEquals(100_000.0, back); assertNumber(100_000, back, 2.0)
    }
}

// iOS: ConsumptionUnitTests.swift / ElectricUnitTests.swift
class ConsumptionUnitTests {
    @Test fun testFuelConversionAndPrice() {
        val mpg = ConsumptionUnit.mpg
        assertNumber(25.02, mpg.display(9.4), 0.05); assertNumber(42, mpg.display(5.6), 0.05)
        assertNumber(9.41, mpg.toPer100(25.0), 0.01); assertNumber(5.6, mpg.toPer100(42.0), 0.01)
        for (value in listOf(0.0, 4.2, 9.1, 22.0)) {
            assertEquals(value, ConsumptionUnit.per100.display(value)); assertEquals(value, ConsumptionUnit.per100.toPer100(value))
        }
        for (unit in ConsumptionUnit.entries) {
            for (stored in listOf(4.2, 6.0, 7.8, 9.1, 14.5, 22.0)) assertNumber(stored, unit.toPer100(unit.display(stored)), 0.0001)
            assertNumber(0, unit.display(0.0)); assertNumber(0, unit.toPer100(0.0)); assertFalse(unit.display(0.0).isInfinite())
        }
        assertTrue(mpg.display(15.0) < mpg.display(5.0))
        assertNumber(50, ConsumptionUnit.per100.inputCeiling); assertNumber(250, mpg.inputCeiling)
        assertEquals(VolumeUnit.gallons, mpg.volumeUnit); assertEquals(VolumeUnit.liters, ConsumptionUnit.per100.volumeUnit)
        assertNumber(246.05, mpg.displayPrice(65.0), 0.01); assertNumber(65, ConsumptionUnit.per100.displayPrice(65.0))
        for (perLitre in listOf(42.0, 56.0, 65.0, 98.5)) assertNumber(perLitre, mpg.priceToPerLitre(mpg.displayPrice(perLitre)), 0.0001)
        assertTrue(mpg.displayPrice(65.0) > 65); assertTrue(mpg.display(9.4) > 9.4)
        assertTrue(abs(mpg.displayPrice(9.4) - mpg.display(9.4)) > 0.001)
        assertNumber(3.785411784, ConsumptionUnit.litresPerGallon); assertNumber(235.214583, ConsumptionUnit.mpgConstant, 1e-6)
    }
    @Test fun testFuelAndElectricLabels() {
        for (lang in UnitNumber.languages) {
            val label = ConsumptionUnit.per100.valueUnit(lang, IosMeasureWords)
            val km = Measure.unitDistanceShort(lang, DistanceUnit.km, 100.0, 0, IosMeasureWords)
            val mile = Measure.unitDistanceShort(lang, DistanceUnit.miles, 100.0, 0, IosMeasureWords)
            assertTrue(label.endsWith("100$km")); assertFalse(label.contains(mile))
            assertEquals("mpg", ConsumptionUnit.mpg.valueUnit(lang, IosMeasureWords))
            val metric = ElectricUnit.per100kWh.valueUnit(lang, IosMeasureWords)
            assertTrue(metric.isNotEmpty()); assertEquals("mi/kWh", ElectricUnit.miPerKWh.valueUnit(lang, IosMeasureWords))
            assertNotEquals(metric, ElectricUnit.miPerKWh.valueUnit(lang, IosMeasureWords))
        }
        assertEquals("л/100км", ConsumptionUnit.per100.valueUnit("ru", IosMeasureWords))
        assertEquals("L/100km", ConsumptionUnit.per100.valueUnit("en", IosMeasureWords))
        for ((lang, word) in listOf("ru" to "кВт·ч", "kk" to "кВт·сағ", "uk" to "кВт·год"))
            assertTrue(ElectricUnit.per100kWh.valueUnit(lang, IosMeasureWords).contains(word))
        assertTrue(ElectricUnit.per100kWh.valueUnit("ru", IosMeasureWords).contains("100"))
    }
    @Test fun testElectricConversion() {
        val mi = ElectricUnit.miPerKWh
        assertEquals(ElectricUnit.per100kWh, ElectricUnit.forDashboard(DistanceUnit.km))
        assertEquals(mi, ElectricUnit.forDashboard(DistanceUnit.miles))
        assertNumber(62.137119 / 16, mi.display(16.0), 0.0001)
        assertNumber(16, ElectricUnit.per100kWh.display(16.0)); assertNumber(16, ElectricUnit.per100kWh.toPer100(16.0))
        for (stored in listOf(1.0, 12.5, 16.0, 23.7, 60.0)) assertNumber(stored, mi.toPer100(mi.display(stored)), 0.000001)
        for (unit in ElectricUnit.entries) { assertNumber(0, unit.display(0.0)); assertNumber(0, unit.toPer100(0.0)) }
        assertNumber(0, mi.display(-5.0)); assertNumber(0, mi.toPer100(-5.0))
        assertNumber(DistanceUnit.miles.distance(100_000.0 / 16), mi.display(16.0), 0.000001)
        assertNumber(60, ElectricUnit.per100kWh.inputCeiling); assertNumber(12, mi.inputCeiling)
        assertTrue(mi.display(16.0) < mi.inputCeiling); assertTrue(16 < ElectricUnit.per100kWh.inputCeiling)
    }
}
