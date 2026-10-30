package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

// iOS: AppStrings.swift, unit labels. The app supplies these words from generated resources.
interface MeasureWords {
    fun word(key: UnitWord, lang: String): String
}

// iOS: AppStrings.swift, unitDistanceShort / unitElevationShort / GarageFormat.swift.
enum class UnitWord {
    kilometre, kilometresPerHour, metre, mileOne, mileFew, mileMany,
    footOne, footFew, footMany, footFraction, milesPerHour, litre, kWh,
    kilometresFull, milesFull, dashboardApp, dashboardMetricFuel, dashboardImperialFuel,
}

// iOS: TripTrackShared/UnitNumber.swift. Decimal punctuation is a product decision.
object UnitNumber {
    val languages = listOf("en", "ru", "de", "es", "fr", "it", "pl", "id", "tr", "fil", "uk", "kk", "pt")

    fun decimalSeparator(code: String): String = "."

    // Foundation omits grouping for four-digit Spanish, Italian and Polish numbers.
    // A fixed table keeps Android and JDK locale data from changing the displayed grouping.
    fun grouped(value: Long, code: String): String {
        val separator = when (code) {
            "ru", "pl", "uk", "kk" -> "\u00a0"
            "fr" -> "\u202f"
            "de", "es", "it", "id", "tr", "pt" -> "."
            else -> ","
        }
        val digits = value.toString()
        if (code in setOf("es", "it", "pl") && digits.removePrefix("-").length <= 4) return digits
        val sign = if (digits.startsWith('-')) "-" else ""
        return sign + digits.removePrefix("-").reversed().chunked(3).joinToString(separator).reversed()
    }

    fun tenths(value: Double, code: String): String =
        String.format(Locale.ROOT, "%.1f", swiftRounded(value * 10) / 10)
            .replace(".", decimalSeparator(code))

    fun upToTenth(value: Double, code: String): String {
        val rounded = swiftRounded(value * 10) / 10
        return if (rounded == swiftRounded(rounded)) String.format(Locale.ROOT, "%.0f", rounded)
        else tenths(rounded, code)
    }
}

// Swift rounds ties away from zero; kotlin.math.round uses ties to even.
internal fun swiftRounded(value: Double): Double {
    if (!value.isFinite()) return value
    val magnitude = abs(value)
    val integral = floor(magnitude)
    return Math.copySign(if (magnitude - integral >= 0.5) integral + 1 else integral, value)
}

// iOS: Measure.swift
private data class Shown(val text: String, val value: Double, val fractionDigits: Int)

private fun shownNumber(raw: Double, style: Measure.Style, unit: DistanceUnit, lang: String): Shown {
    val value = if (raw.isFinite()) raw else 0.0
    fun grouped(): Shown {
        val rounded = swiftRounded(value).coerceIn(-1e15, 1e15)
        return Shown(UnitNumber.grouped(rounded.toLong(), lang), rounded, 0)
    }
    fun tenths() = Shown(UnitNumber.tenths(value, lang), swiftRounded(value * 10) / 10, 1)
    return when (style) {
        Measure.Style.tenths -> tenths()
        Measure.Style.grouped -> grouped()
        Measure.Style.adaptive -> {
            if (!unit.showsTenths(value)) grouped()
            else tenths().let { if (it.value == swiftRounded(it.value)) grouped() else it }
        }
    }
}

// iOS: AppStrings.swift, pluralForm(shown:fractionDigits:). The visible digits choose the word.
private fun unitForm(value: Double, fractionDigits: Int, one: UnitWord, few: UnitWord,
                     many: UnitWord, fraction: UnitWord): UnitWord {
    if (fractionDigits > 0) return fraction
    val n = abs(value).toLong()
    return when {
        n % 10 == 1L && n % 100 != 11L -> one
        n % 10 in 2L..4L && n % 100 !in 12L..14L -> few
        else -> many
    }
}

fun Measure.unitDistanceShort(lang: String, unit: DistanceUnit, value: Double, fractionDigits: Int,
                              words: MeasureWords): String = when {
    unit == DistanceUnit.km -> words.word(UnitWord.kilometre, lang)
    lang == "ru" || lang == "uk" -> words.word(unitForm(value, fractionDigits, UnitWord.mileOne,
        UnitWord.mileFew, UnitWord.mileMany, UnitWord.mileFew), lang)
    lang == "kk" -> words.word(UnitWord.mileOne, lang)
    else -> "mi"
}

fun Measure.unitSpeedShort(lang: String, unit: DistanceUnit, words: MeasureWords): String = when {
    unit == DistanceUnit.km -> words.word(UnitWord.kilometresPerHour, lang)
    lang in setOf("ru", "uk", "kk", "tr") -> words.word(UnitWord.milesPerHour, lang)
    else -> "mph"
}

fun Measure.unitElevationShort(lang: String, unit: DistanceUnit, value: Double, fractionDigits: Int,
                               words: MeasureWords): String = when {
    unit == DistanceUnit.km -> words.word(UnitWord.metre, lang)
    lang == "ru" || lang == "uk" -> words.word(unitForm(value, fractionDigits, UnitWord.footOne,
        UnitWord.footFew, UnitWord.footMany, UnitWord.footFraction), lang)
    lang == "kk" -> words.word(UnitWord.footOne, lang)
    else -> "ft"
}

fun Measure.distanceParts(metres: Double, unit: DistanceUnit, lang: String, words: MeasureWords,
                          style: Measure.Style = Measure.Style.adaptive): Measure.Parts {
    val shown = shownNumber(unit.distance(metres), style, unit, lang)
    return Measure.Parts(shown.text, unitDistanceShort(lang, unit, shown.value, shown.fractionDigits, words))
}
fun Measure.distance(metres: Double, unit: DistanceUnit, lang: String, words: MeasureWords,
                     style: Measure.Style = Measure.Style.adaptive): String =
    distanceParts(metres, unit, lang, words, style).joined()
fun Measure.distanceValue(metres: Double, unit: DistanceUnit, lang: String,
                          style: Measure.Style = Measure.Style.adaptive): String =
    shownNumber(unit.distance(metres), style, unit, lang).text

// Kotlin cannot overload functions whose only difference is an argument label (km / metres).
fun Measure.distanceKm(km: Double, unit: DistanceUnit, lang: String, words: MeasureWords,
                       style: Measure.Style = Measure.Style.grouped): String = distance(km * 1000, unit, lang, words, style)
fun Measure.distanceValueKm(km: Double, unit: DistanceUnit, lang: String,
                            style: Measure.Style = Measure.Style.grouped): String = distanceValue(km * 1000, unit, lang, style)
fun Measure.distancePartsKm(km: Double, unit: DistanceUnit, lang: String, words: MeasureWords,
                            style: Measure.Style = Measure.Style.grouped): Measure.Parts = distanceParts(km * 1000, unit, lang, words, style)
fun Measure.odometer(km: Double, unit: DistanceUnit, lang: String, words: MeasureWords): String = distanceKm(km, unit, lang, words)

fun Measure.speedParts(ms: Double, unit: DistanceUnit, lang: String, words: MeasureWords): Measure.Parts =
    Measure.Parts(speedValue(ms, unit, lang), unitSpeedShort(lang, unit, words))
fun Measure.speedValue(ms: Double, unit: DistanceUnit, lang: String): String =
    shownNumber(unit.speed(ms), Measure.Style.grouped, unit, lang).text
fun Measure.speed(ms: Double, unit: DistanceUnit, lang: String, words: MeasureWords): String = speedParts(ms, unit, lang, words).joined()

fun Measure.elevationParts(metres: Double, unit: DistanceUnit, lang: String, words: MeasureWords): Measure.Parts {
    val shown = shownNumber(unit.elevation(metres), Measure.Style.grouped, unit, lang)
    return Measure.Parts(shown.text, unitElevationShort(lang, unit, shown.value, shown.fractionDigits, words))
}
fun Measure.elevationValue(metres: Double, unit: DistanceUnit, lang: String): String =
    shownNumber(unit.elevation(metres), Measure.Style.grouped, unit, lang).text
fun Measure.elevation(metres: Double, unit: DistanceUnit, lang: String, words: MeasureWords): String = elevationParts(metres, unit, lang, words).joined()

// Radius has its own entry point so changes to altitude display cannot change privacy radii.
fun Measure.radius(metres: Double, unit: DistanceUnit, lang: String, words: MeasureWords): String =
    elevationParts(metres, unit, lang, words).joined()

private fun Measure.Parts.joined(): String = "$value $unit"
