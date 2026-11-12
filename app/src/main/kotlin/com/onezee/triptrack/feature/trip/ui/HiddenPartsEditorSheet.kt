package com.onezee.triptrack.feature.trip.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onezee.triptrack.designsystem.R
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.ToastHost
import com.onezee.triptrack.designsystem.components.ToastItem
import com.onezee.triptrack.designsystem.components.ToastType
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.LocalDistanceUnit
import com.onezee.triptrack.designsystem.theme.LocalReduceMotion
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import com.onezee.triptrack.feature.account.HouseSwitch
import com.onezee.triptrack.logic.HiddenLockHandleMath
import com.onezee.triptrack.logic.TripHiddenPartsResolver
import com.onezee.triptrack.logic.GeometryUtils
import com.onezee.triptrack.logic.distance
import com.onezee.triptrack.map.MapHost
import com.onezee.triptrack.map.MapHostState
import com.onezee.triptrack.map.camera.MapCameraCommand
import com.onezee.triptrack.map.camera.MapInsets
import com.onezee.triptrack.map.marker.MapMarker
import com.onezee.triptrack.map.route.MapRoutes
import com.onezee.triptrack.map.route.RouteAppearance
import com.onezee.triptrack.map.route.RouteGeometry
import com.onezee.triptrack.map.route.withSelectedRouteStyle
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.HiddenSection
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.model.TrackPoint
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.model.TripHiddenParts
import com.onezee.triptrack.ui.LiveMaps
import com.onezee.triptrack.ui.LocalLiveMaps
import com.onezee.triptrack.ui.durationText
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot

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
    focusedHandleKey: String? = null,
    onFocusConsumed: () -> Unit = {},
) {
    val strings = appStrings
    val colors = AppTheme.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = rememberHaptics()
    val reduceMotion = LocalReduceMotion.current || Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
    val partsState = remember(trip.id) { mutableStateOf(trip.hiddenParts ?: TripHiddenParts()) }
    var parts by partsState
    var resolvedParts by remember(trip.id) { mutableStateOf(trip.hiddenParts ?: TripHiddenParts()) }
    var usePublicPreview by remember { mutableStateOf(false) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var bubbleKey by remember { mutableStateOf<String?>(null) }
    var pulseKey by remember { mutableStateOf<String?>(null) }
    var pulseToken by remember { mutableIntStateOf(0) }
    var focusBubbleToken by remember { mutableIntStateOf(0) }
    var lastDetent by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<ToastItem?>(null) }
    var panelHeightDp by remember { mutableFloatStateOf(0f) }
    var panelTopPx by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var fallbackProjection by remember(trip.id) { mutableStateOf<Map<Int, HiddenLockHandleMath.ScreenPoint>?>(null) }
    val mapState = remember { MapHostState() }
    val displayedParts by rememberUpdatedState(parts)
    val initialResolution = remember {
        TripHiddenPartsResolver.resolve(emptyList(), emptyList(), 0, 0, emptyList(), null)
    }
    val result by produceState(
        initialResolution,
        trip.trackPoints,
        trip.recordingBreaks,
        resolvedParts,
        defaultEdgeMetres,
        home,
    ) {
        value = withContext(Dispatchers.Default) {
            TripHiddenPartsResolver.resolve(
                trip.trackPoints,
                trip.recordingBreaks,
                resolvedParts.startMetres ?: defaultEdgeMetres ?: 0,
                resolvedParts.endMetres ?: defaultEdgeMetres ?: 0,
                resolvedParts.sections,
                home,
            )
        }
    }
    LaunchedEffect(parts, draggingKey) {
        if (draggingKey == null) resolvedParts = parts
    }
    LaunchedEffect(draggingKey) {
        if (draggingKey != null) {
            while (true) {
                delay(50)
                if (resolvedParts != displayedParts) resolvedParts = displayedParts
            }
        }
    }

    val prefix = remember(trip.trackPoints, trip.recordingBreaks) {
        TripHiddenPartsResolver.odometerPrefix(trip.trackPoints, trip.recordingBreaks)
    }
    val distanceUnit = LocalDistanceUnit.current
    val handles = remember(parts, defaultEdgeMetres, prefix, trip.trackPoints) {
        HiddenLockHandleMath.handleIndices(trip.trackPoints, prefix, parts, defaultEdgeMetres)
    }
    val overlayHandles = remember(resolvedParts, defaultEdgeMetres, prefix, trip.trackPoints) {
        HiddenLockHandleMath.handleIndices(trip.trackPoints, prefix, resolvedParts, defaultEdgeMetres)
    }
    val handleValues = remember(handles, parts, prefix, trip.trackPoints, strings, distanceUnit) {
        handles.mapNotNull { handle ->
            HiddenLockPresentation.bubble(handle.key, trip.trackPoints, prefix, parts, handles, distanceUnit, strings)
                ?.let { handle.key to (it.title to it.caption) }
        }.toMap()
    }
    val handleLabels = remember(handles, strings) {
        handles.associate { it.key to HiddenLockPresentation.accessibilityLabel(it.key, strings) }
    }
    val summary = remember(parts, handles, prefix, trip.trackPoints, defaultEdgeMetres, strings, distanceUnit) {
        HiddenLockPresentation.summary(trip.trackPoints, prefix, parts, defaultEdgeMetres, handles, distanceUnit, strings)
    }
    val hiddenPhotoCount = remember(trip.photos, result, home) {
        trip.photos.count { photo ->
            TripHiddenPartsResolver.photoIsHidden(
                photo.capturedAt,
                photo.exifLatitude?.let { latitude -> photo.exifLongitude?.let { longitude -> GeoPoint(latitude, longitude) } },
                trip.trackPoints,
                result,
                home,
            )
        }
    }
    val firstPoint = trip.trackPoints.firstOrNull()
    val lastPoint = trip.trackPoints.lastOrNull()
    val startLocality by produceState<String?>(null, trip.id, firstPoint, localityLookup) {
        value = firstPoint?.let { localityLookup(GeoPoint(it.latitude, it.longitude)) }
    }
    val endLocality by produceState<String?>(null, trip.id, lastPoint, localityLookup) {
        value = lastPoint?.let { localityLookup(GeoPoint(it.latitude, it.longitude)) }
    }
    val liveMaps = LocalLiveMaps.current
    val routeShape = remember(trip.trackPoints, result.hidden, result.previewPoints, result.previewBreaks, usePublicPreview) {
        if (usePublicPreview) RouteGeometry.build(result.previewPoints, preview = true, breaks = result.previewBreaks)
        else hiddenEditorRoute(trip, result)
    }
    val recordingSegments = remember(trip.trackPoints, trip.recordingBreaks) {
        trip.trackPoints.map { point -> trip.recordingBreaks.count { point.timestamp >= it } }
    }
    val hiddenDrawingRuns = remember(result.hidden, recordingSegments) {
        TripHiddenPartsResolver.drawnHiddenRuns(result.hidden, recordingSegments)
    }
    val staticLockMarkers = remember(trip.trackPoints, result.hidden, overlayHandles, resolvedParts.sections, usePublicPreview) {
        if (usePublicPreview) emptyList() else {
            val ignored = overlayHandles.mapTo(HashSet()) { it.index }
            resolvedParts.sections.mapNotNull { HiddenLockHandleMath.sectionIndices(trip.trackPoints, it) }.forEach { range ->
                (range.first - 1..range.last + 1).filter { it in trip.trackPoints.indices }.forEach(ignored::add)
            }
            overlayHandles.firstOrNull { it.key == "end" && it.parkingOnly }?.let { ignored.add(it.index - 1) }
            hiddenRouteLockMarkers(trip.trackPoints, result.hidden).filterNot { marker ->
                val markerIndex = trip.trackPoints.indices.minByOrNull { index ->
                    val point = trip.trackPoints[index]
                    GeometryUtils.haversineDistance(marker.position, GeoPoint(point.latitude, point.longitude))
                }
                markerIndex in ignored
            }
        }
    }
    LaunchedEffect(routeShape.bounds, panelHeightDp) {
        routeShape.bounds?.let { bounds ->
            mapState.execute(MapCameraCommand.FitRoute(
                bounds,
                MapInsets(top = 18.0, left = 24.0, bottom = panelHeightDp.toDouble() + 12.0, right = 24.0),
                animated = false,
            ))
        }
    }

    val stops = remember(trip.trackPoints) { HiddenLockHandleMath.parkedStops(trip.trackPoints) }
    fun screenPoint(index: Int): HiddenLockHandleMath.ScreenPoint? {
        val point = trip.trackPoints.getOrNull(index) ?: return null
        return fallbackProjection?.get(index) ?: mapState.screenPosition(GeoPoint(point.latitude, point.longitude))
            ?.let { HiddenLockHandleMath.ScreenPoint(it.x.toDouble(), it.y.toDouble()) }
    }
    fun allowedSectionBounds(section: HiddenSection, range: IntRange): IntRange? {
        val startHandle = handles.firstOrNull { it.key == "start" }?.index ?: 0
        val endHandle = handles.firstOrNull { it.key == "end" }?.index ?: trip.trackPoints.lastIndex
        val sectionIndex = parts.sections.indexOfFirst { it.id == section.id }
        val previous = parts.sections.getOrNull(sectionIndex - 1)?.let { HiddenLockHandleMath.sectionIndices(trip.trackPoints, it)?.last }
        val next = parts.sections.getOrNull(sectionIndex + 1)?.let { HiddenLockHandleMath.sectionIndices(trip.trackPoints, it)?.first }
        val lower = maxOf(startHandle, previous?.plus(1) ?: startHandle)
        val upper = minOf(endHandle, next?.minus(1) ?: endHandle)
        return (lower..upper).takeIf { lower <= upper }
    }
    fun updateDetent(key: String, displayedMetres: Double, special: String? = null) {
        val target = special ?: HiddenLockHandleMath.nearestDetent(displayedMetres)?.toInt()?.toString()
        if (target == lastDetent) return
        lastDetent = target
        if (target != null) {
            haptics.selection()
            pulseKey = key
            pulseToken++
        }
    }
    fun pointsOutwardFromStop(
        finger: HiddenLockHandleMath.ScreenPoint,
        stopIndex: Int,
        behindStart: Int,
        behindEnd: Int,
    ): Boolean {
        val stop = screenPoint(stopIndex) ?: return false
        val behindRange = behindStart..behindEnd
        val nearestBehind = HiddenLockHandleMath.nearestPoint(
            ::screenPoint,
            currentIndex = behindStart,
            lowerBound = behindRange.first,
            upperBound = behindRange.last,
            finger = finger,
            pointCount = trip.trackPoints.size,
        )?.let { screenPoint(it.index) }
        return HiddenLockHandleMath.isOutwardPastStop(finger, stop, nearestBehind)
    }
    fun updateSectionBoundary(key: String, index: Int): Boolean {
        val sectionId = key.substringAfter(':')
        val section = parts.sections.firstOrNull { it.id.toString() == sectionId } ?: return false
        val currentRange = HiddenLockHandleMath.sectionIndices(trip.trackPoints, section) ?: return false
        val bounds = allowedSectionBounds(section, currentRange) ?: return false
        val inBounds = index.coerceIn(bounds.first, bounds.last)
        val pointTime = trip.trackPoints.getOrNull(inBounds)?.timestamp ?: return false
        val movedSection = when {
            key.startsWith("section-start:") -> section.copy(from = minOf(pointTime, section.to))
            else -> section.copy(to = maxOf(pointTime, section.from))
        }
        parts = parts.copy(sections = parts.sections.map { if (it.id == section.id) movedSection else it })
        return true
    }
    fun applyEdgeIndex(key: String, requestedIndex: Int, finger: HiddenLockHandleMath.ScreenPoint? = null) {
        val current = HiddenLockHandleMath.handleIndices(trip.trackPoints, prefix, parts, defaultEdgeMetres)
        val start = current.firstOrNull { it.key == "start" } ?: return
        val end = current.firstOrNull { it.key == "end" } ?: return
        if (key == "start") {
            val upper = HiddenLockHandleMath.allowedStartMaximum(prefix, end.index)
            var index = requestedIndex.coerceIn(0, upper)
            val departure = stops.departureIndex
            val parkingIndex = departure?.let { (it + 1).coerceIn(trip.trackPoints.indices) }
            val entersParking = departure != null && index <= parkingIndex!!
            val ghost = if (departure == null) index <= 1 else entersParking && finger != null &&
                pointsOutwardFromStop(finger, departure, departure + 1, trip.trackPoints.lastIndex)
            if (ghost) {
                parts = parts.copy(startMetres = 0)
                return
            }
            if (parkingIndex != null && index <= parkingIndex && parkingIndex <= upper) {
                val value = HiddenLockHandleMath.startMetresForParking(prefix, departure)
                parts = parts.copy(startMetres = value)
                updateDetent("start", 0.0, "parking")
                return
            }
            val value = HiddenLockHandleMath.startMetresForBoundary(prefix, index)
            val snappedIndex = HiddenLockHandleMath.startBoundaryIndex(prefix, value)
            if (prefix[end.index] - prefix[snappedIndex] < HiddenLockHandleMath.minimumVisibleGapMetres) {
                index = upper
                parts = parts.copy(startMetres = HiddenLockHandleMath.startMetresForBoundary(prefix, index))
            } else parts = parts.copy(startMetres = value)
            val distance = HiddenLockHandleMath.displayedStartMetres(prefix, snappedIndex, stops.departureIndex)
            updateDetent("start", distance)
        } else {
            val lower = HiddenLockHandleMath.allowedEndMinimum(prefix, start.index)
            var index = requestedIndex.coerceIn(lower, trip.trackPoints.lastIndex)
            val arrival = stops.arrivalIndex
            val parkingIndex = arrival
            val entersParking = arrival != null && index >= arrival
            val ghost = if (arrival == null) index >= trip.trackPoints.lastIndex - 1 else entersParking && finger != null &&
                pointsOutwardFromStop(finger, arrival, 0, arrival - 1)
            if (ghost) {
                parts = parts.copy(endMetres = 0)
                return
            }
            if (parkingIndex != null && index >= parkingIndex && parkingIndex >= lower) {
                parts = parts.copy(endMetres = HiddenLockHandleMath.endMetresForParking(prefix, arrival))
                updateDetent("end", 0.0, "parking")
                return
            }
            val value = HiddenLockHandleMath.endMetresForBoundary(prefix, index)
            val snappedIndex = HiddenLockHandleMath.endBoundaryIndex(prefix, value)
            if (prefix[snappedIndex] - prefix[start.index] < HiddenLockHandleMath.minimumVisibleGapMetres) {
                index = lower
                parts = parts.copy(endMetres = HiddenLockHandleMath.endMetresForBoundary(prefix, index))
            } else parts = parts.copy(endMetres = value)
            val distance = HiddenLockHandleMath.displayedEndMetres(prefix, snappedIndex, stops.arrivalIndex)
            updateDetent("end", distance)
        }
    }
    fun createSectionAt(centreIndex: Int, beginDrag: Boolean): String? {
        val run = result.visibleRuns.firstOrNull { centreIndex in it } ?: run {
            haptics.error()
            return null
        }
        val ranges = parts.sections.mapNotNull { HiddenLockHandleMath.sectionIndices(trip.trackPoints, it) }
        val range = HiddenLockHandleMath.createSection(prefix, centreIndex, run.first, run.last, ranges)
        if (range == null) {
            haptics.error()
            return null
        }
        val section = HiddenSection(UUID.randomUUID(), trip.trackPoints[range.first].timestamp, trip.trackPoints[range.last].timestamp)
        parts = parts.copy(sections = (parts.sections + section).sortedBy { it.from })
        if (beginDrag) return "section-end:${section.id}"
        haptics.selection()
        return null
    }
    fun nearestSectionCentre(): Int? {
        if (result.visibleRuns.isEmpty() || prefix.isEmpty()) return null
        val total = result.visibleRuns.sumOf { prefix[it.last] - prefix[it.first] }
        if (total <= 0.0) return result.visibleRuns.first().first
        var offset = total / 2.0
        result.visibleRuns.forEach { run ->
            val length = prefix[run.last] - prefix[run.first]
            if (offset <= length) {
                val target = prefix[run.first] + offset
                return (run.first..run.last).minByOrNull { abs(prefix[it] - target) }
            }
            offset -= length
        }
        return result.visibleRuns.last().last
    }
    fun moveHandle(key: String, finger: HiddenLockHandleMath.ScreenPoint) {
        if (key == "start" || key == "end") {
            val current = handles.firstOrNull { it.key == key } ?: return
            val opposite = handles.firstOrNull { it.key == if (key == "start") "end" else "start" } ?: return
            val lower = if (key == "start") 0 else HiddenLockHandleMath.allowedEndMinimum(prefix, opposite.index)
            val upper = if (key == "start") HiddenLockHandleMath.allowedStartMaximum(prefix, opposite.index) else trip.trackPoints.lastIndex
            val nearest = HiddenLockHandleMath.nearestPoint(::screenPoint, current.index, lower, upper, finger, trip.trackPoints.size)
            if (nearest == null) return
            val candidate = if (key == "start") {
                HiddenLockHandleMath.detentIndex(prefix, nearest.index, lower, upper, prefix[stops.departureIndex ?: 0], fromEnd = false)
            } else {
                val tailPrefix = prefix.last() - prefix[stops.arrivalIndex ?: prefix.lastIndex]
                HiddenLockHandleMath.detentIndex(prefix, nearest.index, lower, upper, tailPrefix, fromEnd = true)
            }
            applyEdgeIndex(key, candidate, finger)
            bubbleKey = key
        } else {
            val section = parts.sections.firstOrNull { key.substringAfter(':') == it.id.toString() } ?: return
            val range = HiddenLockHandleMath.sectionIndices(trip.trackPoints, section) ?: return
            val bounds = allowedSectionBounds(section, range) ?: return
            val handleIndex = if (key.startsWith("section-start:")) range.first else range.last
            val nearest = HiddenLockHandleMath.nearestPoint(::screenPoint, handleIndex, bounds.first, bounds.last, finger, trip.trackPoints.size) ?: return
            val allowedIndex = (bounds.first..bounds.last).filter { index ->
                !result.hidden.getOrNull(index).orFalse() || index in range
            }.minByOrNull { abs(it - nearest.index) } ?: return
            updateSectionBoundary(key, allowedIndex)
            bubbleKey = key
        }
    }
    fun dragStarted(key: String) {
        draggingKey = key
        bubbleKey = key
        lastDetent = null
        haptics.tap()
    }
    fun dragFinished(key: String) {
        val latestParts = partsState.value
        val section = latestParts.sections.firstOrNull { key.substringAfter(':') == it.id.toString() }
        if (section != null) {
            val removal = HiddenLockHandleMath.sectionRemovalOnRelease(prefix, trip.trackPoints, latestParts.sections, section.id)
            if (removal != null) {
                partsState.value = latestParts.copy(sections = latestParts.sections.filterNot { it.id == section.id })
                toast = ToastItem(
                    ToastType.undo,
                    strings.hiddenSectionRemoved(),
                    strings.undo(),
                    undoAction = {
                        val undoParts = partsState.value
                        partsState.value = undoParts.copy(sections = HiddenLockHandleMath.restoreSection(undoParts.sections, removal))
                        resolvedParts = partsState.value
                    },
                )
            }
        }
        draggingKey = null
        bubbleKey = null
        resolvedParts = partsState.value
    }
    fun accessibilityMove(key: String, forward: Boolean) {
        val direction = if (forward) 1 else -1
        if (key == "start" || key == "end") {
            val current = handles.firstOrNull { it.key == key } ?: return
            val opposite = handles.firstOrNull { it.key == if (key == "start") "end" else "start" } ?: return
            val lower = if (key == "start") 0 else HiddenLockHandleMath.allowedEndMinimum(prefix, opposite.index)
            val upper = if (key == "start") HiddenLockHandleMath.allowedStartMaximum(prefix, opposite.index) else trip.trackPoints.lastIndex
            val stops = HiddenLockHandleMath.parkedStops(trip.trackPoints)
            val displayAt: (Int) -> Double = if (key == "start") {
                { index -> HiddenLockHandleMath.displayedStartMetres(prefix, index, stops.departureIndex) }
            } else {
                { index -> HiddenLockHandleMath.displayedEndMetres(prefix, index, stops.arrivalIndex) }
            }
            val target = (displayAt(current.index) + direction * 100.0).coerceAtLeast(0.0)
            val index = (lower..upper).minByOrNull { abs(displayAt(it) - target) } ?: return
            applyEdgeIndex(key, index)
        } else {
            val section = parts.sections.firstOrNull { key.substringAfter(':') == it.id.toString() } ?: return
            val range = HiddenLockHandleMath.sectionIndices(trip.trackPoints, section) ?: return
            val bounds = allowedSectionBounds(section, range) ?: return
            var index = if (key.startsWith("section-start:")) range.first else range.last
            val minute = trip.trackPoints[index].timestamp.epochSecond / 60
            while (index + direction in bounds && trip.trackPoints[index + direction].timestamp.epochSecond / 60 == minute) index += direction
            if (index + direction in bounds) index += direction
            updateSectionBoundary(key, index)
        }
        haptics.selection()
        resolvedParts = parts
    }
    fun showFocus(handleKey: String) {
        bubbleKey = handleKey
        pulseKey = handleKey
        pulseToken++
        focusBubbleToken++
    }

    LaunchedEffect(focusedHandleKey, mapState.camera, handles) {
        val key = focusedHandleKey ?: return@LaunchedEffect
        if (mapState.camera == null || handles.none { it.key == key }) return@LaunchedEffect
        showFocus(key)
        onFocusConsumed()
    }
    LaunchedEffect(focusBubbleToken) {
        if (focusBubbleToken == 0) return@LaunchedEffect
        val key = bubbleKey ?: return@LaunchedEffect
        delay(2_000)
        if (bubbleKey == key && draggingKey == null) bubbleKey = null
    }

    val firstOpen = remember(trip.id) {
        val preferences = context.getSharedPreferences("hidden-lock-editor", Context.MODE_PRIVATE)
        val first = !preferences.getBoolean("opened", false)
        if (first) preferences.edit().putBoolean("opened", true).apply()
        first
    }
    val nudgeKey = if (firstOpen && !reduceMotion) {
        if (handles.firstOrNull { it.key == "end" }?.ghost == true) "start" else "end"
    } else null

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().background(colors.bg)) {
            val panelLimit = maxHeight * .46f
            Box(Modifier.fillMaxSize()) {
                if (liveMaps) {
                    MapHost(
                        state = mapState,
                        modifier = Modifier.fillMaxSize(),
                        routes = MapRoutes(listOf(routeShape), RouteAppearance.plain.withSelectedRouteStyle().copy(
                            hiddenInkArgb = colors.text.copy(alpha = .55f).toArgb(),
                        )),
                        markers = staticLockMarkers,
                        systemInsets = WindowInsets(0),
                        styleUri = LiveMaps.style(AppTheme.isDark),
                        textureMode = true,
                        gesturesEnabled = draggingKey == null,
                    )
                } else {
                    HiddenRouteFallback(trip.trackPoints, result.visibleRuns, hiddenDrawingRuns, usePublicPreview, panelTopPx) {
                        fallbackProjection = it
                    }
                }
                HiddenLockHandleOverlay(
                    points = trip.trackPoints,
                    hidden = result.hidden,
                    handles = if (usePublicPreview) emptyList() else handles,
                    mapState = mapState,
                    bottomLimitPx = panelTopPx,
                    draggingKey = draggingKey,
                    bubbleKey = bubbleKey,
                    bubbleText = handleValues,
                    labels = handleLabels,
                    forwardLabel = strings.hiddenHandleMoveForward(),
                    backLabel = strings.hiddenHandleMoveBack(),
                    pulseKey = pulseKey,
                    pulseToken = pulseToken,
                    nudgeKey = nudgeKey,
                    reduceMotion = reduceMotion,
                    onDragStart = ::dragStarted,
                    onDrag = ::moveHandle,
                    onDragEnd = ::dragFinished,
                    onLongPress = if (usePublicPreview) null else { index -> createSectionAt(index, beginDrag = true) },
                    onAccessibilityMove = ::accessibilityMove,
                    projectedPoints = if (liveMaps) null else fallbackProjection,
                )
                Column(
                    Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .heightIn(max = panelLimit)
                        .navigationBarsPadding()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(colors.card)
                        .onGloballyPositioned { coordinates ->
                            panelTopPx = coordinates.positionInRoot().y
                            panelHeightDp = with(density) { coordinates.size.height.toDp().value }
                        }
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .testTag("hidden_editor_sheet"),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 2.dp)
                        .size(width = 36.dp, height = 5.dp).clip(CircleShape).background(colors.border))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(strings.tr(R.string.hiddenEditorTitle), Modifier.weight(1f), style = inter(19f, InterWeight.bold), color = colors.text)
                        Text(
                            strings.done(),
                            Modifier.clickable(role = Role.Button) {
                                onSave(parts.takeIf { it != TripHiddenParts() })
                                onDismissRequest()
                            }.testTag("hidden_editor_done"),
                            style = inter(15f, InterWeight.bold),
                            color = AppTheme.accent,
                        )
                    }
                    Text(strings.hiddenDragHint(), Modifier.testTag("hidden_drag_hint"), style = inter(13f), color = colors.textSecondary)
                    Text(summary, Modifier.testTag("hidden_summary"), style = inter(13f, InterWeight.medium), color = colors.text)
                    HiddenTripBar(trip, result.hidden, parts.sections, strings)
                    if (parts.sections.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            val clock = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(strings.language.locale)
                            parts.sections.forEach { section ->
                                val range = HiddenLockHandleMath.sectionIndices(trip.trackPoints, section)
                                val metres = range?.let { HiddenLockHandleMath.sectionDistanceMetres(prefix, it) } ?: 0.0
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            strings.hiddenSectionTitle(clock.format(section.from.atZone(strings.zone)), clock.format(section.to.atZone(strings.zone))),
                                            Modifier.testTag("hidden_section_title"),
                                            style = inter(13f, InterWeight.semibold),
                                            color = colors.text,
                                        )
                                        Text(Measure.distance(metres, LocalDistanceUnit.current, strings.language.code, strings), style = inter(11.5f), color = colors.textSecondary)
                                    }
                                    Text("×", Modifier.clickable(role = Role.Button) {
                                        parts = parts.copy(sections = parts.sections.filterNot { it.id == section.id })
                                    }.semantics { contentDescription = strings.delete() }
                                        .padding(horizontal = 10.dp, vertical = 6.dp), style = inter(20f), color = colors.textSecondary)
                                }
                            }
                        }
                    }
                    Text(
                        strings.hiddenAddSection(),
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.cardAlt)
                            .clickable(role = Role.Button) { nearestSectionCentre()?.let { createSectionAt(it, beginDrag = false) } }
                            .padding(horizontal = 14.dp, vertical = 12.dp).testTag("hidden_add_section"),
                        style = inter(14f, InterWeight.medium),
                        color = colors.text,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(strings.tr(R.string.hiddenNumbersTitle), Modifier.weight(1f), style = inter(14f, InterWeight.medium), color = colors.text)
                        listOf(false to strings.hiddenAllTrip(), true to strings.hiddenWithoutHidden()).forEach { (selected, label) ->
                            val active = parts.numbersVisibleOnly == selected
                            Text(label, Modifier.clip(CircleShape).background(if (active) AppTheme.accent else colors.cardAlt)
                                .clickable(role = Role.Button) { parts = parts.copy(numbersVisibleOnly = selected) }
                                .padding(horizontal = 10.dp, vertical = 8.dp).testTag("hidden_numbers_$selected"),
                                style = inter(12f, if (active) InterWeight.bold else InterWeight.medium),
                                color = if (active) Color.White else colors.text,
                            )
                        }
                    }
                    val othersMetres = if (parts.numbersVisibleOnly) result.visibleDistance else trip.distance
                    val othersSeconds = if (!parts.numbersVisibleOnly) {
                        ((trip.endDate ?: trip.startDate).toEpochMilli() - trip.startDate.toEpochMilli()) / 1000.0
                    } else {
                        result.visibleStart?.let { from -> result.visibleEnd?.let { to -> (to.toEpochMilli() - from.toEpochMilli()) / 1000.0 } } ?: 0.0
                    }
                    Text(
                        strings.hiddenNumbersPreview(Measure.distance(othersMetres, LocalDistanceUnit.current, strings.language.code, strings), durationText(othersSeconds, strings)),
                        Modifier.testTag("hidden_numbers_preview"),
                        style = inter(12f),
                        color = colors.textSecondary,
                    )
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.cardAlt).padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(strings.hiddenLocalCardTitle(), style = inter(13f, InterWeight.bold), color = colors.text)
                        Text(hiddenPartsLocalCopy(strings, result.hiddenRanges.size, hiddenPhotoCount), style = inter(12f), color = colors.textSecondary)
                    }
                    Row(Modifier.fillMaxWidth().toggleable(usePublicPreview, role = Role.Switch, onValueChange = { usePublicPreview = it }), verticalAlignment = Alignment.CenterVertically) {
                        Text(strings.hiddenShowPublic(), Modifier.weight(1f), style = inter(14f), color = colors.text)
                        HouseSwitch(usePublicPreview, { usePublicPreview = it })
                    }
                    val title = trip.title?.takeIf { it.isNotBlank() }
                    val startHidden = handles.firstOrNull { it.key == "start" }?.ghost == false
                    val endHidden = handles.firstOrNull { it.key == "end" }?.ghost == false
                    val titleNamesStart = !trip.titleIsCustom && title != null && startHidden &&
                        !startLocality.isNullOrBlank() && title.contains(startLocality!!, ignoreCase = true)
                    val titleNamesFinish = !trip.titleIsCustom && title != null && endHidden &&
                        !endLocality.isNullOrBlank() && title.contains(endLocality!!, ignoreCase = true)
                    if (titleNamesStart || titleNamesFinish) {
                        val hint = if (titleNamesStart) strings.hiddenTitleHintStart(title) else strings.hiddenTitleHint(title)
                        Text(hint, Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onRename)
                            .testTag(if (titleNamesStart) "hidden_title_warning_start" else "hidden_title_warning_finish"),
                            style = inter(12.5f), color = AppTheme.accent)
                    }
                    if (trip.isOnServer && !trip.isPrivate) {
                        Text(strings.tr(R.string.hiddenAlreadySeen), style = inter(12.5f), color = colors.textSecondary)
                    }
                }
                ToastHost(toast, { toast = null }, Modifier.align(Alignment.TopCenter))
            }
        }
    }

}

@Composable
private fun HiddenRouteFallback(
    points: List<TrackPoint>,
    visibleRuns: List<IntRange>,
    hiddenRuns: List<IntRange>,
    publicOnly: Boolean,
    bottomLimitPx: Float,
    onProjection: (Map<Int, HiddenLockHandleMath.ScreenPoint>) -> Unit,
) {
    val colors = AppTheme.colors
    var size by remember(points) { mutableStateOf(IntSize.Zero) }
    val projection = remember(points, visibleRuns, publicOnly, size, bottomLimitPx) {
        val fitIndices = if (publicOnly) visibleRuns.flatMap { it.toList() } else points.indices.toList()
        if (fitIndices.isEmpty() || size == IntSize.Zero) emptyMap()
        else {
        val mapHeight = bottomLimitPx.coerceIn(0f, size.height.toFloat())
        val coordinates = fitIndices.map { index -> GeoPoint(points[index].latitude, points[index].longitude) }
        val minLat = coordinates.minOf { it.latitude }
        val maxLat = coordinates.maxOf { it.latitude }
        val minLon = coordinates.minOf { it.longitude }
        val maxLon = coordinates.maxOf { it.longitude }
        val scale = minOf((mapHeight * .62f) / (maxLat - minLat).toFloat().coerceAtLeast(.00001f),
            (size.width * .76f) / (maxLon - minLon).toFloat().coerceAtLeast(.00001f))
        val centreLat = (minLat + maxLat) / 2.0
        val centreLon = (minLon + maxLon) / 2.0
        points.mapIndexed { index, point ->
            val coordinate = GeoPoint(point.latitude, point.longitude)
            index to HiddenLockHandleMath.ScreenPoint(
                (size.width / 2 + ((coordinate.longitude - centreLon) * scale).toFloat()).toDouble(),
                (mapHeight * .40f - ((coordinate.latitude - centreLat) * scale).toFloat()).toDouble(),
            )
        }.toMap()
        }
    }
    LaunchedEffect(projection) { onProjection(projection) }
    Canvas(Modifier.fillMaxSize().background(colors.bg).onSizeChanged { size = it }) {
        if (points.size < 2 || projection.isEmpty()) return@Canvas
        visibleRuns.forEach { range ->
            val path = androidx.compose.ui.graphics.Path()
            range.forEachIndexed { rangeIndex, index ->
                val projected = projection[index] ?: return@forEachIndexed
                val point = Offset(projected.x.toFloat(), projected.y.toFloat())
                if (rangeIndex == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(path, Color.White, style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, AppTheme.accent, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
        }
        if (!publicOnly) {
            hiddenRuns.forEach { range ->
                range.forEach { index ->
                    projection[index]?.let { point ->
                        drawCircle(colors.text.copy(alpha = .55f), 2.dp.toPx(), Offset(point.x.toFloat(), point.y.toFloat()))
                    }
                }
            }
        }
    }
}

private fun Boolean?.orFalse(): Boolean = this ?: false
