package com.onezee.triptrack.ui

import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.strings.LocalizedDateFormatter
import com.onezee.triptrack.logic.distance
import com.onezee.triptrack.model.DistanceUnit
import com.onezee.triptrack.model.Measure
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * iOS: TripAutoTitle.swift. A title the app wrote itself («5 авг, 17:41») is shown in the CURRENT
 * language, so a trip recorded under English does not stay English after the switch; a title the
 * person typed is shown as typed. Dots are dropped in every language: ru «авг.» and en «Aug.».
 */
object TripAutoTitle {
    private fun formatters(zone: ZoneId) = LocalizedDateFormatter.patterns("d MMM, HH:mm", zone)

    fun generate(date: Instant?, strings: AppStrings): String =
        if (date == null) strings.tripTitle() else string(date, strings.language, strings.zone)

    fun isAuto(title: String?, startDate: Instant, zone: ZoneId): Boolean =
        !title.isNullOrEmpty() && title in autoForms(startDate, zone)

    /** Empty stays empty and null stays null, as on iOS: only an auto title is rewritten. */
    fun localized(title: String?, startDate: Instant, strings: AppStrings): String? {
        if (title.isNullOrEmpty()) return title
        if (title !in autoForms(startDate, strings.zone)) return title
        return string(startDate, strings.language, strings.zone)
    }

    /** The card's line: the localized title, or «Поездка» when there is none. */
    fun display(title: String?, startDate: Instant, strings: AppStrings): String =
        localized(title, startDate, strings) ?: strings.tripTitle()

    private fun autoForms(startDate: Instant, zone: ZoneId): Set<String> = formatters(zone).values.flatMap { formatter ->
        val dotted = formatter.format(startDate)
        listOf(dotted, dotted.replace(".", ""))
    }.toSet()

    private fun string(date: Instant, language: AppLanguage, zone: ZoneId): String =
        formatters(zone).getValue(language).format(date).replace(".", "")
}

/**
 * iOS: RelativeTripDate.swift. «только что», «12 мин назад», «Сегодня в 18:01», «Вчера в 18:01»,
 * «3 дня назад», then the date, with the year only when it differs. Days are calendar days of
 * [now], not 24-hour blocks.
 */
object RelativeTripDate {
    fun string(date: Instant, now: Instant, strings: AppStrings): String {
        val zone = strings.zone
        val secondsAgo = maxOf(0L, ChronoUnit.SECONDS.between(date, now))
        if (secondsAgo < 60) return strings.relativeTripDateJustNow()
        val day = date.atZone(zone).toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        if (day == today) {
            if (secondsAgo < 3600) return strings.relTimeMinutesAgo((secondsAgo / 60).toInt())
            return strings.relTimeTodayAt(time(date, strings))
        }
        if (day == today.minusDays(1)) return strings.relTimeYesterdayAt(time(date, strings))
        val days = ChronoUnit.DAYS.between(day, today)
        if (days in 2..6) return strings.relTimeDaysAgo(days.toInt())
        val sameYear = day.year == today.year
        val text = LocalizedDateFormatter.patterns(if (sameYear) "d MMM" else "d MMM yyyy", zone).getValue(strings.language).format(date)
        // ru «апр.» — the meta canon is dotless.
        return if (strings.language == AppLanguage.ru) text.replace(".", "") else text
    }

    private fun time(date: Instant, strings: AppStrings) =
        LocalizedDateFormatter.patterns("HH:mm", strings.zone).getValue(strings.language).format(date)
}

/** iOS: CheckpointReading.swift. «1 ч 40 мин · 100 км»: the clock and the distance from the start. */
object CheckpointReading {
    fun text(elapsed: Double, metres: Double, unit: DistanceUnit, strings: AppStrings): String =
        "${clock(elapsed, strings)} · ${Measure.distance(maxOf(0.0, metres), unit, strings.language.code, strings)}"

    /** Hours and minutes in words; under an hour «N мин», «0 мин» included. */
    fun clock(elapsed: Double, strings: AppStrings): String {
        val total = maxOf(0L, elapsed.toLong())
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val h = strings.hoursUnitShort()
        val m = strings.minutesUnitShort()
        return when {
            hours == 0L -> "$minutes $m"
            minutes == 0L -> "$hours $h"
            else -> "$hours $h $minutes $m"
        }
    }
}

/** iOS `LocalizedDateFormatter.templates("Hmm")`: the start time with the language's own separator. */
fun AppStrings.clockTime(date: Instant): String = LocalizedDateFormatter.templates("Hmm", zone).getValue(language).format(date)
