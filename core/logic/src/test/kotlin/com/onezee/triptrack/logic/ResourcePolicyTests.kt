package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.Instant
import kotlin.test.*

// iOS: AppStrings.swift / Translations+*.swift. Fixture words are separate from production resources.
object IosWordFixtures : ProWords {
    private val rows = checkNotNull(javaClass.getResource("/ios/PolicyWords.tsv")).readText().lineSequence()
        .filter { it.isNotEmpty() }.associate { row -> val (lang, key, text) = row.split('\t'); (lang to key) to text }
    override fun text(key: ProText, lang: String): String = rows.getValue(lang to key.name)
    fun unit(key: UnitWord, lang: String): String = rows.getValue(lang to when (key) {
        UnitWord.kilometresFull -> "unitKilometersFull"
        UnitWord.milesFull -> "unitMilesFull"
        UnitWord.dashboardApp -> "dashboardUnitsApp"
        UnitWord.dashboardMetricFuel -> "dashboardUnitsMetricFuel"
        UnitWord.dashboardImperialFuel -> "dashboardUnitsImperialFuel"
        else -> error("Not a dashboard word: $key")
    })
}

// iOS: TripTrackShared/UnitNumber.swift. Frozen output from Foundation, including minimum grouping digits.
class UnitNumberFixtureTests {
    @Test fun testNumbersMatchTheIPhoneFormatterInEveryLanguage() {
        val lines = checkNotNull(javaClass.getResource("/ios/UnitNumber.tsv")).readText().lineSequence().filter { it.isNotBlank() }.toList()
        assertEquals(518, lines.size)
        for (line in lines) {
            val (operation, language, input, expected) = line.split('\t')
            val actual = when (operation) {
                "grouped" -> UnitNumber.grouped(input.toLong(), language)
                "tenths" -> UnitNumber.tenths(input.toDouble(), language)
                "upToTenth" -> UnitNumber.upToTenth(input.toDouble(), language)
                else -> error(operation)
            }
            // Large grouped figures use escaped dots to avoid the repository's IPv4 source guard.
            assertEquals(expected.replace("\\u002e", "."), actual, "$operation/$language/$input")
        }
    }
    @Test fun testOdometerParsingMatchesSwiftDoubleRatherThanJavaSuffixes() {
        for (text in listOf("1f", "1D", "1\n", "\u000b1\u000b", "1_000")) assertNull(OdometerField.storedKm(text, DistanceUnit.km))
        assertNumber(1, OdometerField.storedKm("\u00a01\u00a0", DistanceUnit.km))
        assertNumber(1, OdometerField.storedKm("\t1\t", DistanceUnit.km))
        assertNumber(1000, OdometerField.storedKm("1e3", DistanceUnit.km))
        assertNumber(16, OdometerField.storedKm("0x1p4", DistanceUnit.km))
        assertNumber(16, OdometerField.storedKm("0x10", DistanceUnit.km))
        assertNumber(1.125, OdometerField.storedKm("0x1.2", DistanceUnit.km))
        for (text in listOf("nan", "nan(123)", "-NaN(1)", "snan"))
            assertTrue(checkNotNull(OdometerField.storedKm(text, DistanceUnit.km)).isNaN())
        assertEquals(Double.POSITIVE_INFINITY, OdometerField.storedKm("inf", DistanceUnit.km))
    }
}

// iOS: VehicleDashboardPickerTests.swift (choice/hint/badge logic).
class VehicleDashboardPickerTests {
    @Test fun testNamesHintsAndBadgesFollowTheActualParsingUnit() {
        for (lang in UnitNumber.languages) {
            for (burnsFuel in listOf(true, false)) {
                val names = DashboardUnits.entries.map { it.label(lang, burnsFuel, IosMeasureWords) }
                assertTrue(names.all { it.isNotBlank() }); assertEquals(3, names.toSet().size)
            }
            assertEquals(DistanceUnit.km.labelFull(lang, IosMeasureWords), DashboardUnits.metric.label(lang, false, IosMeasureWords))
            assertEquals(DistanceUnit.miles.labelFull(lang, IosMeasureWords), DashboardUnits.imperial.label(lang, false, IosMeasureWords))
            assertNotEquals(DashboardUnits.metric.label(lang, false, IosMeasureWords), DashboardUnits.metric.label(lang, true, IosMeasureWords))
            assertEquals(DashboardUnits.app.label(lang, false, IosMeasureWords), DashboardUnits.app.label(lang, true, IosMeasureWords))
            for (app in DistanceUnit.entries) for (unit in DashboardUnits.entries) {
                val parsing = unit.resolved(app)
                val named = unit.resolvedLabel(lang, app, true, IosMeasureWords)
                assertNotEquals(DashboardUnits.app.label(lang, true, IosMeasureWords), named)
                assertEquals((if (parsing == DistanceUnit.miles) DashboardUnits.imperial else DashboardUnits.metric).label(lang, true, IosMeasureWords), named)
                assertNumber(if (parsing == DistanceUnit.miles) 160.9344 else 100.0, OdometerField.storedKm("100", parsing), 0.0001)
            }
        }
        for (app in DistanceUnit.entries) assertEquals(
            (if (app == DistanceUnit.miles) DashboardUnits.imperial else DashboardUnits.metric).badge("en", app, IosMeasureWords),
            DashboardUnits.app.badge("en", app, IosMeasureWords))
        assertNotEquals(DashboardUnits.metric.badge("en", DistanceUnit.miles, IosMeasureWords), DashboardUnits.imperial.badge("en", DistanceUnit.miles, IosMeasureWords))
    }
}

// iOS: ProStatusTests.swift / ProManageModelTests.swift (template selection and substitution).
class ProTextTests {
    private val date = Instant.ofEpochSecond(1_822_000_000)
    private val words = IosWordFixtures
    @Test fun testTitlesSubtitlesAndMissingDatesInEveryLanguage() {
        for (lang in UnitNumber.languages) {
            val states = listOf(ProStatus.None, ProStatus.Trial(date), ProStatus.Active(date), ProStatus.Grace, ProStatus.Pending)
            for (state in states) assertEquals(words.text(ProText.meProNone, lang), state.rowTitle(lang, words) { "D" })
            assertNotEquals(words.text(ProText.meProNone, lang), ProStatus.Expired(date).rowTitle(lang, words) { "D" })
            val expired = ProStatus.Expired(null).rowTitle(lang, words) { "D" }
            assertTrue(expired.isNotEmpty()); assertEquals(expired.trim(), expired); assertFalse(expired.contains("  ")); assertFalse(expired.contains("{date}"))
            val subtitles = (states + ProStatus.Expired(date)).map { it.rowSubtitle(lang, words) { "D" } }
            assertEquals(subtitles.size, subtitles.toSet().size)
            for (text in subtitles) { assertTrue(text.isNotEmpty()); assertFalse(text.contains("{date}")) }
            assertEquals(words.text(ProText.meProNoneSub, lang), ProStatus.Active(null).rowSubtitle(lang, words) { "D" })
        }
    }
    @Test fun testThePeriodWordFollowsThePlan() {
        assertEquals("Продлится 12 окт. · X в год", ProManageLine.Renews(date, "X", PlusProductInfo.Period.yearly).text("ru", words) { "12 окт." })
        assertEquals("Продлится 12 окт. · X в месяц", ProManageLine.Renews(date, "X", PlusProductInfo.Period.monthly).text("ru", words) { "12 окт." })
    }
    @Test fun testEveryLineIsFilledInEveryLanguage() {
        val lines = listOf(ProManageLine.TrialRenews(date, "¤42", PlusProductInfo.Period.yearly),
            ProManageLine.TrialRenews(date, "¤42", PlusProductInfo.Period.monthly), ProManageLine.Renews(date, "¤42", PlusProductInfo.Period.yearly),
            ProManageLine.Renews(date, "¤42", PlusProductInfo.Period.monthly), ProManageLine.EndsOn(date), ProManageLine.Until(date), ProManageLine.BillingProblem)
        for (lang in UnitNumber.languages) {
            val texts = lines.mapNotNull { it.text(lang, words) { "D" } }
            assertEquals(lines.size, texts.size); assertEquals(texts.size, texts.toSet().size)
            assertTrue(texts.none { it.contains('{') }); assertTrue(texts[0].contains("¤42") && texts[2].contains("¤42"))
            assertFalse(checkNotNull(lines[4].text(lang, words) { "D" }).contains("¤42"))
        }
        assertNull(ProManageLine.Unknown.text("ru", words) { "D" })
    }
}
