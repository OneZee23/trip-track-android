// iOS: TripTrack/Views/ManualTrip/ManualTripEntry.swift, ManualTripSheet.swift,
// ManualTripModel.swift, ManualTripRouter.swift, ManualTripMapView.swift
package com.onezee.triptrack.feature.me

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.AppGraph
import com.onezee.triptrack.designsystem.R
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.logic.ManualTripRules
import com.onezee.triptrack.logic.ManualTripPreset
import com.onezee.triptrack.logic.ManualTripSuggestionPoint
import com.onezee.triptrack.logic.ManualTripSuggestions
import com.onezee.triptrack.logic.ManualTripFrequentPlace
import com.onezee.triptrack.logic.PlusGate
import com.onezee.triptrack.logic.distance
import com.onezee.triptrack.logic.distanceFrom
import com.onezee.triptrack.logic.previewCoordinates
import com.onezee.triptrack.logic.GeohashEncoder
import com.onezee.triptrack.logic.totalDistance
import com.onezee.triptrack.map.MapHost
import com.onezee.triptrack.map.MapHostState
import com.onezee.triptrack.map.MapTap
import com.onezee.triptrack.map.route.MapRoutes
import com.onezee.triptrack.map.route.RouteAppearance
import com.onezee.triptrack.map.route.RouteGeometry
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.ManualTripPlaceDTO
import com.onezee.triptrack.model.PlusAccessLevel
import com.onezee.triptrack.model.PlusFeature
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.model.TripDistanceGate
import com.onezee.triptrack.model.Vehicle
import com.onezee.triptrack.network.ApiError
import com.onezee.triptrack.network.NetworkFailure
import com.onezee.triptrack.ui.LiveMaps
import com.onezee.triptrack.ui.DetailStatCard
import com.onezee.triptrack.ui.StatSegment
import com.onezee.triptrack.ui.cards.dayMonth
import com.onezee.triptrack.feature.journey.ui.HouseDatePickerSheet
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class ManualPlace(val name: String, val point: GeoPoint, val locality: String? = null)
internal data class ViaPlace(val id: Int, val place: ManualPlace?)
internal data class ManualTripUiSeed(
    val stage: Boolean = false,
    val activeFrom: Boolean = true,
    val query: String = "",
    val results: List<ManualTripPlaceDTO> = emptyList(),
    val from: ManualPlace? = null,
    val to: ManualPlace? = null,
    val via: List<ViaPlace> = emptyList(),
    val route: List<GeoPoint> = emptyList(),
    val error: String? = null,
    val suggestions: List<ManualPlace> = emptyList(),
    val title: String = "",
    val start: Instant = Instant.parse("2026-11-06T14:42:00Z"),
    val routeLengthMetres: Double = 0.0,
    val suggestedDurationSeconds: Double = 0.0,
)
private const val MANUAL_TRIP_MAX_VIA = 3

internal object ManualTripEntry {
    fun level(capabilities: com.onezee.triptrack.model.ManualTripCapabilities?, isPlus: Boolean): PlusAccessLevel {
        if (capabilities?.search != true || capabilities.routing != true) return PlusAccessLevel.hidden
        return PlusGate.allows(PlusFeature.manualTrip, isPlus, storefrontHidesPlus = false)
    }
}

@Composable
internal fun ManualTripSheet(graph: AppGraph, onDismiss: () -> Unit, onCreated: (com.onezee.triptrack.model.Trip) -> Unit,
                             initialDay: LocalDate? = null, initialReturnTrip: com.onezee.triptrack.model.Trip? = null,
                             onLocked: () -> Unit = {}, seed: ManualTripUiSeed? = null) {
    val strings = appStrings
    val scope = rememberCoroutineScope()
    val capabilities by graph.manualTripCapabilities.collectAsState()
    val settings by graph.settingsManager.state.collectAsState()
    var stage by remember(initialReturnTrip, seed) { mutableStateOf(seed?.stage ?: (initialReturnTrip != null)) }
    val ends = remember(initialReturnTrip) { initialReturnTrip?.title?.split(" → ")?.let { if (it.size == 2) it.reversed() else listOf(it.firstOrNull().orEmpty(), it.firstOrNull().orEmpty()) } }
    var from by remember(initialReturnTrip, seed) { mutableStateOf(seed?.from ?: initialReturnTrip?.trackPoints?.lastOrNull()?.let { ManualPlace(ends?.getOrNull(0).orEmpty(), GeoPoint(it.latitude, it.longitude)) }) }
    var to by remember(initialReturnTrip, seed) { mutableStateOf(seed?.to ?: initialReturnTrip?.trackPoints?.firstOrNull()?.let { ManualPlace(ends?.getOrNull(1).orEmpty(), GeoPoint(it.latitude, it.longitude)) }) }
    var fromQuery by remember(initialReturnTrip, seed) { mutableStateOf(if (seed?.activeFrom != false) seed?.query ?: from?.name.orEmpty() else from?.name.orEmpty()) }
    var toQuery by remember(initialReturnTrip, seed) { mutableStateOf(if (seed?.activeFrom == false) seed.query else to?.name.orEmpty()) }
    var viaQuery by remember { mutableStateOf("") }
    var activeFrom by remember(seed) { mutableStateOf(seed?.activeFrom ?: true) }
    var activeViaId by remember { mutableStateOf<Int?>(null) }
    var results by remember(seed) { mutableStateOf(seed?.results.orEmpty()) }
    var localSuggestions by remember(seed) { mutableStateOf(seed?.suggestions.orEmpty()) }
    var route by remember(seed) { mutableStateOf(seed?.route.orEmpty()) }
    var via by remember(seed) { mutableStateOf(seed?.via.orEmpty()) }
    var routeDuration by remember(seed) { mutableDoubleStateOf(seed?.suggestedDurationSeconds ?: 0.0) }
    var routeLength by remember(seed) { mutableDoubleStateOf(seed?.routeLengthMetres ?: 0.0) }
    var routeBusy by remember { mutableStateOf(false) }
    var routeRetry by remember { mutableIntStateOf(0) }
    var searchError by remember(seed) { mutableStateOf(seed?.error) }
    var date by remember(initialDay, initialReturnTrip, seed) { mutableStateOf(seed?.start?.atZone(strings.zone)?.toLocalDate() ?: initialReturnTrip?.startDate?.atZone(strings.zone)?.toLocalDate() ?: initialDay ?: LocalDate.now(strings.zone)) }
    var start by remember(initialDay, initialReturnTrip) {
        mutableStateOf(seed?.start ?: initialReturnTrip?.endDate?.plusSeconds(3600)?.coerceAtMost(Instant.now())
            ?: initialDay?.let { ManualTripPreset.forDay(it, Instant.now(), strings.zone).startDate }
            ?: Instant.now().minusSeconds(3600))
    }
    var startTouched by remember(initialDay, initialReturnTrip) { mutableStateOf(initialDay != null || initialReturnTrip != null) }
    var duration by remember { mutableDoubleStateOf(3600.0) }
    var title by remember(seed) { mutableStateOf(seed?.title.orEmpty()) }
    var titleEditing by remember { mutableStateOf(false) }
    val titleFocusRequester = remember { FocusRequester() }
    val displayTitle = title.ifBlank {
        ManualTripRules.title(from?.locality ?: from?.name, to?.locality ?: to?.name).orEmpty()
    }
    var vehicleOptions by remember { mutableStateOf<List<Vehicle>>(emptyList()) }
    var vehicleId by remember(initialReturnTrip) { mutableStateOf(initialReturnTrip?.vehicleId ?: settings.selectedVehicleId) }
    var showVehicles by remember { mutableStateOf(false) }
    var showStart by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var durationTouched by remember { mutableStateOf(false) }
    var pickingOnMap by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val mapState = remember { MapHostState() }
    val mapStyle = LiveMaps.style(AppTheme.isDark)
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val query = when { activeViaId != null -> viaQuery; activeFrom -> fromQuery; else -> toQuery }
    val routeDistance = remember(route, start, duration) {
        val stamps = ManualTripRules.timestamps(route.size, start, duration)
        TripDistanceGate.totalDistance(route.zip(stamps).map { (point, stamp) ->
            TripDistanceGate.Sample(point.latitude, point.longitude, stamp)
        })
    }
    val minimumDuration = remember(routeLength) { ManualTripRules.minimumDuration(routeLength) }
    val routeTooLong = route.isNotEmpty() && minimumDuration > ManualTripRules.MAXIMUM_DURATION_SECONDS
    LaunchedEffect(titleEditing) { if (titleEditing) titleFocusRequester.requestFocus() }
    LaunchedEffect(Unit) {
        if (seed != null) return@LaunchedEffect
        vehicleOptions = runCatching { graph.vehicles.vehicles() }.getOrDefault(emptyList()).filter { it.soldAt == null }
        vehicleId = settings.selectedVehicleId?.takeIf { id -> vehicleOptions.any { it.id == id } }
        val home = settings.home.coordinate?.let { point ->
            val cached = runCatching { graph.trips.cachedGeocode(GeohashEncoder.encode(point.latitude, point.longitude, 5)) }.getOrNull()
            ManualTripSuggestionPoint(strings.tr(R.string.manualTripChipHome), point, cached?.locality ?: cached?.region)
        }
        runCatching { graph.placeManager.reload() }
        val frequent = ManualTripSuggestions.topFrequentPlaces(
            graph.placeManager.places.value.map { place ->
                ManualTripFrequentPlace(
                    value = place,
                    passCount = runCatching { graph.placeManager.passCount(place.id) }.getOrDefault(0),
                    createdAt = place.createdAt,
                )
            },
        ).mapNotNull { place ->
                val point = GeoPoint(place.latitude, place.longitude)
                val cached = runCatching { graph.trips.cachedGeocode(GeohashEncoder.encode(point.latitude, point.longitude, 5)) }.getOrNull()
                val name = place.name?.takeIf(String::isNotBlank) ?: cached?.locality ?: cached?.region
                name?.takeIf(String::isNotBlank)?.let { ManualTripSuggestionPoint(it, point, cached?.locality ?: cached?.region) }
        }
        val recent = runCatching { graph.trips.fetchTrips(8, 0) }.getOrDefault(emptyList())
        val recentEnds = recent.flatMap { trip ->
            val ends = trip.previewCoordinates
            listOfNotNull(ends.firstOrNull(), ends.lastOrNull()).mapNotNull { point ->
                val cached = runCatching { graph.trips.cachedGeocode(GeohashEncoder.encode(point.latitude, point.longitude, 5)) }.getOrNull()
                val name = cached?.locality ?: cached?.region
                name?.takeIf(String::isNotBlank)?.let { ManualTripSuggestionPoint(it, point, cached?.locality ?: cached?.region) }
            }
        }
        localSuggestions = ManualTripSuggestions.build(home, frequent, recentEnds)
            .map { candidate ->
                val cached = runCatching { graph.trips.cachedGeocode(GeohashEncoder.encode(candidate.point.latitude, candidate.point.longitude, 5)) }.getOrNull()
                ManualPlace(candidate.name, candidate.point, candidate.locality ?: cached?.locality ?: cached?.region)
            }
    }
    LaunchedEffect(query, activeFrom, activeViaId, capabilities) {
        if (seed != null) return@LaunchedEffect
        searchJob?.cancel()
        results = emptyList()
        searchError = null
        if (query.length < 3 || capabilities?.search != true) return@LaunchedEffect
        searchJob = launch {
            delay(400)
            try {
                val lang = strings.language.code.takeIf { it in com.onezee.triptrack.network.ManualTripsApi.LANGUAGES } ?: "en"
                results = graph.manualTripsApi.search(query, lang).places
            } catch (error: ApiError.UnknownServer) {
                if (error.code == "PRO_REQUIRED") onLocked() else searchError = "failed"
            } catch (error: ApiError.Network) {
                searchError = if (error.failure == NetworkFailure.offline) "offline" else "failed"
            } catch (_: Exception) { searchError = "failed" }
        }
    }
    LaunchedEffect(from, to, via, capabilities, routeRetry) {
        if (seed != null) return@LaunchedEffect
        if (from == null || to == null || capabilities?.routing != true) {
            route = emptyList()
            routeLength = 0.0
            routeDuration = 0.0
            routeBusy = false
            return@LaunchedEffect
        }
        if (via.count { it.place != null } > MANUAL_TRIP_MAX_VIA) { searchError = "tooManyStops"; return@LaunchedEffect }
        val waypoints = listOfNotNull(from) + via.mapNotNull { it.place } + listOfNotNull(to)
        routeBusy = true
        searchError = null
        try {
            val response = graph.manualTripsApi.route(waypoints.map { it.point })
            route = response.coordinates.mapNotNull { coordinate ->
                if (coordinate.size < 2) null else GeoPoint(coordinate[0], coordinate[1]).takeIf {
                    it.latitude.isFinite() && it.longitude.isFinite() && it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0
                }
            }
            routeDuration = response.durationSeconds
            routeLength = response.distance.coerceAtLeast(0.0)
            if (route.size < 2) searchError = "noRoute"
            val suggested = ManualTripRules.duration(duration, response.durationSeconds, durationTouched, ManualTripRules.DURATION_STEP_SECONDS)
            duration = maxOf(ManualTripRules.minimumDuration(response.distance), suggested)
                .coerceAtMost(ManualTripRules.MAXIMUM_DURATION_SECONDS)
            if (!startTouched) start = ManualTripRules.untouchedStart(start, duration, Instant.now()).also { date = it.atZone(strings.zone).toLocalDate() }
        } catch (error: ApiError.UnknownServer) {
            route = emptyList()
            routeLength = 0.0
            routeDuration = 0.0
            searchError = when (error.code) {
                "ROUTE_NOT_FOUND" -> "noRoute"
                "TOO_MANY_STOPS" -> "tooManyStops"
                "PRO_REQUIRED" -> { onLocked(); "locked" }
                else -> "failed"
            }
        } catch (error: ApiError.Network) {
            route = emptyList(); routeLength = 0.0; routeDuration = 0.0
            searchError = if (error.failure == NetworkFailure.offline) "offline" else "failed"
        } catch (_: Exception) {
            route = emptyList(); routeLength = 0.0; routeDuration = 0.0
            searchError = "failed"
        }
        finally { routeBusy = false }
    }

    ContentSizedSheet(onDismiss, background = AppTheme.colors.bg, detent = SheetDetent.large, dismissible = false) {
        val sheet = this
        Column(Modifier.fillMaxWidth().imePadding().padding(bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clickable(enabled = stage) { stage = false }, verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (stage) Glyph(AppGlyph.chevronLeft, null, tint = AppTheme.colors.textSecondary, size = symbolBox(14f))
                    Text(strings.tr(if (stage) R.string.tripTitle else R.string.manualTripEntry), style = inter(20f, InterWeight.bold), color = AppTheme.colors.text)
                }
                Box(Modifier.size(44.dp).clickable { sheet.dismiss() }.testTag("manual_trip_close"), contentAlignment = Alignment.Center) {
                    Glyph(AppGlyph.xmark, null, tint = AppTheme.colors.textSecondary, size = symbolBox(16f))
                }
            }
            if (!stage) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (activeFrom && activeViaId == null) ManualPointField(fromQuery, { fromQuery = it }, strings.tr(R.string.manualTripFrom), "manual_trip_from", true, Modifier.weight(1f))
                        else Text(from?.name ?: strings.tr(R.string.manualTripFrom), Modifier.weight(1f).clickable { activeFrom = true; activeViaId = null },
                            style = inter(17f, InterWeight.bold), color = if (activeFrom) AppTheme.colors.text else AppTheme.colors.textSecondary, maxLines = 1)
                        Text("→", style = inter(16f, InterWeight.semibold), color = AppTheme.colors.textTertiary)
                        if (!activeFrom && activeViaId == null) ManualPointField(toQuery, { toQuery = it }, strings.tr(R.string.manualTripTo), "manual_trip_to", true, Modifier.weight(1f))
                        else Text(to?.name ?: strings.tr(R.string.manualTripTo), Modifier.weight(1f).clickable { activeFrom = false; activeViaId = null },
                            style = inter(17f, InterWeight.bold), color = if (!activeFrom && activeViaId == null) AppTheme.colors.text else AppTheme.colors.textSecondary, maxLines = 1)
                    }
                    via.forEachIndexed { index, stop ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(strings.tr(R.string.manual_trip_via), style = inter(13f, InterWeight.semibold), color = AppTheme.colors.textSecondary)
                            Spacer(Modifier.width(6.dp))
                            if (activeViaId == stop.id) ManualPointField(viaQuery, { viaQuery = it }, stop.place?.name ?: strings.tr(R.string.manualTripSearchHint), "manual_trip_via_${stop.id}", true, Modifier.weight(1f), small = true)
                            else Text(stop.place?.name ?: strings.tr(R.string.manualTripSearchHint), Modifier.weight(1f).clickable { activeViaId = stop.id; activeFrom = false },
                                style = inter(15f, InterWeight.semibold), color = AppTheme.colors.text)
                            Text("↑", Modifier.clickable { via = via.toMutableList().also { list -> if (index > 0) java.util.Collections.swap(list, index, index - 1) } }, color = AppTheme.colors.textSecondary)
                            Text("↓", Modifier.clickable { via = via.toMutableList().also { list -> if (index < list.lastIndex) java.util.Collections.swap(list, index, index + 1) } }, color = AppTheme.colors.textSecondary)
                            Text("×", Modifier.clickable { via = via.filterNot { it.id == stop.id } }, color = AppTheme.colors.textSecondary)
                        }
                    }
                    if (query.isBlank()) LazyColumn(Modifier.heightIn(max = 164.dp)) {
                        items(localSuggestions) { item ->
                            Text(item.name, Modifier.fillMaxWidth().clickable {
                                if (activeViaId != null) { via = via.map { if (it.id == activeViaId) it.copy(place = item) else it }; stage = true }
                                else if (activeFrom) { from = item; fromQuery = item.name; activeFrom = false; if (to != null) stage = true }
                                else { to = item; toQuery = item.name; if (from != null) stage = true }
                                activeViaId = null
                            }.padding(vertical = 10.dp), style = inter(14f), color = AppTheme.colors.textSecondary)
                        }
                    }
                    if (routeBusy) Text(strings.tr(R.string.manualTripRouting), style = inter(13f), color = AppTheme.colors.textSecondary)
                    searchError?.let { ManualTripError(it, strings) }
                    LazyColumn(Modifier.heightIn(max = 240.dp)) {
                        items(results) { item ->
                            Row(Modifier.fillMaxWidth().clickable {
                                val selected = ManualPlace(item.name, GeoPoint(item.latitude, item.longitude))
                                if (activeViaId != null) { via = via.map { if (it.id == activeViaId) it.copy(place = selected) else it }; stage = true }
                                else if (activeFrom) { from = selected; fromQuery = item.name; activeFrom = false; if (to != null) stage = true }
                                else { to = selected; toQuery = item.name; stage = true }
                                activeViaId = null
                                results = emptyList()
                            }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(item.name, style = inter(15f), color = AppTheme.colors.text)
                            }
                        }
                    }
                    Text(strings.tr(R.string.manualTripPointOnMap), Modifier.fillMaxWidth().clickable { pickingOnMap = !pickingOnMap }
                        .padding(vertical = 10.dp), style = inter(14f, InterWeight.semibold), color = AppTheme.accent)
                    if (pickingOnMap) {
                        Box(Modifier.fillMaxWidth().height(240.dp).background(AppTheme.colors.cardAlt, RoundedCornerShape(14.dp))) {
                            if (LocalInspectionMode.current) ManualTripMapPreview(emptyList()) else MapHost(mapState, Modifier.fillMaxSize(), systemInsets = WindowInsets(0),
                                styleUri = mapStyle, onMapTap = { tap: MapTap ->
                                    scope.launch {
                                        val name = graph.location.namer.nameForPoint(tap.coordinate).orEmpty()
                                        val point = ManualPlace(name, tap.coordinate, name.takeIf(String::isNotBlank))
                                        if (activeViaId != null) { via = via.map { if (it.id == activeViaId) it.copy(place = point) else it }; stage = true }
                                        else if (activeFrom) { from = point; fromQuery = point.name; activeFrom = false; if (to != null) stage = true }
                                        else { to = point; toQuery = point.name; stage = true }
                                        activeViaId = null
                                        pickingOnMap = false
                                    }
                                })
                        }
                        Text(strings.tr(R.string.manualTripTapMapHint), style = inter(12f), color = AppTheme.colors.textTertiary)
                    }
                    Text(strings.tr(R.string.manual_trip_attribution), style = inter(11f), color = AppTheme.colors.textTertiary)
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.fillMaxWidth().height(220.dp).background(AppTheme.colors.cardAlt, RoundedCornerShape(14.dp))) {
                        if (LocalInspectionMode.current) ManualTripMapPreview(route) else MapHost(mapState, Modifier.fillMaxSize(), routes = MapRoutes(listOf(RouteGeometry.build(route, preview = true)), RouteAppearance.plain),
                            systemInsets = WindowInsets(0),
                            styleUri = mapStyle)
                    }
                    Text(strings.tr(R.string.manual_trip_attribution), style = inter(11f), color = AppTheme.colors.textTertiary)
                    Text(dateLine(start, strings), style = pressStart2P(9f), color = AppTheme.colors.textSecondary,
                        maxLines = 1, modifier = Modifier.testTag("manual_trip_date_line"))
                    if (titleEditing) BasicTextField(title, { title = it },
                        Modifier.fillMaxWidth().focusRequester(titleFocusRequester).padding(vertical = 6.dp).testTag("manual_trip_title"),
                        singleLine = true, textStyle = inter(26f, InterWeight.bold).copy(color = AppTheme.colors.text), cursorBrush = SolidColor(AppTheme.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { titleEditing = false }),
                        decorationBox = { field -> Box { if (title.isEmpty()) Text(displayTitle, style = inter(26f, InterWeight.bold), color = AppTheme.colors.text); field() } })
                    else Text(displayTitle, Modifier.fillMaxWidth().clickable { titleEditing = true }.testTag("manual_trip_title"),
                        style = inter(26f, InterWeight.bold), color = AppTheme.colors.text, maxLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(timeRange(start, duration, strings), Modifier.clickable { showStart = !showStart; showVehicles = false }
                            .background(if (showStart) AppTheme.accentBg else AppTheme.colors.card, RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 9.dp)
                            .testTag("manual_trip_time"), style = inter(13f, InterWeight.semibold), color = AppTheme.colors.text)
                        Text(vehicleOptions.firstOrNull { it.id == vehicleId }?.let { "${it.avatarEmoji} ${it.name}" } ?: strings.noVehicle(),
                            Modifier.clickable { showVehicles = !showVehicles; showStart = false }
                                .background(if (showVehicles) AppTheme.accentBg else AppTheme.colors.card, RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 9.dp)
                                .testTag("manual_trip_vehicle"), style = inter(13f, InterWeight.semibold), color = AppTheme.colors.text)
                        if (via.size < MANUAL_TRIP_MAX_VIA) Text("+ ${strings.tr(R.string.manual_trip_via)}",
                            Modifier.clickable { val id = (via.maxOfOrNull { it.id } ?: 0) + 1; via = via + ViaPlace(id, null); activeViaId = id; activeFrom = false; stage = false }
                                .background(AppTheme.colors.card, RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
                            style = inter(13f, InterWeight.semibold), color = AppTheme.accent)
                    }
                    if (showStart) Column(Modifier.fillMaxWidth().surfaceCard(12.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(LocalDate.now(strings.zone) to strings.today(), LocalDate.now(strings.zone).minusDays(1) to strings.yesterday()).forEach { (day, label) ->
                                Text(label, Modifier.clickable { date = day; start = ManualTripRules.defaultStart(day, Instant.now(), strings.zone); startTouched = true }
                                    .background(if (date == day) AppTheme.accentBg else AppTheme.colors.cardAlt, RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = inter(13f, InterWeight.semibold), color = AppTheme.colors.text)
                            }
                            Text(start.atZone(strings.zone).format(DateTimeFormatter.ofPattern("d MMM yyyy", strings.language.locale)), Modifier.weight(1f).clickable { showDatePicker = true }.padding(8.dp),
                                style = inter(13f, InterWeight.semibold), color = AppTheme.colors.textSecondary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("− 15", Modifier.clickable { start = start.minusSeconds(900).coerceAtLeast(Instant.now().minusSeconds(ManualTripRules.MAXIMUM_AGE_SECONDS.toLong())); date = start.atZone(strings.zone).toLocalDate(); startTouched = true }.padding(8.dp), color = AppTheme.colors.text)
                            Text(start.atZone(strings.zone).format(DateTimeFormatter.ofPattern("HH:mm", strings.language.locale)), Modifier.weight(1f), style = inter(15f, InterWeight.semibold), color = AppTheme.colors.text)
                            Text("+ 15", Modifier.clickable { start = start.plusSeconds(900).coerceAtMost(Instant.now()); date = start.atZone(strings.zone).toLocalDate(); startTouched = true }.padding(8.dp), color = AppTheme.colors.text)
                            Text("−", Modifier.clickable { duration = maxOf(minimumDuration.coerceAtMost(ManualTripRules.MAXIMUM_DURATION_SECONDS), duration - 900.0); durationTouched = true }.padding(8.dp), color = AppTheme.colors.text)
                            Text(durationText(duration, strings), style = inter(13f, InterWeight.semibold), color = AppTheme.colors.text)
                            Text("+", Modifier.clickable { duration = minOf(604800.0, duration + 900.0); durationTouched = true }.padding(8.dp), color = AppTheme.colors.text)
                        }
                        if (durationTouched && routeDuration > 0.0) {
                            Text(strings.tr(R.string.manualTripSuggestedTime, durationText(routeDuration, strings)),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    duration = ManualTripRules.duration(duration, routeDuration, false, ManualTripRules.DURATION_STEP_SECONDS)
                                        .coerceAtLeast(minimumDuration).coerceAtMost(ManualTripRules.MAXIMUM_DURATION_SECONDS)
                                    durationTouched = false
                                }.padding(vertical = 8.dp).testTag("manual_trip_suggested"), style = inter(13f, InterWeight.semibold), color = AppTheme.accent,
                            )
                        }
                    }
                    if (showVehicles) Column(Modifier.fillMaxWidth().surfaceCard(12.dp).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(strings.noVehicle(), Modifier.fillMaxWidth().clickable { vehicleId = null; showVehicles = false }.padding(10.dp), style = inter(14f), color = AppTheme.colors.textSecondary)
                        vehicleOptions.forEach { vehicle -> Text("${vehicle.avatarEmoji} ${vehicle.name}", Modifier.fillMaxWidth().clickable { vehicleId = vehicle.id; showVehicles = false }.padding(10.dp), style = inter(14f), color = AppTheme.colors.text) }
                    }
                    if (route.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailStatCard(listOf(StatSegment(Measure.distance(routeDistance, settings.distanceUnit, strings.language.code, strings))),
                            strings.distance(), AppTheme.green, Modifier.weight(1f))
                        DetailStatCard(listOf(StatSegment(durationText(duration, strings))), strings.duration(), AppTheme.colors.text, Modifier.weight(1f))
                    }
                    via.forEachIndexed { index, stop ->
                        Row(Modifier.fillMaxWidth().surfaceCard(10.dp).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${strings.tr(R.string.manual_trip_via)} · ${stop.place?.name ?: strings.tr(R.string.manualTripSearchHint)}",
                                Modifier.weight(1f).clickable { activeViaId = stop.id; activeFrom = false; stage = false }, style = inter(13f), color = AppTheme.colors.text)
                            Text("↑", Modifier.clickable { via = via.toMutableList().also { if (index > 0) java.util.Collections.swap(it, index, index - 1) } }.padding(6.dp), color = AppTheme.colors.textSecondary)
                            Text("↓", Modifier.clickable { via = via.toMutableList().also { if (index < it.lastIndex) java.util.Collections.swap(it, index, index + 1) } }.padding(6.dp), color = AppTheme.colors.textSecondary)
                            Text("×", Modifier.clickable { via = via.filterNot { it.id == stop.id } }.padding(6.dp), color = AppTheme.colors.textSecondary)
                        }
                    }
                    val endsLater = route.size > 1 && !ManualTripRules.hasEnded(start, duration, Instant.now())
                    if (routeBusy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Canvas(Modifier.size(16.dp)) {
                            drawArc(AppTheme.accent, -90f, 280f, false, style = Stroke(2.dp.toPx()))
                        }
                        Text(strings.manualTripRouting(), style = inter(14f), color = AppTheme.colors.textSecondary)
                    } else if (routeTooLong) ManualTripError("tooLong", strings)
                    else if (endsLater) ManualTripError("endsLater", strings)
                    else searchError?.let { ManualTripError(it, strings) }
                    if (searchError == "offline" || searchError == "failed") Text(strings.retry(), Modifier.clickable { routeRetry++ }.padding(vertical = 8.dp).testTag("manual_trip_retry_route"),
                        style = inter(14f, InterWeight.semibold), color = AppTheme.accent)
                    if (route.size > 1) Text(summaryLine(Measure.distance(routeDistance, settings.distanceUnit, strings.language.code, strings), durationText(duration, strings), start, strings), style = inter(13f, InterWeight.semibold), color = AppTheme.colors.textSecondary,
                        modifier = Modifier.testTag("manual_trip_summary"))
                    val canSave = route.size > 1 && !routeBusy && !routeTooLong && !endsLater && !saving
                    Box(Modifier.fillMaxWidth().clickable(enabled = canSave) {
                        val built = ManualTripRules.build(route, start, duration, Instant.now(), vehicleId, title.ifBlank { displayTitle })
                        if (built == null) { searchError = "failed"; return@clickable }
                        saving = true
                        scope.launch {
                            try {
                                if (graph.trips.insertManualTrip(built)) {
                                    graph.placeManager.process(built.id)
                                    graph.revealedLayer.ingest(built.id)
                                    onCreated(built)
                                    sheet.dismiss()
                                }
                            } catch (_: Exception) { searchError = "failed" }
                            finally { saving = false }
                        }
                    }.background(AppTheme.accent.copy(alpha = if (canSave) 1f else 0.4f), RoundedCornerShape(14.dp)).padding(16.dp).testTag("manual_trip_save"), contentAlignment = Alignment.Center) {
                        Text(strings.tr(R.string.manualTripSave), style = inter(15f, InterWeight.bold), color = AppTheme.colors.card)
                    }
                }
            }
        }
        if (showDatePicker) HouseDatePickerSheet(
            selection = start,
            bounds = ManualTripRules.bounds(start, Instant.now(), strings.zone),
            zone = strings.zone,
            onPick = { start = it; date = it.atZone(strings.zone).toLocalDate(); startTouched = true; showDatePicker = false },
            onDismissRequest = { showDatePicker = false },
        )
    }
}

@Composable
private fun ManualPointField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    tag: String,
    requestFocus: Boolean,
    modifier: Modifier,
    small: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(requestFocus) { if (requestFocus) focusRequester.requestFocus() }
    BasicTextField(value, onValue, modifier.focusRequester(focusRequester).testTag(tag), singleLine = true,
        textStyle = inter(if (small) 15f else 17f, InterWeight.bold).copy(color = AppTheme.colors.text), cursorBrush = SolidColor(AppTheme.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        decorationBox = { field -> Box { if (value.isEmpty()) Text(placeholder, style = inter(if (small) 15f else 17f, InterWeight.bold), color = AppTheme.colors.textTertiary, maxLines = 1); field() } })
}

@Composable
private fun ManualTripMapPreview(route: List<GeoPoint>) {
    Box(Modifier.fillMaxSize().background(AppTheme.colors.cardAlt)) {
        if (route.size > 1) Canvas(Modifier.fillMaxSize().padding(32.dp)) {
            val minLat = route.minOf { it.latitude }
            val maxLat = route.maxOf { it.latitude }
            val minLon = route.minOf { it.longitude }
            val maxLon = route.maxOf { it.longitude }
            val latSpan = (maxLat - minLat).takeIf { it > 0 } ?: 1.0
            val lonSpan = (maxLon - minLon).takeIf { it > 0 } ?: 1.0
            val path = Path()
            route.forEachIndexed { index, point ->
                val x = ((point.longitude - minLon) / lonSpan * size.width).toFloat()
                val y = (size.height - (point.latitude - minLat) / latSpan * size.height).toFloat()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, AppTheme.accent, style = Stroke(width = 5.dp.toPx()))
        }
    }
}

@Composable
private fun ManualTripError(code: String, strings: com.onezee.triptrack.designsystem.strings.AppStrings) {
    val resource = when (code) {
        "noRoute" -> R.string.manualTripErrorNoRoute
        "offline" -> R.string.manualTripErrorOffline
        "tooLong" -> R.string.manualTripErrorTooLong
        "endsLater" -> R.string.manualTripErrorEndsLater
        else -> R.string.manualTripErrorFailed
    }
    Text(strings.tr(resource), style = inter(13f), color = AppTheme.colors.textSecondary)
}

private fun durationText(seconds: Double, strings: com.onezee.triptrack.designsystem.strings.AppStrings): String {
    val total = seconds.roundToInt()
    val hours = total / 3600
    val minutes = total % 3600 / 60
    return when {
        hours == 0 -> "$minutes ${strings.tr(R.string.minutesUnitShort)}"
        minutes == 0 -> "$hours ${strings.tr(R.string.hoursUnitShort)}"
        else -> "$hours ${strings.tr(R.string.hoursUnitShort)} $minutes ${strings.tr(R.string.minutesUnitShort)}"
    }
}

private fun dateLine(start: Instant, strings: com.onezee.triptrack.designsystem.strings.AppStrings): String {
    val day = start.atZone(strings.zone).toLocalDate()
    val today = LocalDate.now(strings.zone)
    val label = when (day) {
        today -> strings.today()
        today.minusDays(1) -> strings.yesterday()
        else -> day.format(DateTimeFormatter.ofPattern("d MMM yyyy", strings.language.locale))
    }
    return label.uppercase(strings.language.locale)
}

private fun timeRange(start: Instant, duration: Double, strings: com.onezee.triptrack.designsystem.strings.AppStrings): String {
    val formatter = DateTimeFormatter.ofPattern("HH:mm", strings.language.locale)
    return "${start.atZone(strings.zone).format(formatter)} – ${start.plusMillis((duration * 1000).toLong()).atZone(strings.zone).format(formatter)}"
}

private fun summaryLine(distance: String, duration: String, start: Instant, strings: com.onezee.triptrack.designsystem.strings.AppStrings): String {
    val whenText = "${strings.dayMonth(start)} · ${start.atZone(strings.zone).format(DateTimeFormatter.ofPattern("HH:mm", strings.language.locale))}"
    return ManualTripRules.summaryLine(distance, duration, whenText)
}
