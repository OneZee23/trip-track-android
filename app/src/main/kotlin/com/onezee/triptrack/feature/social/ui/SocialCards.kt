// iOS: JourneyCardView.swift (title, meta, window days), PublicProfileView.tripGridCell / gridMap,
// ProfileHistoryMonthHeader.swift
package com.onezee.triptrack.feature.social.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.journey.ui.JourneyFormat
import com.onezee.triptrack.feature.me.RoutePreview
import com.onezee.triptrack.feature.social.LocalSocial
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import com.onezee.triptrack.ui.RelativeTripDate
import com.onezee.triptrack.ui.TripAutoTitle
import com.onezee.triptrack.ui.cards.JourneyCard
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** iOS JourneyCardView's prepared text: the pill's days, the title ladder and the summary line. */
data class JourneyCardModel(val title: String, val meta: String, val days: Int, val routes: List<List<GeoPoint>>, val thumbs: List<List<GeoPoint>>) {
    companion object {
        fun build(journey: Journey, legs: List<Trip>, strings: AppStrings, unit: DistanceUnit, names: Map<String, String>, zone: ZoneId = strings.zone): JourneyCardModel {
            val aggregate = JourneyAggregate.build(legs, zone)
            val windowEnd = journey.endDate ?: legs.maxOfOrNull { it.endDate ?: it.startDate } ?: journey.startDate
            // Days by the legs, and without legs by the window itself: an empty window still has its dates.
            val days = if (aggregate.calendarDays > 0) aggregate.calendarDays
                else maxOf(ChronoUnit.DAYS.between(journey.startDate.atZone(zone).toLocalDate(), windowEnd.atZone(zone).toLocalDate()).toInt() + 1, 1)
            val range = JourneyFormat.dateRange(journey.startDate, windowEnd, strings)
            fun name(point: GeoPoint?) = point?.let { names[key(it)] }
            val title = JourneyFormat.title(journey.title, name(aggregate.firstStart), name(aggregate.farthestEnd), range)
            val meta = if (legs.isEmpty()) strings.journeyEmptyTitle() else buildList {
                if (title != range) add(range)
                add(Measure.distance(aggregate.totalMetres, unit, strings.language.code, strings, Measure.Style.grouped))
                add("${aggregate.legCount} ${strings.nounTrips(aggregate.legCount)}")
                if (aggregate.localTripCount > 0) add(strings.journeyAroundTownCount(aggregate.localTripCount))
                JourneyFormat.duration(aggregate.drivingSeconds, strings).takeIf { it.isNotEmpty() }
                    ?.let { add("$it ${strings.journeyDrivingLabel().lowercase(strings.language.locale)}") }
            }.joinToString(" · ")
            return JourneyCardModel(title, meta, days, legs.map { it.previewCoordinates }.filter { it.size > 1 },
                legs.sortedByDescending { it.startDate }.take(4).map { it.previewCoordinates })
        }

        /** The geocoder cache is keyed by geohash 5, the journey screen's own key. */
        fun key(point: GeoPoint) = GeohashEncoder.encode(point.latitude, point.longitude, 5)
    }
}

/** Someone's journey as a card of their history: names only from the geocoder's cache, never a request. */
@Composable
fun PublicJourneyCard(journey: Journey, legs: List<Trip>, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val strings = appStrings
    val unit = LocalDistanceUnit.current
    val trips = LocalSocial.current?.trips
    val keys = remember(legs) { JourneyAggregate.build(legs, strings.zone).let { listOfNotNull(it.firstStart, it.farthestEnd) }.map(JourneyCardModel::key).toSet() }
    val names by produceState(emptyMap<String, String>(), keys, trips) { if (trips != null && keys.isNotEmpty()) value = runCatching { trips.localities(keys) }.getOrDefault(emptyMap()) }
    val model = remember(journey, legs, names, strings, unit) { JourneyCardModel.build(journey, legs, strings, unit, names) }
    JourneyCard(model.title, model.meta, model.days, model.routes, model.thumbs, onTap, modifier)
}

/**
 * iOS tripGridCell: the compact half of the toggle, three lines: map, title, «дата · км». Time and
 * tallies made it a small bad card instead of a good tile.
 */
@Composable
fun TripGridCell(trip: SocialFeedTrip, now: Instant, onTap: (() -> Unit)?, modifier: Modifier = Modifier) {
    val strings = appStrings
    val colors = AppTheme.colors
    val unit = LocalDistanceUnit.current
    val coordinates = remember(trip.previewPolyline) { trip.previewPolyline?.let { runCatching { Trip.decodePolyline(java.util.Base64.getDecoder().decode(it)) }.getOrNull() }.orEmpty() }
    val body = @Composable {
        Column(Modifier.fillMaxWidth().surfaceCard(14.dp).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(12.dp))) {
                if (coordinates.size > 1) RoutePreview(coordinates, Modifier.matchParentSize())
                else Box(Modifier.matchParentSize().padding(0.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.layout.Spacer(Modifier.matchParentSize())
                    Glyph(AppGlyph.mapSlash, null, tint = colors.textTertiary, size = 16.dp)
                }
            }
            Text(TripAutoTitle.localized(trip.title, trip.startDate, strings)?.takeIf { it.isNotEmpty() } ?: strings.longDate(trip.startDate),
                style = inter(12f, InterWeight.bold), color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(RelativeTripDate.string(trip.startDate, now, strings), style = inter(10.5f, InterWeight.semibold, monospacedDigits = true), color = colors.textTertiary, maxLines = 1)
                Text("·", style = inter(10.5f, InterWeight.semibold), color = colors.textTertiary)
                Text(Measure.distance(trip.distance, unit, strings.language.code, strings, Measure.Style.adaptive), style = inter(10.5f, InterWeight.semibold, monospacedDigits = true),
                    color = colors.textSecondary, maxLines = 1)
            }
        }
    }
    if (onTap == null) Box(modifier) { body() }
    else PressableCard(onClick = onTap, modifier = modifier.testTag("profile_trip_${trip.id.toString().take(8)}")) { body() }
}

/** iOS ProfileHistoryMonthHeader: the month, then its distance, trips and regions. */
@Composable
fun ChapterHeader(chapter: ProfileHistoryMonth, modifier: Modifier = Modifier) {
    val strings = appStrings
    val unit = LocalDistanceUnit.current
    Column(modifier.padding(top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(chapter.heading, style = interScaled(24f, InterWeight.heavy), color = AppTheme.colors.text, modifier = Modifier.semantics { heading() }.testTag("profile_month_header"))
        Text(strings.profileChapterSummary(Measure.distance(chapter.distance, unit, strings.language.code, strings), chapter.tripCount, chapter.regionCount),
            style = interScaled(13f), color = AppTheme.colors.textSecondary)
    }
}
