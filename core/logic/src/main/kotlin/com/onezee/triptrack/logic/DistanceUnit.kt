package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.util.Locale

// iOS: TripTrackShared/DistanceUnit.swift. Display conversions never enter stored rewards.
fun DistanceUnit.distance(fromMetres: Double): Double = when (this) {
    DistanceUnit.km -> fromMetres / 1000
    DistanceUnit.miles -> fromMetres / DistanceUnit.metresPerMile
}

fun DistanceUnit.metres(fromDistance: Double): Double = when (this) {
    DistanceUnit.km -> fromDistance * 1000
    DistanceUnit.miles -> fromDistance * DistanceUnit.metresPerMile
}

fun DistanceUnit.speed(fromMetresPerSecond: Double): Double = when (this) {
    DistanceUnit.km -> fromMetresPerSecond * 3.6
    DistanceUnit.miles -> fromMetresPerSecond * 3600 / DistanceUnit.metresPerMile
}

fun DistanceUnit.metresPerSecond(fromSpeed: Double): Double = when (this) {
    DistanceUnit.km -> fromSpeed / 3.6
    DistanceUnit.miles -> fromSpeed * DistanceUnit.metresPerMile / 3600
}

fun DistanceUnit.elevation(fromMetres: Double): Double = when (this) {
    DistanceUnit.km -> fromMetres
    DistanceUnit.miles -> fromMetres / DistanceUnit.metresPerFoot
}

val DistanceUnit.tenthsBelow: Double get() = if (this == DistanceUnit.miles) 6.0 else 10.0
fun DistanceUnit.showsTenths(shown: Double): Boolean = shown < tenthsBelow

// iOS: DistanceUnit+Current.swift. The device region, independently of the app language.
fun DistanceUnit.Companion.guessFromRegion(locale: Locale): DistanceUnit =
    if (locale.country in setOf("US", "GB", "LR", "MM")) DistanceUnit.miles else DistanceUnit.km

// iOS: DashboardUnits.swift
fun DashboardUnits.resolved(app: DistanceUnit): DistanceUnit = when (this) {
    DashboardUnits.app -> app
    DashboardUnits.metric -> DistanceUnit.km
    DashboardUnits.imperial -> DistanceUnit.miles
}

// iOS: Vehicle.swift
fun Vehicle.dashboardUnit(app: DistanceUnit): DistanceUnit = dashboardUnits.resolved(app)
fun Vehicle.consumptionUnit(app: DistanceUnit): ConsumptionUnit = ConsumptionUnit.forDashboard(dashboardUnit(app))
fun Vehicle.electricUnit(app: DistanceUnit): ElectricUnit = ElectricUnit.forDashboard(dashboardUnit(app))

// iOS: OdometerField.swift
object OdometerField {
    private val nan = Regex("[+-]?s?nan(?:\\([^)]*\\))?")
    private val number = Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?|[+-]?0[xX](?:[0-9a-fA-F]+(?:\\.[0-9a-fA-F]*)?|\\.[0-9a-fA-F]+)(?:[pP][+-]?[0-9]+)?")
    fun storedKm(text: String, unit: DistanceUnit): Double? {
        val trimmed = text.trim {
            it == '\t' || it == '\u200b' || Character.getType(it) == Character.SPACE_SEPARATOR.toInt()
        }
        if (trimmed.isEmpty()) return null
        // Java accepts whitespace and f/d suffixes that Swift Double rejects.
        val normalized = trimmed.lowercase(Locale.ROOT)
        val shown = when {
            normalized in setOf("inf", "+inf", "infinity", "+infinity") -> Double.POSITIVE_INFINITY
            normalized in setOf("-inf", "-infinity") -> Double.NEGATIVE_INFINITY
            nan.matches(normalized) -> Double.NaN
            !number.matches(trimmed) -> return null
            normalized.contains("0x") && !normalized.contains('p') -> (trimmed + "p0").toDoubleOrNull() ?: return null
            else -> trimmed.toDoubleOrNull() ?: return null
        }
        return unit.metres(shown) / 1000
    }

    fun fieldText(km: Double?, unit: DistanceUnit): String =
        km?.let { swiftRounded(unit.distance(it * 1000)).toLong().toString() } ?: ""
}

// iOS: DashboardUnits.swift / DistanceUnit+Current.swift. Words are supplied by the resource adapter.
fun DistanceUnit.labelFull(lang: String, words: MeasureWords): String =
    words.word(if (this == DistanceUnit.miles) UnitWord.milesFull else UnitWord.kilometresFull, lang)
fun DashboardUnits.label(lang: String, burnsFuel: Boolean, words: MeasureWords): String = when (this) {
    DashboardUnits.app -> words.word(UnitWord.dashboardApp, lang)
    DashboardUnits.metric -> if (burnsFuel) words.word(UnitWord.dashboardMetricFuel, lang) else DistanceUnit.km.labelFull(lang, words)
    DashboardUnits.imperial -> if (burnsFuel) words.word(UnitWord.dashboardImperialFuel, lang) else DistanceUnit.miles.labelFull(lang, words)
}
fun DashboardUnits.resolvedLabel(lang: String, app: DistanceUnit, burnsFuel: Boolean, words: MeasureWords): String =
    (if (resolved(app) == DistanceUnit.miles) DashboardUnits.imperial else DashboardUnits.metric).label(lang, burnsFuel, words)
fun DashboardUnits.badge(lang: String, app: DistanceUnit, words: MeasureWords): String =
    if (resolved(app) == DistanceUnit.miles) "mi" else words.word(UnitWord.kilometre, lang)
