package com.onezee.triptrack.feature.trip.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.toArgb
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
import com.onezee.triptrack.logic.GeometryUtils
import com.onezee.triptrack.logic.radius
import java.time.Instant
import java.util.UUID
import com.onezee.triptrack.map.MapHost
import com.onezee.triptrack.map.MapHostState
import com.onezee.triptrack.map.camera.MapCameraCommand
import com.onezee.triptrack.map.camera.MapInsets
import com.onezee.triptrack.map.route.MapRoutes
import com.onezee.triptrack.map.route.RouteAppearance
import com.onezee.triptrack.map.route.RouteGeometry
import com.onezee.triptrack.map.route.RouteLine
import com.onezee.triptrack.map.route.RouteShape
import com.onezee.triptrack.map.route.SpeedColorScale
import com.onezee.triptrack.map.marker.MapMarker
import com.onezee.triptrack.feature.account.HouseSwitch
import com.onezee.triptrack.ui.LightRoutePreview
import com.onezee.triptrack.ui.LiveMaps
import com.onezee.triptrack.ui.LocalLiveMaps
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
    localityLookup: suspend (GeoPoint) -> String? = { null },
) {
    val strings = appStrings
    val colors = AppTheme.colors
    var parts by remember(trip.id) { mutableStateOf(trip.hiddenParts ?: TripHiddenParts()) }
    var usePublicPreview by remember { mutableStateOf(false) }
    var selectingSection by remember { mutableStateOf(false) }
    var sectionStart by remember { mutableStateOf<Instant?>(null) }
    val result by produceState(TripHiddenPartsResolver.resolve(emptyList(), emptyList(), 0, 0, emptyList(), null),
        trip.trackPoints, trip.recordingBreaks, parts, defaultEdgeMetres, home) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            TripHiddenPartsResolver.resolve(
                trip.trackPoints, trip.recordingBreaks, parts.startMetres ?: defaultEdgeMetres ?: 0,
                parts.endMetres ?: defaultEdgeMetres ?: 0, parts.sections, home,
            )
        }
    }
    val distanceUnit = LocalDistanceUnit.current
    val hiddenPhotoCount = remember(trip.photos, result, home) { trip.photos.count { photo ->
        TripHiddenPartsResolver.photoIsHidden(photo.capturedAt,
            photo.exifLatitude?.let { lat -> photo.exifLongitude?.let { lon -> GeoPoint(lat, lon) } },
            trip.trackPoints, result, home)
    } }
    val firstPoint = trip.trackPoints.firstOrNull()
    val lastPoint = trip.trackPoints.lastOrNull()
    val startLocality by produceState<String?>(null, trip.id, firstPoint, localityLookup) {
        value = firstPoint?.let { localityLookup(GeoPoint(it.latitude, it.longitude)) }
    }
    val endLocality by produceState<String?>(null, trip.id, lastPoint, localityLookup) {
        value = lastPoint?.let { localityLookup(GeoPoint(it.latitude, it.longitude)) }
    }
    val mapState = remember { MapHostState() }
    val mapShape = remember(trip.trackPoints, result, usePublicPreview) {
        if (usePublicPreview) RouteGeometry.build(
            result.previewPoints, preview = true, breaks = result.previewBreaks,
        ) else hiddenEditorRoute(trip, result)
    }
    val lockMarkers = remember(trip.trackPoints, result, usePublicPreview) {
        if (usePublicPreview) emptyList() else hiddenRouteLockMarkers(trip.trackPoints, result.hidden)
    }
    LaunchedEffect(mapShape.bounds) {
        mapShape.bounds?.let { bounds -> mapState.execute(MapCameraCommand.FitRoute(bounds, MapInsets.all(30.0), animated = false)) }
    }
    val summary = hiddenPartsSummary(strings, parts, defaultEdgeMetres, distanceUnit)

    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(colors.bg)) {
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(224.dp)) {
                if (LocalLiveMaps.current) MapHost(
                    state = mapState,
                    modifier = Modifier.fillMaxSize(),
                    routes = MapRoutes(listOf(mapShape), RouteAppearance.plain.copy(hiddenInkArgb = colors.text.copy(alpha = 0.55f).toArgb())),
                    markers = lockMarkers,
                    onMapTap = { tap ->
                        val nearest = trip.trackPoints.indices.minByOrNull { index ->
                            GeometryUtils.haversineDistance(tap.coordinate, GeoPoint(trip.trackPoints[index].latitude, trip.trackPoints[index].longitude))
                        } ?: return@MapHost
                        val stamp = trip.trackPoints[nearest].timestamp
                        if (!selectingSection) return@MapHost
                        val start = sectionStart
                        if (start == null) sectionStart = stamp
                        else {
                            val section = HiddenSection(UUID.randomUUID(), minOf(start, stamp), maxOf(start, stamp))
                            parts = parts.copy(sections = parts.sections + section)
                            sectionStart = null
                            selectingSection = false
                        }
                    },
                    systemInsets = WindowInsets(0),
                    styleUri = LiveMaps.style(AppTheme.isDark),
                    textureMode = true,
                    gesturesEnabled = true,
                ) else HiddenRouteCanvas(
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
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(colors.card)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp)
                    .testTag("hidden_editor_sheet"),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.tr(R.string.hiddenEditorTitle), Modifier.weight(1f), style = inter(19f, InterWeight.bold), color = colors.text)
                    Text(strings.done(), Modifier.clickable(role = Role.Button) { onSave(parts.takeIf { it != TripHiddenParts() }); onDismissRequest() }
                        .testTag("hidden_editor_done"), style = inter(15f, InterWeight.bold), color = AppTheme.accent)
                }
                Text(if (!selectingSection) summary else if (sectionStart == null) strings.hiddenTapStart() else strings.hiddenTapEnd(), style = inter(13f), color = colors.textSecondary)
                HiddenTripBar(trip, result.hidden, parts.sections, strings)
                HiddenEdgePicker(strings.hiddenStart(), parts.startMetres, defaultEdgeMetres) { parts = parts.copy(startMetres = it) }
                HiddenEdgePicker(strings.hiddenEnd(), parts.endMetres, defaultEdgeMetres) { parts = parts.copy(endMetres = it) }
                if (parts.sections.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    parts.sections.forEach { section ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${section.from} · ${section.to}", Modifier.weight(1f), style = inter(12f), color = colors.text)
                            Text("×", Modifier.clickable(role = Role.Button) { parts = parts.copy(sections = parts.sections - section) }.padding(horizontal = 10.dp, vertical = 6.dp), style = inter(20f), color = colors.textSecondary)
                        }
                    }
                }
                Text(
                    if (!selectingSection) strings.hiddenAddSection() else if (sectionStart == null) strings.hiddenTapStart() else strings.hiddenTapEnd(),
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.cardAlt)
                        .clickable(role = Role.Button) { selectingSection = true; sectionStart = null }
                        .padding(horizontal = 14.dp, vertical = 12.dp).testTag("hidden_add_section"),
                    style = inter(14f, InterWeight.medium), color = colors.text,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.tr(R.string.hiddenNumbersTitle), Modifier.weight(1f), style = inter(14f, InterWeight.medium), color = colors.text)
                    listOf(false to strings.hiddenAllTrip(), true to strings.hiddenWithoutHidden()).forEach { (selected, label) ->
                        val active = parts.numbersVisibleOnly == selected
                        Text(label, Modifier.clip(CircleShape).background(if (active) AppTheme.accent else colors.cardAlt)
                            .clickable(role = Role.Button) { parts = parts.copy(numbersVisibleOnly = selected) }
                            .padding(horizontal = 10.dp, vertical = 8.dp).testTag("hidden_numbers_$selected"),
                            style = inter(12f, if (active) InterWeight.bold else InterWeight.medium), color = if (active) Color.White else colors.text)
                    }
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.cardAlt).padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(strings.hiddenLocalCardTitle(), style = inter(13f, InterWeight.bold), color = colors.text)
                    Text(hiddenPartsLocalCopy(strings, result.hiddenRanges.size, hiddenPhotoCount), style = inter(12f), color = colors.textSecondary)
                }
                Row(Modifier.fillMaxWidth().toggleable(usePublicPreview, role = Role.Switch, onValueChange = { usePublicPreview = it }), verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.hiddenShowPublic(), Modifier.weight(1f), style = inter(14f), color = colors.text)
                    HouseSwitch(usePublicPreview, { usePublicPreview = it })
                }
                val titleEnd = trip.title?.takeIf { it.isNotBlank() }
                val startHidden = (parts.startMetres ?: defaultEdgeMetres ?: 0) > 0
                val endHidden = (parts.endMetres ?: defaultEdgeMetres ?: 0) > 0
                val titleNamesStart = !trip.titleIsCustom && titleEnd != null && startHidden &&
                    !startLocality.isNullOrBlank() && titleEnd.contains(startLocality!!, ignoreCase = true)
                val titleNamesFinish = !trip.titleIsCustom && titleEnd != null && endHidden &&
                    !endLocality.isNullOrBlank() && titleEnd.contains(endLocality!!, ignoreCase = true)
                if (titleNamesStart || titleNamesFinish) {
                    val hint = if (titleNamesStart) strings.hiddenTitleHintStart(titleEnd!!) else strings.hiddenTitleHint(titleEnd!!)
                    Text(hint, Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onRename)
                        .testTag(if (titleNamesStart) "hidden_title_warning_start" else "hidden_title_warning_finish"),
                        style = inter(12.5f), color = AppTheme.accent)
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
            if (value == null && default != null) Text(strings.hiddenDefault(), style = inter(11f), color = colors.textSecondary)
            if (value != null && default != null) Text(strings.hiddenDefault(), Modifier.clickable(role = Role.Button) { onPick(null) }, style = inter(11f), color = colors.textSecondary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 200, 500, 1000, 2000).forEach { option ->
                val chosen = (value ?: default ?: 0) == option
                val labelText = if (option == 0) strings.hiddenNone() else Measure.radius(option.toDouble(), LocalDistanceUnit.current, strings.language.code, strings)
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

private fun hiddenEditorRoute(trip: Trip, result: com.onezee.triptrack.logic.TripHiddenPartsResolution): RouteShape {
    val visible = result.visibleRuns.flatMap { range ->
        val points = trip.trackPoints.subList(range.first, range.last + 1)
        RouteGeometry.build(points.map { GeoPoint(it.latitude, it.longitude) }, points.map { it.speed }).lines
    }
    val segments = trip.trackPoints.map { point -> trip.recordingBreaks.count { point.timestamp >= it } }
    val hidden = com.onezee.triptrack.logic.TripHiddenPartsResolver.drawnHiddenRuns(result.hidden, segments).map { range ->
        RouteLine(range.map { trip.trackPoints[it].let { p -> GeoPoint(p.latitude, p.longitude) } }, null, hidden = true)
    }
    val all = trip.trackPoints.map { GeoPoint(it.latitude, it.longitude) }
    val speeds = trip.trackPoints.map { it.speed }
    val bounds = RouteGeometry.build(all, speeds, breaks = trip.recordingBreaks.map { boundary ->
        trip.trackPoints.indexOfFirst { it.timestamp >= boundary }
    }.filter { it > 0 }).bounds
    return RouteShape(visible, bounds, hidden)
}

internal fun hiddenRouteLockMarkers(points: List<TrackPoint>, hidden: List<Boolean>): List<MapMarker> {
    val icon = hiddenLockBitmap()
    val markers = mutableListOf<MapMarker>()
    var index = 0
    while (index < hidden.size) {
        if (!hidden[index]) { index++; continue }
        val first = index
        while (index + 1 < hidden.size && hidden[index + 1]) index++
        listOf(first - 1, index + 1).filter { it in points.indices }.forEachIndexed { offset, pointIndex ->
            val point = points[pointIndex]
            markers += MapMarker("hidden_lock_${first}_$offset", GeoPoint(point.latitude, point.longitude),
                MapMarker.Kind.HIDDEN_LOCK, label = "", image = icon)
        }
        index++
    }
    return markers
}

internal fun hiddenLockBitmap(): Bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).also { bitmap ->
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(24f, 24f, 23f, paint)
    paint.color = android.graphics.Color.rgb(90, 91, 101)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 3f
    canvas.drawRoundRect(RectF(16f, 13f, 32f, 29f), 8f, 8f, paint)
    paint.style = Paint.Style.FILL
    canvas.drawRoundRect(RectF(14f, 22f, 34f, 38f), 3f, 3f, paint)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(24f, 28f, 2f, paint)
    canvas.drawRect(23f, 28f, 25f, 33f, paint)
}

@Composable
private fun HiddenTripBar(
    trip: Trip,
    hidden: List<Boolean>,
    sections: List<HiddenSection>,
    strings: com.onezee.triptrack.designsystem.strings.AppStrings,
) {
    val start = trip.trackPoints.firstOrNull()?.timestamp ?: trip.startDate
    val end = trip.trackPoints.lastOrNull()?.timestamp ?: trip.endDate ?: start
    val duration = (end.toEpochMilli() - start.toEpochMilli()).coerceAtLeast(0).toDouble()
    val colors = AppTheme.colors
    Canvas(Modifier.fillMaxWidth().height(24.dp).testTag("hidden_trip_bar")) {
        val y = size.height / 2
        val barHeight = 12.dp.toPx()
        drawRoundRect(colors.cardAlt, topLeft = Offset(0f, y - barHeight / 2), size = androidx.compose.ui.geometry.Size(size.width, barHeight), cornerRadius = androidx.compose.ui.geometry.CornerRadius(barHeight / 2))
        val points = trip.trackPoints
        if (points.size > 1) {
            for (index in 0 until points.lastIndex) {
                val left = size.width * index / points.lastIndex
                val right = size.width * (index + 1) / points.lastIndex
                val color = if (hidden.getOrNull(index) == true) colors.textTertiary.copy(alpha = .65f)
                    else Color(SpeedColorScale.argb(points[index].speed))
                drawLine(color, Offset(left, y), Offset(right, y), strokeWidth = barHeight, cap = StrokeCap.Round)
                if (hidden.getOrNull(index) == true) {
                    var x = left - barHeight
                    while (x < right) {
                        drawLine(Color.White.copy(alpha = .7f), Offset(x, y + barHeight / 2), Offset(x + barHeight, y - barHeight / 2), strokeWidth = 1.2.dp.toPx())
                        x += 6.dp.toPx()
                    }
                }
            }
        }
        sections.forEach { section ->
            listOf(section.from, section.to).forEach { time ->
                val fraction = if (duration > 0) ((time.toEpochMilli() - start.toEpochMilli()) / duration).toFloat().coerceIn(0f, 1f) else 0f
                drawCircle(colors.card, radius = 8.dp.toPx(), center = Offset(size.width * fraction, y))
                drawCircle(colors.textSecondary, radius = 8.dp.toPx(), center = Offset(size.width * fraction, y), style = Stroke(1.dp.toPx()))
            }
        }
    }
    val clock = java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT).withLocale(strings.language.locale)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(clock.format(start.atZone(strings.zone)), style = inter(10f), color = AppTheme.colors.textTertiary)
        Text(clock.format(end.atZone(strings.zone)), style = inter(10f), color = AppTheme.colors.textTertiary)
    }
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
