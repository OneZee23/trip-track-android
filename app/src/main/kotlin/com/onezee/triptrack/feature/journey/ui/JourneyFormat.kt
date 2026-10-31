package com.onezee.triptrack.feature.journey.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.strings.LocalizedDateFormatter
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.feature.trip.TripSummary
import com.onezee.triptrack.ui.LightRoutePreview
import com.onezee.triptrack.ui.RegionDisplay
import com.onezee.triptrack.ui.TripAutoTitle
import com.onezee.triptrack.ui.cards.dayMonth
import com.onezee.triptrack.ui.cards.hasDisplayableName
import com.onezee.triptrack.ui.durationText
import java.time.Instant

/**
 * iOS: JourneyFormat.swift. The dates of journeys: one ICU template per language, the abbreviation
 * dot kept («12–17 сент.», «Sep 12–17», «30 сент. – 2 окт.»).
 */
object JourneyFormat {
    fun dateRange(from: Instant, to: Instant, strings: AppStrings): String {
        val f = LocalizedDateFormatter.templates("dMMM", strings.zone).getValue(strings.language)
        val zone = strings.zone
        val a = f.format(from)
        val start = from.atZone(zone).toLocalDate()
        val end = to.atZone(zone).toLocalDate()
        if (start == end) return a
        val b = f.format(to)
        if (start.year == end.year && start.month == end.month) {
            val dayA = start.dayOfMonth.toString()
            val dayB = end.dayOfMonth.toString()
            if (a.startsWith(dayA)) return dayA + "–" + b
            if (a.endsWith(dayA)) return a + "–" + dayB
        }
        return "$a – $b"
    }

    /** «вс, 6 сент.», «Sun, Sep 6». */
    fun dayDate(date: Instant, strings: AppStrings): String =
        LocalizedDateFormatter.templates("dMMMEEE", strings.zone).getValue(strings.language).format(date)

    /** The `Hm` skeleton: 24-hour in every language, as iOS renders it. */
    fun time(date: Instant, strings: AppStrings): String =
        LocalizedDateFormatter.templates("Hm", strings.zone).getValue(strings.language).format(date)

    fun duration(seconds: Double, strings: AppStrings): String = durationText(seconds, strings)

    /** iOS: JourneyFormat.tripTitle. The name if it is one, else the region, else «14 апр». */
    fun tripTitle(trip: TripSummary, strings: AppStrings): String {
        if (trip.hasDisplayableName(strings)) TripAutoTitle.localized(trip.title, trip.startDate, strings)?.takeIf { it.isNotEmpty() }?.let { return it }
        RegionDisplay.localized(trip.region, strings.language)?.takeIf { it.isNotEmpty() }?.let { return it }
        return strings.dayMonth(trip.startDate)
    }

    /**
     * iOS: JourneyTitle.text. The journey's own name, else «Краснодар — Ростов-на-Дону» from the
     * cached localities of the first start and the farthest end, else the date range.
     */
    fun title(own: String?, startName: String?, farthestName: String?, dateRange: String): String {
        own?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val s = startName?.takeIf { it.isNotEmpty() }
        val f = farthestName?.takeIf { it.isNotEmpty() }
        val default = if (s != null && f != null && s != f) "$s — $f" else s ?: f
        return default?.takeIf { it.isNotEmpty() } ?: dateRange
    }
}

/**
 * iOS: JourneyLegRow.swift. A leg in the composer and the publish sheet: its route in a 56 × 44
 * thumbnail (a struck-out map when there is none), the title, a prepared subtitle, an optional
 * third line, and whatever the caller puts on the right.
 */
@Composable
fun JourneyLegRow(
    trip: TripSummary,
    subtitle: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    captionColor: Color = AppTheme.accent,
    trailing: @Composable () -> Unit = {},
) {
    val strings = appStrings
    val colors = AppTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        LegThumbnail(trip)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(JourneyFormat.tripTitle(trip, strings), style = inter(15f, InterWeight.bold), color = colors.text, maxLines = 1)
            Text(subtitle, style = inter(12f, InterWeight.medium), color = colors.textTertiary, maxLines = 1)
            if (caption != null) Text(caption, style = inter(10.5f, InterWeight.bold), color = captionColor, maxLines = 1)
        }
        trailing()
    }
}

/** The 56 × 44 route of a leg: drawn when it has one, the struck-out map otherwise. */
@Composable
fun LegThumbnail(trip: TripSummary) {
    val shape = RoundedCornerShape(8.dp)
    if (trip.coordinates.size > 1) {
        LightRoutePreview(listOf(trip.coordinates), Modifier.size(56.dp, 44.dp).clip(shape), padding = 6.dp)
    } else {
        Box(Modifier.size(56.dp, 44.dp).clip(shape).background(AppTheme.colors.card), contentAlignment = Alignment.Center) {
            Glyph(AppGlyph.mapSlash, contentDescription = null, tint = AppTheme.colors.textTertiary, size = 15.dp)
        }
    }
}
