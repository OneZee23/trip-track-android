// iOS: ProfileHistoryCalendar.swift
package com.onezee.triptrack.feature.me

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.ui.LocalClock
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.delay
import kotlin.math.abs

// iOS: ProfileHistoryCalendar.handleDayTap. A full range restarts on the next tap.
internal data class CalendarRange(val from: LocalDate? = null, val to: LocalDate? = null) {
    fun tap(day: LocalDate): CalendarRange = when {
        from == null || to != null -> CalendarRange(day)
        day == from -> CalendarRange()
        day < from -> CalendarRange(day, from)
        else -> CalendarRange(from, day)
    }
    fun contains(day: LocalDate): Boolean = from != null && day >= from && day <= (to ?: from)
    /** iOS isRangeStart / isRangeEnd: a selected end of the range, which is drawn inverted. */
    fun isEndpoint(day: LocalDate): Boolean = day == from || day == to
}

internal fun calendarHeatStep(km: Double, scale: Double): Int? = when {
    km <= 0 -> null
    scale <= 0 -> 3
    km / scale <= .25 -> 0
    km / scale <= .5 -> 1
    km / scale <= .75 -> 2
    else -> 3
}

/** How a day is drawn: its fill, its numeral and, for today, the 2 pt ring. */
internal data class CalendarDayLook(val fill: Color, val numeral: Color, val ring: Color?)

/**
 * iOS dayFill / dayNumberColor. Selection is inverted rather than tinted (every heat step is a warm
 * orange, so an accent day would read as «a lot of km here»): the ends of the range take the ink as
 * their fill and the card colour as their numeral, the days between take the ink at 16 %. A future
 * day is the card-alt at 40 % with a numeral at 35 %. The palest step keeps the dark numeral, the
 * warmer ones turn it white; an empty day's numeral is tertiary.
 */
internal fun calendarDayLook(heat: Int?, isFuture: Boolean, inRange: Boolean, isEndpoint: Boolean, isToday: Boolean, colors: AppColors, dark: Boolean): CalendarDayLook {
    // The light tints are painted on a white card; on the dark card each step carries a darker twin.
    val ramp = if (dark) listOf(0xFF423C38, 0xFF694D3A, 0xFF9D512B, 0xFFC44021) else listOf(0xFFFCDBB8, 0xFFFAA86B, 0xFFF27333, 0xFFDB4521)
    val empty = Color(if (dark) 0xFF2C2A28 else 0xFFEBE8E5)
    val fill = when {
        isFuture -> colors.cardAlt.copy(alpha = colors.cardAlt.alpha * .4f)
        isEndpoint -> colors.text
        inRange -> colors.text.copy(alpha = colors.text.alpha * .16f)
        heat == null -> empty
        else -> Color(ramp[heat])
    }
    val numeral = when {
        isFuture -> colors.textTertiary.copy(alpha = colors.textTertiary.alpha * .35f)
        isEndpoint -> colors.card
        inRange -> colors.text
        heat == null -> colors.textTertiary
        heat == 0 -> colors.text
        else -> Color.White
    }
    // Inside a selected end the ring turns to the card colour, so «today» survives on the inverted fill.
    return CalendarDayLook(fill, numeral, if (isToday) (if (isEndpoint) colors.card else colors.text) else null)
}

/** iOS ProfileDateFormat.monthName: «Ноябрь», the standalone month, capitalised. */
internal fun monthName(date: LocalDate, locale: java.util.Locale): String =
    DateTimeFormatter.ofPattern("LLLL", locale).format(date).replaceFirstChar { it.titlecase(locale) }

/**
 * iOS ProfileHistoryCalendar: the date filter above the history. Collapsed it is the current week
 * with the month at its right; opened it is the month, stepped by chevrons at both ends or by a
 * swipe. A tap sets the start, a second the end, the same day twice clears.
 */
@Composable
internal fun ProfileHistoryCalendar(calendar: HistoryCalendar, from: Instant?, to: Instant?, filteredCount: Int,
                                     onRange: (Instant?, Instant?) -> Unit,
                                     today: LocalDate = LocalClock.current.instant().atZone(appStrings.zone).toLocalDate(),
                                     onEmptyDayTap: ((LocalDate) -> Unit)? = null) {
    val strings = appStrings
    val zone = strings.zone
    val locale = strings.language.locale
    val colors = AppTheme.colors
    val dark = AppTheme.isDark
    val haptics = rememberHaptics()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var monthEpoch by rememberSaveable { mutableLongStateOf(today.withDayOfMonth(1).toEpochDay()) }
    var bounced by remember { mutableStateOf<LocalDate?>(null) }
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
    // The busiest day among those drawn, capped by the all-time busiest, so the scale never exceeds reality.
    val peak = days.maxOfOrNull(::distance) ?: 0.0
    val scale = if (calendar.maxKmDay > 0) minOf(peak, calendar.maxKmDay) else peak
    fun step(months: Long) { monthEpoch = month.plusMonths(months).toEpochDay() }
    val reduceMotion = LocalReduceMotion.current
    val swipeThreshold = with(androidx.compose.ui.platform.LocalDensity.current) { 50.dp.toPx() }

    LaunchedEffect(bounced) { if (bounced != null) { delay(300); bounced = null } }

    Column(Modifier.fillMaxWidth().surfaceCard(16.dp).padding(14.dp).then(if (reduceMotion) Modifier else Modifier.animateContentSize(tween(250))).testTag("profile_calendar"),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (expanded) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MonthStep(AppGlyph.chevronLeft) { haptics.tap(); step(-1) }
                Spacer(Modifier.weight(1f))
                val name = monthName(month, locale)
                val title = if (month.year != today.year) "$name ${month.year}" else name
                Row(Modifier.clickable(role = Role.Button) { haptics.tap(); expanded = false }.testTag("profile_calendar_expand"),
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = inter(15f, InterWeight.bold), color = colors.text)
                    Glyph(AppGlyph.chevronUp, null, tint = colors.textSecondary, size = symbolBox(16f))
                }
                Spacer(Modifier.weight(1f))
                MonthStep(AppGlyph.chevronRight) { haptics.tap(); step(1) }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(strings.thisWeek(), Modifier.weight(1f), style = inter(15f, InterWeight.bold), color = colors.text)
                // Open where the filter already lives, so a March range is not five taps of paging away. The 44 pt
                // target is the hit area's, as on iOS (`tapTarget`): the row keeps the height of its content.
                Row(Modifier.clickable(role = Role.Button) {
                    haptics.tap(); monthEpoch = (range.from ?: today).withDayOfMonth(1).toEpochDay(); expanded = true
                }.testTag("profile_calendar_month"), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Today's month, not the displayed one: collapsed always shows the current week.
                    Text(monthName(today, locale), style = inter(13f, InterWeight.semibold), color = colors.textSecondary)
                    Glyph(AppGlyph.chevronDown, null, tint = colors.textSecondary, size = symbolBox(16f))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
            DayOfWeek.entries.forEach { day ->
                Text(day.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.titlecase(locale) }, Modifier.weight(1f),
                    style = inter(11f, InterWeight.semibold), color = colors.textTertiary, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
        val grid: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                days.chunked(7).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                        week.forEach { day ->
                            if (day == null) Spacer(Modifier.weight(1f).aspectRatio(1f))
                            else {
                                val look = calendarDayLook(calendarHeatStep(distance(day), scale), day > today, range.contains(day), range.isEndpoint(day), day == today, colors, dark)
                                val bounce by animateFloatAsState(if (bounced == day) 1.15f else 1f, spring(dampingRatio = .5f, stiffness = Spring.StiffnessMedium), label = "dayBounce")
                                val shape = RoundedCornerShape(8.dp)
                                Box(Modifier.weight(1f).aspectRatio(1f).graphicsLayer { scaleX = bounce; scaleY = bounce }.background(look.fill, shape)
                                    .then(if (look.ring != null) Modifier.border(2.dp, look.ring, shape) else Modifier)
                                    // Every day filters, including days without recorded distance; a day to come cannot.
                                    .clickable(enabled = day <= today, role = Role.Button) {
                                        haptics.tap(); bounced = day
                                        val hasTrip = calendar.kmByDay.keys.any { it.atZone(zone).toLocalDate() == day }
                                        if (!hasTrip && onEmptyDayTap != null) onEmptyDayTap(day) else {
                                            val next = range.tap(day)
                                            onRange(next.from?.atStartOfDay(zone)?.toInstant(), next.to?.atStartOfDay(zone)?.toInstant())
                                        }
                                    }.semantics { selected = range.contains(day); contentDescription = day.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG).withLocale(locale)) }
                                    .testTag("calendar_day_$day"), contentAlignment = Alignment.Center) {
                                    Text(day.dayOfMonth.toString(), style = inter(13f, InterWeight.semibold), color = look.numeral)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (expanded) Box(Modifier.pointerInput(month) {
            // Horizontal dominance only, or the card steals the profile's vertical scroll on a sloppy swipe.
            var total = 0f
            detectHorizontalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (abs(total) > swipeThreshold) step(if (total < 0) 1 else -1) }) { _, delta -> total += delta }
        }) { grid() } else grid()
        if (range.from != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(strings.calendarFilterActive(filteredCount), Modifier.weight(1f), style = inter(12f, InterWeight.semibold), color = colors.textSecondary, maxLines = 1)
            Text(strings.calendarClearFilter(), Modifier.clickable(role = Role.Button) { haptics.tap(); onRange(null, null) }.testTag("profile_calendar_clear"),
                style = inter(12f, InterWeight.bold), color = AppTheme.accent, maxLines = 1)
        }
    }
}

/** The 32 pt step at either end of the open month: a 14 pt chevron in the secondary ink. */
@Composable
private fun MonthStep(glyph: AppGlyph, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Glyph(glyph, null, tint = AppTheme.colors.textSecondary, size = symbolBox(14f))
    }
}

/** iOS cellGap: 4.5 pt between the squares, which themselves scale with the row. */
private val CELL_GAP = 4.5.dp
