package com.onezee.triptrack.feature.trip.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onezee.triptrack.designsystem.R
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.LocalDistanceUnit
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.HiddenSection
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.model.TrackPoint
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.model.TripHiddenParts
import com.onezee.triptrack.logic.TripHiddenPartsResolver
import com.onezee.triptrack.logic.distance
import java.time.Instant
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

// iOS: HiddenPartsEditor.swift.
@Composable
fun HiddenPartsEditorSheet(
    trip: Trip,
    defaultEdgeMetres: Int?,
    home: Pair<GeoPoint, Double>?,
    onSave: (TripHiddenParts?) -> Unit,
    onDismissRequest: () -> Unit,
    onRename: () -> Unit = {},
) {
    val strings = appStrings
    val colors = AppTheme.colors
    var parts by remember(trip.id) { mutableStateOf(trip.hiddenParts ?: TripHiddenParts()) }
    var usePublicPreview by remember { mutableStateOf(false) }
    var selectingSection by remember { mutableStateOf(false) }
    var sectionStart by remember { mutableStateOf<Instant?>(null) }
    val result = remember(trip.trackPoints, trip.recordingBreaks, parts, defaultEdgeMetres, home) {
        TripHiddenPartsResolver.resolve(
            trip.trackPoints, trip.recordingBreaks, parts.startMetres ?: defaultEdgeMetres ?: 0,
            parts.endMetres ?: defaultEdgeMetres ?: 0, parts.sections, home,
        )
    }
    val distanceUnit = LocalDistanceUnit.current
    val hiddenPhotoCount = remember(trip.photos, result, home) { trip.photos.count { photo ->
        TripHiddenPartsResolver.photoIsHidden(photo.capturedAt,
            photo.exifLatitude?.let { lat -> photo.exifLongitude?.let { lon -> GeoPoint(lat, lon) } },
            trip.trackPoints, result, home)
    } }
    val summary = buildList {
        val start = parts.startMetres ?: defaultEdgeMetres ?: 0
        val end = parts.endMetres ?: defaultEdgeMetres ?: 0
        add(strings.tr(R.string.hiddenSummaryStart).replace("{value}", Measure.distance(start.toDouble(), distanceUnit, strings.language.code, strings)))
        add(strings.tr(R.string.hiddenSummaryEnd).replace("{value}", Measure.distance(end.toDouble(), distanceUnit, strings.language.code, strings)))
        add(if (parts.sections.isEmpty()) strings.tr(R.string.hiddenNothing) else strings.tr(R.string.hiddenSummaryMiddle).replace("{count}", parts.sections.size.toString()))
    }.joinToString(" · ")

    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(colors.bg)) {
            HiddenRouteCanvas(
                points = trip.trackPoints,
                hidden = result.hidden,
                publicOnly = usePublicPreview,
                onPoint = { index ->
                    val stamp = trip.trackPoints.getOrNull(index)?.timestamp ?: return@HiddenRouteCanvas
                    if (!selectingSection) return@HiddenRouteCanvas
                    val start = sectionStart
                    if (start == null) sectionStart = stamp
                    else {
                        val section = HiddenSection(UUID.randomUUID(), minOf(start, stamp), maxOf(start, stamp))
                        parts = parts.copy(sections = parts.sections + section)
                        sectionStart = null
                        selectingSection = false
                    }
                },
            )
            Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(strings.cancel(), Modifier.clickable(role = Role.Button, onClick = onDismissRequest).testTag("hidden_editor_cancel"), style = inter(15f), color = colors.textSecondary)
                    Text(strings.tr(R.string.hiddenEditorTitle), Modifier.weight(1f), style = inter(17f, InterWeight.bold), color = colors.text, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(strings.done(), Modifier.clickable(role = Role.Button) { onSave(parts.takeIf { it != TripHiddenParts() }); onDismissRequest() }.testTag("hidden_editor_done"), style = inter(15f, InterWeight.bold), color = AppTheme.accent)
                }
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(colors.card)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp)
                    .testTag("hidden_editor_sheet"),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(if (!selectingSection) summary else strings.tr(if (sectionStart == null) R.string.hiddenPickStart else R.string.hiddenPickEnd), style = inter(13f), color = colors.textSecondary)
                HiddenEdgePicker(strings.tr(R.string.hiddenStartLabel), parts.startMetres, defaultEdgeMetres) { parts = parts.copy(startMetres = it) }
                HiddenEdgePicker(strings.tr(R.string.hiddenEndLabel), parts.endMetres, defaultEdgeMetres) { parts = parts.copy(endMetres = it) }
                if (parts.sections.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    parts.sections.forEach { section ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${section.from} · ${section.to}", Modifier.weight(1f), style = inter(12f), color = colors.text)
                            Text("×", Modifier.clickable(role = Role.Button) { parts = parts.copy(sections = parts.sections - section) }.padding(horizontal = 10.dp, vertical = 6.dp), style = inter(20f), color = colors.textSecondary)
                        }
                    }
                }
                Text(
                    if (!selectingSection) "+ ${strings.tr(R.string.hiddenAddSection)}" else strings.tr(if (sectionStart == null) R.string.hiddenPickStart else R.string.hiddenPickEnd),
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.cardAlt)
                        .clickable(role = Role.Button) { selectingSection = true; sectionStart = null }
                        .padding(horizontal = 14.dp, vertical = 12.dp).testTag("hidden_add_section"),
                    style = inter(14f, InterWeight.medium), color = colors.text,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.tr(R.string.hiddenNumbersTitle), Modifier.weight(1f), style = inter(14f, InterWeight.medium), color = colors.text)
                    listOf(false to R.string.hiddenNumbersWhole, true to R.string.hiddenNumbersVisible).forEach { (selected, label) ->
                        val active = parts.numbersVisibleOnly == selected
                        Text(strings.tr(label), Modifier.clip(CircleShape).background(if (active) AppTheme.accent else colors.cardAlt)
                            .clickable(role = Role.Button) { parts = parts.copy(numbersVisibleOnly = selected) }
                            .padding(horizontal = 10.dp, vertical = 8.dp).testTag("hidden_numbers_$selected"),
                            style = inter(12f, if (active) InterWeight.bold else InterWeight.medium), color = if (active) Color.White else colors.text)
                    }
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.cardAlt).padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(strings.tr(R.string.hiddenStaysTitle), style = inter(13f, InterWeight.bold), color = colors.text)
                    Text(strings.tr(R.string.hiddenStaysBody) + "\n" + strings.photosCount(hiddenPhotoCount), style = inter(12f), color = colors.textSecondary)
                }
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { usePublicPreview = !usePublicPreview }, verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.tr(R.string.hiddenPreviewToggle), Modifier.weight(1f), style = inter(14f), color = colors.text)
                    Text(if (usePublicPreview) "●" else "○", style = inter(20f), color = AppTheme.accent)
                }
                val titleEnd = trip.title?.takeIf { it.isNotBlank() }
                if (!trip.titleIsCustom && titleEnd != null) {
                    val endpoint = when {
                        (parts.endMetres ?: defaultEdgeMetres ?: 0) > 0 -> R.string.hiddenTitleNamesFinish
                        (parts.startMetres ?: defaultEdgeMetres ?: 0) > 0 -> R.string.hiddenTitleNamesStart
                        else -> null
                    }
                    endpoint?.let { key ->
                        Column(Modifier.fillMaxWidth().testTag(if (key == R.string.hiddenTitleNamesStart) "hidden_title_warning_start" else "hidden_title_warning_finish"), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(strings.tr(key).replace("{title}", titleEnd), style = inter(12.5f), color = colors.textSecondary)
                        Text(strings.tr(R.string.hiddenRename), Modifier.clickable(role = Role.Button, onClick = onRename), style = inter(12.5f, InterWeight.semibold), color = AppTheme.accent)
                        }
                    }
                }
                if (trip.isOnServer && !trip.isPrivate) {
                    Text(strings.tr(R.string.hiddenAlreadySeen), style = inter(12.5f), color = colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun HiddenEdgePicker(label: String, value: Int?, default: Int?, onPick: (Int?) -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = inter(14f, InterWeight.bold), color = colors.text)
            if (value == null && default != null) Text(strings.tr(R.string.hiddenByDefault), style = inter(11f), color = colors.textSecondary)
            if (value != null && default != null) Text(strings.tr(R.string.hiddenByDefault), Modifier.clickable(role = Role.Button) { onPick(null) }, style = inter(11f), color = colors.textSecondary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 200, 500, 1000, 2000).forEach { option ->
                val chosen = (value ?: default ?: 0) == option
                val labelText = if (option == 0) strings.tr(R.string.hiddenNone) else Measure.distance(option.toDouble(), LocalDistanceUnit.current, strings.language.code, strings)
                Text(labelText, Modifier.weight(1f).clip(CircleShape).background(if (chosen) AppTheme.accent else colors.cardAlt)
                    .clickable(role = Role.Button) { onPick(option) }.padding(vertical = 9.dp).testTag("hidden_${label}_${option}"),
                    style = inter(11f, if (chosen) InterWeight.bold else InterWeight.medium), color = if (chosen) Color.White else colors.text, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
private fun HiddenRouteCanvas(points: List<TrackPoint>, hidden: List<Boolean>, publicOnly: Boolean, onPoint: (Int) -> Unit) {
    val ink = AppTheme.colors.text
    val route = AppTheme.accent
    Canvas(Modifier.fillMaxSize().pointerInput(points, hidden) {
        detectTapGestures { tap ->
            if (points.isEmpty()) return@detectTapGestures
            val bounds = routePoints(points.map { GeoPoint(it.latitude, it.longitude) }, size.width.toFloat(), size.height.toFloat())
            val nearest = bounds.indices.minByOrNull { (bounds[it] - tap).getDistanceSquared() } ?: return@detectTapGestures
            onPoint(nearest)
        }
    }) {
        if (points.size < 2) return@Canvas
        val positions = routePoints(points.map { GeoPoint(it.latitude, it.longitude) }, size.width, size.height)
        val ranges = if (publicOnly) visibleRanges(hidden) else listOf(points.indices)
        ranges.forEach { range ->
            if (range.count() < 2) return@forEach
            val path = Path().apply { moveTo(positions[range.first].x, positions[range.first].y); range.drop(1).forEach { lineTo(positions[it].x, positions[it].y) } }
            drawPath(path, Color.White, style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, route, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (!publicOnly) {
            hiddenRanges(hidden).forEach { range ->
                if (range.count() < 2) return@forEach
                val path = Path().apply { moveTo(positions[range.first].x, positions[range.first].y); range.drop(1).forEach { lineTo(positions[it].x, positions[it].y) } }
                drawPath(path, Color.White, style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, ink.copy(alpha = .55f), style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 7.2f))))
            }
            for (i in 1 until points.lastIndex) if (hidden[i] != hidden[i - 1]) {
                val at = positions[i]
                drawCircle(Color.White, 11.dp.toPx(), at)
                drawCircle(ink, 1.3.dp.toPx(), at, style = Stroke(1.4.dp.toPx()))
                drawRoundRect(ink, topLeft = Offset(at.x - 3.dp.toPx(), at.y - 1.dp.toPx()), size = androidx.compose.ui.geometry.Size(6.dp.toPx(), 5.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
                drawArc(ink, 180f, 180f, false, Offset(at.x - 2.dp.toPx(), at.y - 5.dp.toPx()), androidx.compose.ui.geometry.Size(4.dp.toPx(), 7.dp.toPx()), style = Stroke(1.3.dp.toPx()))
            }
        }
    }
}

private fun routePoints(points: List<GeoPoint>, width: Float, height: Float): List<Offset> {
    if (points.isEmpty()) return emptyList()
    val minLat = points.minOf { it.latitude }; val maxLat = points.maxOf { it.latitude }
    val minLon = points.minOf { it.longitude }; val maxLon = points.maxOf { it.longitude }
    val scale = min((height * .57) / max(maxLat - minLat, .00001), (width * .78) / max(maxLon - minLon, .00001))
    val midLat = (minLat + maxLat) / 2; val midLon = (minLon + maxLon) / 2
    return points.map { Offset(width / 2 + ((it.longitude - midLon) * scale).toFloat(), height * .36f - ((it.latitude - midLat) * scale).toFloat()) }
}

private fun hiddenRanges(hidden: List<Boolean>): List<IntRange> = booleanRanges(hidden, true)
private fun visibleRanges(hidden: List<Boolean>): List<IntRange> = booleanRanges(hidden, false)
private fun booleanRanges(values: List<Boolean>, seek: Boolean): List<IntRange> {
    val ranges = mutableListOf<IntRange>(); var index = 0
    while (index < values.size) {
        if (values[index] != seek) { index++; continue }
        val start = index
        while (index + 1 < values.size && values[index + 1] == seek) index++
        ranges += start..index; index++
    }
    return ranges
}
