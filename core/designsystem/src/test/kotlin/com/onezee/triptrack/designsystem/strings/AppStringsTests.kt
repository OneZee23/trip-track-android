package com.onezee.triptrack.designsystem.strings

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import com.onezee.triptrack.designsystem.R
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import org.json.JSONArray
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.test.*

// iOS: LocalizationTests.swift, UnitLabelTests.swift, MeasureFormatTests.swift.
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35])
class AppStringsTests(private val language: AppLanguage) {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val strings get() = AppStrings(context, language, ZoneId.of("UTC"))

    // iOS: AppStrings.swift, BluetoothScanSheet.swift; platform override in every language.
    @Test fun linkedStereoCaptionNamesAndroidAuto() {
        assertEquals("Android Auto", strings.androidAuto())
        assertTrue(strings.autoRecordTriggerStereoCaption().contains(strings.androidAuto()))
        assertFalse(strings.autoRecordTriggerStereoCaption().contains("CarPlay"))
    }

    @Test fun originalSwiftFunctionOutputs() {
        val facade = strings
        val failures = mutableListOf<String>()
        val rows = JSONArray(javaClass.getResource("/ios/AppStrings.json")!!.readText())
        var checked = 0
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            if (row.getString("language") != language.code) continue
            val name = row.getString("function")
            val arguments = row.getJSONArray("arguments")
            val method = AppStrings::class.java.methods.single { it.name == name && it.parameterCount == arguments.length() }
            val args = method.parameterTypes.mapIndexed { index, type ->
                if (arguments.isNull(index)) null else when {
                    type == String::class.java -> arguments.getString(index)
                    type == Int::class.javaPrimitiveType || type == Int::class.javaObjectType -> arguments.getInt(index)
                    type == Double::class.javaPrimitiveType -> arguments.getDouble(index)
                    type == Boolean::class.javaPrimitiveType -> arguments.getBoolean(index)
                    type == Instant::class.java -> Instant.parse(arguments.getString(index))
                    type.isEnum -> requireNotNull(type.enumConstants).single { (it as Enum<*>).name == arguments.getString(index) }
                    else -> error("Unsupported fixture parameter $type")
                }
            }.toTypedArray()
            val expected = if (row.isNull("expected")) null else androidWording(name, row.getString("expected"))
            val actual = method.invoke(facade, *args)
            if (expected != actual) failures += "$name$arguments expected=<$expected> actual=<$actual>"
            checked++
        }
        assertTrue(checked > 400, "Too few Swift fixtures for ${language.code}: $checked")
        assertTrue(failures.isEmpty(), "${language.code}: ${failures.size} failures\n${failures.take(30).joinToString("\n")}")
    }

    /** The same Swift inputs and sentences; only the device name differs on Android. */
    private fun androidWording(function: String, text: String): String {
        if (function != "cosmeticRetainedChoice") return text
        val (before, after) = when (language) {
            AppLanguage.en -> "iPhone" to "phone"
            AppLanguage.ru -> "iPhone" to "телефоне"
            AppLanguage.de -> "iPhone" to "Telefon"
            AppLanguage.es -> "iPhone" to "teléfono"
            AppLanguage.fr -> "cet iPhone" to "ce téléphone"
            AppLanguage.it -> "iPhone" to "telefono"
            AppLanguage.pl -> "iPhonie" to "telefonie"
            AppLanguage.id -> "iPhone" to "ponsel"
            AppLanguage.tr -> "iPhone’da" to "telefonda"
            AppLanguage.fil -> "iPhone na ito" to "teleponong ito"
            AppLanguage.uk -> "iPhone" to "телефоні"
            AppLanguage.kk -> "iPhone құрылғысында" to "телефонда"
            AppLanguage.pt -> "iPhone" to "celular"
        }
        return text.replace(before, after)
    }

    @Test fun chosenLanguageOverridesConfigurationAndCanChange() {
        val configured = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(AppLanguage.tr.locale))
        })
        assertEquals(AppLanguage.tr, AppStrings(configured).language)
        assertEquals(strings.cancel(), AppStrings(configured, language).cancel())
        assertEquals("Abbrechen", strings.withLanguage(AppLanguage.de).cancel())
        assertEquals("Cancelar", strings.withLanguage(AppLanguage.es).cancel())
        assertEquals("Abbrechen", strings.withLanguage(AppLanguage.de).tr("cancel", "Отмена", "Cancel"))
        assertEquals("Gewöhnlich", strings.withLanguage(AppLanguage.de).tr("badgeRarity.common", "Обычная", "Common"))
        assertEquals(if (language == AppLanguage.ru) "русский" else "english", strings.tr("no-such-key-exists", "русский", "english"))
    }

    @Test fun placeholdersDoNotReinterpretInsertedPercentOrBraces() {
        val value = "50% {name} %1\$s & <tag>"
        assertTrue(strings.placeTowards(value).contains(value))
        assertTrue(strings.journeyPublishIntro(value).contains(value))
        assertTrue(strings.adminProSplit(21, 5).contains("21"))
        assertTrue(strings.placeLastPass(value).contains(value))
        assertTrue(strings.exploredPercent("50", value).contains(value))
        assertTrue(strings.garageGearbox("dct", 7).contains("7"))
    }

    @Test fun everyNounUsesTheSameIosCountRules() {
        val samples = listOf(0, 1, 2, 5, 11, 21, 22, 111, -1, -21)
        val expected = when (language) {
            AppLanguage.ru -> listOf("поездок", "поездка", "поездки", "поездок", "поездок", "поездка", "поездки", "поездок", "поездка", "поездка")
            AppLanguage.uk -> listOf("поїздок", "поїздка", "поїздки", "поїздок", "поїздок", "поїздка", "поїздки", "поїздок", "поїздка", "поїздка")
            AppLanguage.pl -> listOf("tras", "trasa", "trasy", "tras", "tras", "tras", "trasy", "tras", "trasa", "tras")
            AppLanguage.fil -> List(samples.size) { "biyahe" }
            AppLanguage.id -> List(samples.size) { "perjalanan" }
            else -> null
        }
        if (expected != null) samples.zip(expected).forEach { (n, word) -> assertEquals(word, strings.nounTrips(n), "$language/$n") }
        val nouns = ResourceAppStrings::class.java.methods.filter { it.name.startsWith("noun") }
        assertEquals(20, nouns.size)
        for (method in nouns) for (n in samples) assertTrue((method.invoke(strings, n) as String).isNotBlank())
        if (language in listOf(AppLanguage.fr, AppLanguage.fil, AppLanguage.pt)) assertEquals(AppStrings.PluralForm.one, strings.pluralForm(0))
        assertEquals(AppStrings.PluralForm.fraction, strings.pluralForm(2.0, 1))
        assertEquals("few", strings.plural(AppStrings.PluralForm.fraction, "one", "few", "many"))
        assertEquals("fraction", strings.plural(AppStrings.PluralForm.fraction, "one", "few", "many", "fraction"))
    }

    @Test fun measureUsesResourceWordsInEveryLanguageUnitAndStyle() {
        val facade = strings
        for (unit in DistanceUnit.entries) for (style in Measure.Style.entries) {
            for (metres in listOf(0.0, 400.0, 5000.0, 9900.0, 128_400.0)) {
                val parts = Measure.distanceParts(metres, unit, language.code, facade, style)
                assertEquals("${parts.value} ${parts.unit}", Measure.distance(metres, unit, language.code, facade, style))
                assertTrue(parts.unit.isNotEmpty())
            }
            for (value in listOf(0.0, 1.0, 2.0, 5.0, 11.0, 21.0, 143.0)) for (digits in listOf(0, 1)) {
                assertEquals(facade.unitDistanceShort(unit, value, digits), Measure.unitDistanceShort(language.code, unit, value, digits, facade))
                assertEquals(facade.unitElevationShort(unit, value, digits), Measure.unitElevationShort(language.code, unit, value, digits, facade))
                assertEquals(facade.unitDistanceShort(unit, value, digits), facade.live_distanceShort(unit, value, digits))
            }
            assertEquals(facade.unitSpeedShort(unit), Measure.unitSpeedShort(language.code, unit, facade))
        }
        for (key in UnitWord.entries) assertTrue(facade.word(key, language.code).isNotBlank(), "$key")
        assertEquals("5.2 мили", Measure.distance(8400.0, DistanceUnit.miles, "ru", facade, Measure.Style.tenths))
        assertEquals("2.0 милі", Measure.distance(2 * DistanceUnit.metresPerMile, DistanceUnit.miles, "uk", facade, Measure.Style.tenths))
        assertEquals("фута", facade.withLanguage(AppLanguage.uk).unitElevationShort(DistanceUnit.miles, 1.5, 1))
    }

    @Test fun caseAndDateFormattingUseLanguageLocale() {
        assertEquals(if (language == AppLanguage.tr) "İ" else "I", "i".uppercased(language))
        assertEquals(if (language == AppLanguage.tr) "ı" else "i", "I".lowercased(language))
        assertEquals(language.locale, Locale.forLanguageTag(language.locale.toLanguageTag()))
        val date = Instant.parse("2026-05-12T12:00:00Z")
        val pattern = LocalizedDateFormatter.patterns("yyyy-MM-dd HH:mm", ZoneId.of("UTC")).getValue(language)
        assertEquals("2026-05-12 12:00", pattern.format(date))
        assertEquals(language, pattern.language)
        assertTrue(strings.longDate(date).contains("2026"))
        assertTrue(strings.mapRegionSince(date).contains("2026"))
        if (language == AppLanguage.ru) assertTrue(strings.mapRegionSince(date).contains("мая"))
        if (language == AppLanguage.uk) assertTrue(strings.mapRegionSince(date).contains("травня"))
        val localDate = LocalizedDateFormatter.templates("dMMMM", ZoneId.of("UTC")).getValue(language).format(date)
        if (language == AppLanguage.en) assertEquals("May 12", localDate)
        if (language == AppLanguage.de) assertEquals("12. Mai", localDate)
        val boundary = Instant.parse("2026-01-01T00:30:00Z")
        val utc = LocalizedDateFormatter.patterns("yyyy-MM-dd HH:mm", ZoneId.of("UTC")).getValue(language)
        val west = LocalizedDateFormatter.patterns("yyyy-MM-dd HH:mm", ZoneId.of("America/Los_Angeles")).getValue(language)
        assertEquals("2026-01-01 00:30", utc.format(boundary))
        assertEquals("2025-12-31 16:30", west.format(boundary))
    }

    @Test fun ukrainianOrdinalUsesTheLastWordOfTheNumber() {
        val uk = strings.withLanguage(AppLanguage.uk)
        for ((n, suffix) in listOf(1 to "ша", 2 to "га", 3 to "тя", 4 to "та", 7 to "ма", 8 to "ма",
            11 to "та", 12 to "та", 21 to "ша", 22 to "га", 40 to "ва", 50 to "та", 100 to "та",
            211 to "та", 221 to "ша", 1000 to "на")) {
            assertEquals("$n-$suffix миля", uk.chartMileMark(n.toDouble()))
        }
        assertEquals("132-я миля", strings.withLanguage(AppLanguage.ru).chartDistanceMark(DistanceUnit.miles, 212.0))
        assertEquals("132-га миля", uk.chartDistanceMark(DistanceUnit.miles, 212.0))
        assertEquals("mi 132", strings.withLanguage(AppLanguage.en).chartDistanceMark(DistanceUnit.miles, 212.0))
        assertEquals("миля", strings.withLanguage(AppLanguage.ru).unitDistanceShort(DistanceUnit.miles, 3_000_000_001.0, 0))
        assertEquals("3000000001-й км", uk.chartKmMark(3_000_000_001.0))
        assertEquals("km 4503599627370497", strings.withLanguage(AppLanguage.en).chartKmMark(4_503_599_627_370_497.0))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun languages(): List<Array<Any>> = AppLanguage.entries.map { arrayOf(it) }
    }
}
