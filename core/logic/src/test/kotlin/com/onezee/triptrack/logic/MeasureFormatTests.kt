package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import kotlin.test.*

// iOS: MeasureFormatTests.swift / UnitsDisciplineTests.swift (pure formatting intent).
class MeasureFormatTests {
    private fun text(metres: Double, unit: DistanceUnit, lang: String, style: Measure.Style = Measure.Style.adaptive) =
        Measure.distance(metres, unit, lang, IosMeasureWords, style)
    private fun value(metres: Double, unit: DistanceUnit, lang: String, style: Measure.Style = Measure.Style.adaptive) =
        Measure.distanceValue(metres, unit, lang, style)
    private fun parse(text: String) = text.replace("\u00a0", "").replace("\u202f", "").replace(" ", "").replace(",", "").toDouble()

    @Test fun testEveryLanguageUnitAndStyle() {
        for (lang in UnitNumber.languages) for (unit in DistanceUnit.entries) for (style in Measure.Style.entries) {
            val text = text(128_400.0, unit, lang, style)
            assertTrue(text.isNotEmpty()); assertFalse(text.lowercase().contains("nan")); assertFalse(text.lowercase().contains("inf"))
            assertTrue(text.contains(' ')); assertTrue(text.any { it.isDigit() })
            assertTrue(text.startsWith(value(128_400.0, unit, lang, style) + " "))
            val parts = Measure.distanceParts(128_400.0, unit, lang, IosMeasureWords, style)
            assertEquals(text, parts.value + " " + parts.unit)
        }
    }
    @Test fun testNonFiniteInputDoesNotCrash() {
        for (unit in DistanceUnit.entries) {
            for (style in Measure.Style.entries) for (input in listOf(Double.NaN, Double.POSITIVE_INFINITY))
                assertTrue(value(input, unit, "ru", style).isNotEmpty())
            assertTrue(Measure.speedValue(Double.NaN, unit, "en").isNotEmpty())
        }
    }
    @Test fun testTenthsAndGroupedPrecision() {
        for (lang in UnitNumber.languages) for (unit in DistanceUnit.entries) {
            for (metres in listOf(0.0, 400.0, 9900.0, 128_400.0)) {
                val parts = value(metres, unit, lang, Measure.Style.tenths).split('.')
                assertEquals(2, parts.size); assertEquals(1, parts.last().length)
            }
            assertFalse(value(128_400.0, unit, lang, Measure.Style.grouped).contains('.'))
            for (shown in listOf(5.0, 142.0, 4.98)) assertFalse(value(unit.metres(shown), unit, lang).endsWith(".0"))
            assertEquals(".", UnitNumber.decimalSeparator(lang))
            val tenths = value(8400.0, unit, lang, Measure.Style.tenths)
            assertTrue(tenths.contains('.')); assertFalse(tenths.contains(','))
        }
        assertEquals("5.0", value(5000.0, DistanceUnit.km, "ru", Measure.Style.tenths))
        assertEquals("0.0", value(0.0, DistanceUnit.km, "en", Measure.Style.tenths))
        assertEquals("8.4", value(8400.0, DistanceUnit.km, "ru", Measure.Style.tenths))
        assertEquals("8.4", value(8400.0, DistanceUnit.km, "de", Measure.Style.tenths))
    }
    @Test fun testAdaptiveThresholdIsTenKilometresAndSixMiles() {
        for ((metres, km, miles) in listOf(Triple(9900.0, "9.9", "6"), Triple(9300.0, "9.3", "5.8"), Triple(11_000.0, "11", "7"))) {
            assertEquals(km, value(metres, DistanceUnit.km, "ru")); assertEquals(miles, value(metres, DistanceUnit.miles, "ru"))
        }
        assertEquals("5 миль", text(5 * DistanceUnit.metresPerMile, DistanceUnit.miles, "ru"))
        assertEquals("2 мили", text(2 * DistanceUnit.metresPerMile, DistanceUnit.miles, "ru"))
    }
    @Test fun testGroupingAndUpToTenth() {
        assertEquals("12\u00a0345", value(12_345_000.0, DistanceUnit.km, "ru", Measure.Style.grouped))
        assertEquals("12,345", value(12_345_000.0, DistanceUnit.km, "en", Measure.Style.grouped))
        for ((number, lang, expected) in listOf(Triple(1.0, "ru", "1"), Triple(1.5, "ru", "1.5"), Triple(9.96, "ru", "10"), Triple(0.44, "de", "0.4"), Triple(0.0, "en", "0")))
            assertEquals(expected, UnitNumber.upToTenth(number, lang))
        for (lang in UnitNumber.languages) assertEquals("8.4", UnitNumber.upToTenth(8.4, lang))
        for (metres in listOf(42_195.0, 100_000.0, 250_000.0, 1_000_000.0))
            assertNumber(DistanceUnit.metresPerMile / 1000,
                parse(value(metres, DistanceUnit.km, "en", Measure.Style.tenths)) / parse(value(metres, DistanceUnit.miles, "en", Measure.Style.tenths)), 0.005)
    }
    @Test fun testLabelsAgreeWithRoundedNumbers() {
        assertEquals("5.2 мили", text(8400.0, DistanceUnit.miles, "ru", Measure.Style.tenths))
        assertEquals("5 миль", text(8000.0, DistanceUnit.miles, "ru", Measure.Style.grouped))
        assertEquals("21 миля", text(21 * DistanceUnit.metresPerMile, DistanceUnit.miles, "ru", Measure.Style.grouped))
        assertEquals("128 км", text(128_400.0, DistanceUnit.km, "ru", Measure.Style.grouped))
        assertEquals("80 mi", text(128_400.0, DistanceUnit.miles, "en", Measure.Style.grouped))
    }
    @Test fun testSpeedElevationAndOdometer() {
        assertEquals("100 км/ч", Measure.speed(100 / 3.6, DistanceUnit.km, "ru", IosMeasureWords))
        assertEquals("62 mph", Measure.speed(100 / 3.6, DistanceUnit.miles, "en", IosMeasureWords))
        assertEquals("0 миль/ч", Measure.speed(0.0, DistanceUnit.miles, "ru", IosMeasureWords))
        assertEquals("640 м", Measure.elevation(640.0, DistanceUnit.km, "ru", IosMeasureWords))
        assertEquals("2,100 ft", Measure.elevation(640.0, DistanceUnit.miles, "en", IosMeasureWords))
        assertNumber(2099.7, parse(Measure.elevationValue(640.0, DistanceUnit.miles, "en")), 1.0)
        assertEquals("38\u00a0420 км", Measure.odometer(38_420.0, DistanceUnit.km, "ru", IosMeasureWords))
        assertEquals("23,873 mi", Measure.odometer(38_420.0, DistanceUnit.miles, "en", IosMeasureWords))
    }
    @Test fun testSwiftRoundingAndClamping() {
        assertEquals("1", value(500.0, DistanceUnit.km, "en", Measure.Style.grouped))
        assertEquals("-1", value(-500.0, DistanceUnit.km, "en", Measure.Style.grouped))
        assertEquals("1.1", value(1050.0, DistanceUnit.km, "en", Measure.Style.tenths))
        assertEquals("-1.1", value(-1050.0, DistanceUnit.km, "en", Measure.Style.tenths))
        assertEquals("1,000,000,000,000,000", value(Double.MAX_VALUE, DistanceUnit.km, "en", Measure.Style.grouped))
    }
}

// iOS: UnitLabelTests.swift (unit label forms; screen/chart copy belongs to the app).
class UnitLabelTests {
    private fun miles(lang: String, n: Double, digits: Int = 0) = Measure.unitDistanceShort(lang, DistanceUnit.miles, n, digits, IosMeasureWords)
    private fun feet(lang: String, n: Double, digits: Int = 0) = Measure.unitElevationShort(lang, DistanceUnit.miles, n, digits, IosMeasureWords)
    @Test fun testSlavicForms() {
        for ((n, expected) in listOf(1.0 to "миля", 2.0 to "мили", 5.0 to "миль", 11.0 to "миль", 21.0 to "миля", 111.0 to "миль", 143.0 to "мили")) assertEquals(expected, miles("ru", n))
        for ((n, expected) in listOf(1.0 to "миля", 2.0 to "милі", 5.0 to "миль", 21.0 to "миля")) assertEquals(expected, miles("uk", n))
        for (n in listOf(1.5, 8.4, 2.0, 21.0)) assertEquals("мили", miles("ru", n, 1))
        assertEquals("милі", miles("uk", 1.5, 1)); assertEquals("миля", miles("ru", 21.0))
        assertEquals("фути", feet("uk", 2.0)); assertEquals("фута", feet("uk", 1.5, 1))
        assertEquals("фута", feet("ru", 2.0)); assertEquals("фута", feet("ru", 1.5, 1))
        assertEquals("фут", feet("ru", 1.0)); assertEquals("футов", feet("ru", 5.0)); assertEquals("футів", feet("uk", 5.0))
    }
    @Test fun testSymbolsAndSingleForms() {
        for (n in listOf(0.0, 1.0, 2.0, 5.0, 21.0, 143.0, 1.5)) {
            assertEquals("миля", miles("kk", n)); assertEquals("mi", miles("id", n))
            assertEquals("фут", feet("kk", n)); assertEquals("ft", feet("id", n))
        }
        for (lang in listOf("en", "de", "es", "fr", "it", "pl", "pt", "fil", "tr")) {
            for (n in listOf(1.0, 2.0, 5.0, 21.0)) { assertEquals("mi", miles(lang, n)); assertEquals("ft", feet(lang, n)) }
            assertEquals("mi", miles(lang, 1.5, 1))
        }
        for (lang in UnitNumber.languages) for (unit in DistanceUnit.entries) {
            for (digits in listOf(0, 1)) for (n in listOf(0.0, 1.0, 2.0, 5.0, 21.0)) {
                assertTrue(Measure.unitDistanceShort(lang, unit, n, digits, IosMeasureWords).isNotBlank())
                assertTrue(Measure.unitElevationShort(lang, unit, n, digits, IosMeasureWords).isNotBlank())
            }
            assertTrue(Measure.unitSpeedShort(lang, unit, IosMeasureWords).isNotBlank())
            for (n in listOf(0.0, 2.0, 5.0, 11.0, 21.0, 1234.0, 1.5))
                assertEquals(IosMeasureWords.word(UnitWord.kilometre, lang), Measure.unitDistanceShort(lang, DistanceUnit.km, n, 0, IosMeasureWords))
        }
        for ((lang, label) in listOf("en" to "mph", "de" to "mph", "ru" to "миль/ч", "uk" to "миль/год", "kk" to "миль/сағ", "tr" to "mil/sa"))
            assertEquals(label, Measure.unitSpeedShort(lang, DistanceUnit.miles, IosMeasureWords))
    }
}

// iOS: GarageWordsTests.swift (measurement-only cases).
class GarageMeasureTests {
    @Test fun testOdometerGroupingRoundingAndMiles() {
        assertEquals("143,500", Measure.distanceValueKm(143_500.0, DistanceUnit.km, "en"))
        val ru = Measure.distanceValueKm(143_500.0, DistanceUnit.km, "ru")
        assertTrue(ru.startsWith("143") && ru.endsWith("500"))
        assertNotEquals(ru, Measure.distanceValueKm(143_500.0, DistanceUnit.km, "en"))
        for (lang in UnitNumber.languages) assertFalse(Measure.distanceValueKm(143_500.0, DistanceUnit.km, lang).contains(' '))
        assertEquals("12,346", Measure.distanceValueKm(12_345.6, DistanceUnit.km, "en"))
        assertEquals("89,167 mi", Measure.odometer(143_500.0, DistanceUnit.miles, "en", IosMeasureWords))
    }
}
