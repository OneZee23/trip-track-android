// iOS: ProfileHistoryCalendar.swift
package com.onezee.triptrack.feature.me

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.surfaceCard
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.settings.MeRow
import com.onezee.triptrack.ui.LocalClock
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters

// iOS: ProfileHistoryCalendar.handleDayTap. A full range restarts on the next tap.
internal data class CalendarRange(val from: LocalDate? = null, val to: LocalDate? = null) {
    fun tap(day: LocalDate): CalendarRange = when {
        from == null || to != null -> CalendarRange(day)
        day == from -> CalendarRange()
        day < from -> CalendarRange(day, from)
        else -> CalendarRange(from, day)
    }
    fun contains(day: LocalDate): Boolean = from != null && day >= from && day <= (to ?: from)
}

internal fun calendarHeatStep(km: Double, scale: Double): Int? = when {
    km <= 0 -> null
    scale <= 0 -> 3
    km / scale <= .25 -> 0
    km / scale <= .5 -> 1
    km / scale <= .75 -> 2
    else -> 3
}

@Composable
internal fun ProfileHistoryCalendar(calendar: HistoryCalendar, from: Instant?, to: Instant?, filteredCount: Int,
                                     onRange: (Instant?, Instant?) -> Unit, today: LocalDate = LocalClock.current.instant().atZone(appStrings.zone).toLocalDate()) {
    val strings = appStrings
    val zone = strings.zone
    var expanded by rememberSaveable { mutableStateOf(false) }
    var monthEpoch by rememberSaveable { mutableLongStateOf(today.withDayOfMonth(1).toEpochDay()) }
    val month = LocalDate.ofEpochDay(monthEpoch)
    val range = CalendarRange(from?.atZone(zone)?.toLocalDate(), to?.atZone(zone)?.toLocalDate())
    val days = if (expanded) {
        val padding = month.dayOfWeek.value - 1
        val count = ((padding + month.lengthOfMonth() + 6) / 7) * 7
        (0 until count).map { index -> (index - padding).takeIf { it in 0 until month.lengthOfMonth() }?.let { month.plusDays(it.toLong()) } }
    } else {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        (0L..6).map(monday::plusDays)
    }
    fun distance(day: LocalDate?): Double = day?.let { calendar.kmByDay[it.atStartOfDay(zone).toInstant()] } ?: 0.0
    val peak = days.maxOfOrNull(::distance) ?: 0.0
    val scale = if (calendar.maxKmDay > 0) minOf(peak, calendar.maxKmDay) else peak
    val ramp = if (AppTheme.isDark) listOf(0xFF423C38, 0xFF694D3A, 0xFF9D512B, 0xFFC44021) else listOf(0xFFFCDBB8, 0xFFFAA86B, 0xFFF27333, 0xFFDB4521)
    val empty = Color(if (AppTheme.isDark) 0xFF2C2A28 else 0xFFEBE8E5)
    Column(Modifier.fillMaxWidth().surfaceCard(16.dp).padding(14.dp).testTag("profile_calendar"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (expanded) Text("‹", Modifier.clickable(role = Role.Button) { monthEpoch = month.minusMonths(1).toEpochDay() }.padding(12.dp), style = AppType.headerTitle, color = AppTheme.colors.text)
            Text(DateTimeFormatter.ofPattern("LLLL yyyy", strings.language.locale).format(if (expanded) month else today)
                .replaceFirstChar { it.titlecase(strings.language.locale) },
                Modifier.weight(1f).clickable(role = Role.Button) { expanded = !expanded }.padding(vertical = 8.dp).testTag("profile_calendar_expand"),
                style = interScaled(15f, InterWeight.semibold), color = AppTheme.colors.text)
            if (expanded) Text("›", Modifier.clickable(role = Role.Button) { monthEpoch = month.plusMonths(1).toEpochDay() }.padding(12.dp), style = AppType.headerTitle, color = AppTheme.colors.text)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.5.dp)) {
            DayOfWeek.entries.forEach { day -> Text(day.getDisplayName(TextStyle.SHORT, strings.language.locale), Modifier.weight(1f),
                style = interScaled(10f, InterWeight.medium), color = AppTheme.colors.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        days.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.5.dp)) {
                week.forEach { day ->
                    val heat = calendarHeatStep(distance(day), scale)
                    val chosen = day != null && range.contains(day)
                    val fill = if (heat == null) empty else Color(ramp[heat])
                    Box(Modifier.weight(1f).aspectRatio(1f).background(if (day == null) Color.Transparent else fill, RoundedCornerShape(9.dp))
                        .then(if (chosen) Modifier.border(2.dp, AppTheme.accent, RoundedCornerShape(9.dp)) else Modifier)
                        .then(if (day != null) Modifier.clickable(role = Role.Button) {
                            val next = range.tap(day)
                            onRange(next.from?.atStartOfDay(zone)?.toInstant(), next.to?.atStartOfDay(zone)?.toInstant())
                        }.semantics { selected = chosen }.testTag("calendar_day_$day") else Modifier), contentAlignment = Alignment.Center) {
                        if (day != null) Text(day.dayOfMonth.toString(), style = interScaled(13f, InterWeight.semibold),
                            color = if (heat != null && heat > 0) Color.White else AppTheme.colors.text)
                    }
                }
            }
        }
        if (from != null) MeRow(strings.calendarFilterActive(filteredCount), strings.calendarClearFilter(), tag = "profile_calendar_clear", onClick = { onRange(null, null) })
    }
}
