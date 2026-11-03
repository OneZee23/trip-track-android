// iOS: StatsPeriodFormat.monthYearPrepositional, TripRowText.swift
package com.onezee.triptrack.feature.garage

import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.strings.LocalizedDateFormatter
import com.onezee.triptrack.feature.atlas.ui.humanTime
import com.onezee.triptrack.feature.me.ProfileRegionDisplay
import com.onezee.triptrack.feature.stats.StatsPeriodFormat
import com.onezee.triptrack.feature.trip.TripSummary
import com.onezee.triptrack.logic.distance
import com.onezee.triptrack.logic.duration
import com.onezee.triptrack.logic.elevation
import com.onezee.triptrack.model.DistanceUnit
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.model.VehicleType
import com.onezee.triptrack.ui.cards.hasDisplayableName
import java.time.Instant
import java.time.ZoneId

/**
 * iOS StatsPeriodFormat.monthYearPrepositional: «в марте 2026» is read after «Продана в», so the month stands in the
 * prepositional case. A table, not a rule: ICU does not give the case, and «drop the ending and add -е» breaks on
 * «мае» and «августе». Two languages decline their months after a preposition; in the others it is the genitive form.
 */
internal fun monthYearPrepositional(date: Instant, strings: AppStrings, zone: ZoneId = strings.zone): String {
    val month = date.atZone(zone).monthValue - 1
    val table = when (strings.language) {
        AppLanguage.ru -> listOf("январе", "феврале", "марте", "апреле", "мае", "июне", "июле", "августе", "сентябре", "октябре", "ноябре", "декабре")
        AppLanguage.uk -> listOf("січні", "лютому", "березні", "квітні", "травні", "червні", "липні", "серпні", "вересні", "жовтні", "листопаді", "грудні")
        else -> null
    } ?: return StatsPeriodFormat.monthYearGenitive(date, strings.language, zone)
    return "${table[month]} ${date.atZone(zone).year}"
}

/**
 * iOS TripRowText: what a row of a trip list inside the garage says. Three questions, WHAT, WHEN and HOW MUCH: the
 * ladder of the title (the name, else the region, else the generic word), the time of day in the line under it (two
 * trips a day are told apart by the clock) and the kilometres on the right.
 */
internal object GarageTripText {
    /** The ladder of the trip screen's bar: the trip's own name, else its region, else «Поездка». */
    fun title(trip: Trip, strings: AppStrings): String {
        trip.title?.takeIf { it.isNotEmpty() && TripSummary.from(trip).hasDisplayableName(strings) }?.let { return it }
        ProfileRegionDisplay.localized(trip.region, strings.language.code)?.takeIf { it.isNotEmpty() }?.let { return it }
        return strings.tripTitle()
    }

    /** «5 сент., 17:41 · 23 мин»; the year only on another year's trip, as iOS prints it. */
    fun whenText(trip: Trip, strings: AppStrings, now: Instant = Instant.now()): String {
        val thisYear = trip.startDate.atZone(strings.zone).year == now.atZone(strings.zone).year
        val stamp = LocalizedDateFormatter.templates(if (thisYear) "dMMMHm" else "dMMMyyyyHm", strings.zone).getValue(strings.language).format(trip.startDate)
        val duration = trip.duration.takeIf { it > 0 }?.let { humanTime(it, strings) }.orEmpty()
        return if (duration.isEmpty()) stamp else "$stamp · $duration"
    }
}

/** iOS VehicleType.supportsAutoRecord: auto-record keys off a Bluetooth car stereo, which only cars and motorcycles have. */
internal val VehicleType.supportsAutoRecord: Boolean get() = this == VehicleType.car || this == VehicleType.moto

/** iOS VehicleType.burnsFuel: a bicycle burns nothing, so consumption and the price of fuel mean nothing to it. */
internal val VehicleType.burnsFuel: Boolean get() = this != VehicleType.bicycle

/**
 * iOS vehicleRecords: the rows of «Рекорды» for one vehicle, from its own trips. The longest trip (the one row that opens
 * its trip), the highest, the best day of the year (named, as iOS names it, by the date of the longest trip) and the day
 * with the most time on the road. A row appears only when it has something to say.
 */
internal fun vehicleRecords(passport: VehiclePassport, unit: DistanceUnit, strings: AppStrings): List<VehicleRecord> {
    if (passport.tripCount == 0) return emptyList()
    val code = strings.language.code
    val longest = passport.longestTrip
    fun name(trip: Trip?) = trip?.title?.takeIf { it.isNotBlank() } ?: trip?.region ?: "—"
    return buildList {
        add(VehicleRecord(strings.recordLongest(), name(longest), Measure.distance(longest?.distance ?: 0.0, unit, code, strings), longest, "vehicle_record_longest"))
        passport.highestTrip?.takeIf { it.elevation > 0 }?.let { add(VehicleRecord(strings.recordHighest(), name(it), Measure.elevation(it.elevation, unit, code, strings), tag = "vehicle_record_highest")) }
        if (passport.maxDayDistanceMetresThisYear > 0) add(VehicleRecord(strings.statsRecordBestDay(unit),
            longest?.startDate?.let { LocalizedDateFormatter.templates("dMMMMyyyy", strings.zone).getValue(strings.language).format(it) } ?: "—",
            Measure.distance(passport.maxDayDistanceMetresThisYear, unit, code, strings), tag = "vehicle_record_best_day"))
        if (passport.maxDayDurationSeconds > 0) add(VehicleRecord(strings.recordLongestDay(), strings.recordLongestDaySubtitle(), humanTime(passport.maxDayDurationSeconds, strings), tag = "vehicle_record_longest_day"))
    }
}
