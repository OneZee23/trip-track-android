package com.onezee.triptrack.debug

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onezee.triptrack.designsystem.R
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.theme.TripTrackTheme
import com.onezee.triptrack.logic.RevealBuilder
import com.onezee.triptrack.logic.RevealedLayer
import com.onezee.triptrack.map.LiveReveal
import com.onezee.triptrack.map.MapFog
import com.onezee.triptrack.map.MapHost
import com.onezee.triptrack.map.MapHostState
import com.onezee.triptrack.map.camera.MapCameraCommand
import com.onezee.triptrack.map.camera.MapInsets
import com.onezee.triptrack.map.fog.FogLook
import com.onezee.triptrack.map.fog.FogStats
import com.onezee.triptrack.map.marker.MapMarker
import com.onezee.triptrack.map.route.MapRoutes
import com.onezee.triptrack.map.route.RouteAppearance
import com.onezee.triptrack.map.route.RouteGeometry
import com.onezee.triptrack.model.AtlasMapAppearance
import com.onezee.triptrack.model.GeoBounds
import com.onezee.triptrack.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Debug builds only: the production map, fog, routes and markers over seeded libraries, including the
 * iPhone's stress set (400 trips × 2,000 points). Every option can also come from intent extras, so a
 * script can open a given state:
 *
 * `adb shell am start -n com.onezee.triptrack.dev/com.onezee.triptrack.debug.MapDemoActivity
 *  --es dataset ios-stress --es style night --es preset city --es coverage half`
 */
class MapDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val options = DemoOptions(
            dataset = DemoDataset.entries.firstOrNull { it.key == intent.getStringExtra("dataset") } ?: DemoDataset.DEMO,
            style = AtlasMapAppearance.Style.fromRawValue(intent.getStringExtra("style") ?: "") ?: AtlasMapAppearance.Style.fog,
            preset = Preset.entries.firstOrNull { it.key == intent.getStringExtra("preset") },
            halfCoverage = intent.getStringExtra("coverage") == "half",
            route = intent.getBooleanExtra("route", true),
            reveal = intent.getBooleanExtra("reveal", false),
        )
        setContent {
            TripTrackTheme(
                isDark = options.style == AtlasMapAppearance.Style.night,
                language = AppLanguage.detect(resources.configuration.locales.toLanguageTags().split(',')),
            ) {
                MapDemoScreen(options)
            }
        }
    }
}

data class DemoOptions(
    val dataset: DemoDataset,
    val style: AtlasMapAppearance.Style,
    val preset: Preset?,
    val halfCoverage: Boolean,
    val route: Boolean,
    val reveal: Boolean,
)

/** The camera presets of the fog spike, in MapLibre zoom. */
enum class Preset(val key: String, val zoom: Double, val label: Int) {
    STREET("street", 15.5, R.string.debug_map_street),
    CITY("city", 11.5, R.string.debug_map_city),
    REGION("region", 8.0, R.string.debug_map_region),
    COUNTRY("country", 5.0, R.string.debug_map_country),
}

private class Built(val dataset: DemoDataset, val trips: Int, val layer: RevealedLayer, val bounds: GeoBounds, val revealMs: Double)

private fun build(dataset: DemoDataset): Built {
    val previews = MapDemoSeeds.previews(dataset)
    val started = System.nanoTime()
    val layer = RevealBuilder.layer(previews.asSequence())
    val revealMs = (System.nanoTime() - started) / 1e6
    val bounds = GeoBounds(minLat = 90.0, maxLat = -90.0, minLon = 180.0, maxLon = -180.0)
    for (trip in previews) for (p in trip) {
        bounds.minLat = minOf(bounds.minLat, p.latitude)
        bounds.maxLat = maxOf(bounds.maxLat, p.latitude)
        bounds.minLon = minOf(bounds.minLon, p.longitude)
        bounds.maxLon = maxOf(bounds.maxLon, p.longitude)
    }
    return Built(dataset, previews.size, layer, bounds, revealMs)
}

/** Where the presets look: the middle of the stress library sits on trip 200 (iOS budget test centre). */
private fun focus(dataset: DemoDataset): GeoPoint =
    if (dataset == DemoDataset.IOS_STRESS) GeoPoint(47.03, 40.92) else MapDemoSeeds.krasnodar

@Composable
private fun MapDemoScreen(options: DemoOptions) {
    val mapState = remember { MapHostState() }
    var dataset by remember { mutableStateOf(options.dataset) }
    var style by remember { mutableStateOf(options.style) }
    var halfCoverage by remember { mutableStateOf(options.halfCoverage) }
    var fogOn by remember { mutableStateOf(true) }
    var routeOn by remember { mutableStateOf(options.route) }
    var revealOn by remember { mutableStateOf(options.reveal) }
    var built by remember { mutableStateOf<Built?>(null) }
    var stats by remember { mutableStateOf<FogStats?>(null) }
    var tapped by remember { mutableStateOf<String?>(null) }
    var controlsHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    LaunchedEffect(dataset) {
        built = null
        val ready = withContext(Dispatchers.Default) { build(dataset) }
        built = ready
        val preset = options.preset
        mapState.execute(
            if (preset != null) MapCameraCommand.Move(focus(dataset), preset.zoom, animated = false)
            else MapCameraCommand.Fit(ready.bounds, MapCameraCommand.Padding.overview, animated = false),
        )
    }
    LaunchedEffect(Unit) {
        mapState.setFogDiagnostics(true)
        while (true) {
            stats = mapState.fogStats()
            delay(500)
        }
    }

    val shape = remember {
        val (coordinates, speeds) = MapDemoSeeds.routeWithSpeeds()
        RouteGeometry.build(coordinates, speeds)
    }
    val homeLabel = stringResource(R.string.debug_map_home)
    val workLabel = stringResource(R.string.debug_map_work)
    val markers = remember(homeLabel, workLabel) {
        val route = MapDemoSeeds.routeWithSpeeds().first
        listOf(
            MapMarker("home", MapDemoSeeds.krasnodar, MapMarker.Kind.PLACE, label = homeLabel),
            MapMarker("work", GeoPoint(45.052, 38.997), MapMarker.Kind.PLACE_SUGGESTION, label = workLabel),
            MapMarker("checkpoint-1", route[route.size / 4], MapMarker.Kind.CHECKPOINT),
            MapMarker("checkpoint-2", route[route.size * 3 / 4], MapMarker.Kind.CHECKPOINT),
            MapMarker("photo", route[route.size / 3], MapMarker.Kind.PHOTO, image = samplePhoto()),
        )
    }
    val look = FogLook.of(AtlasMapAppearance(style = style), halfResolutionCoverage = halfCoverage)
    val fog = built?.takeIf { fogOn }?.let { b ->
        MapFog(b.layer, look, liveReveal = if (revealOn) LiveReveal(focus(b.dataset), progress = 1.0) else null)
    }
    val routes = if (routeOn) MapRoutes(listOf(shape), RouteAppearance.overFog(look.palette)) else null

    Box(Modifier.fillMaxSize()) {
        MapHost(
            state = mapState,
            modifier = Modifier.fillMaxSize(),
            fog = fog,
            routes = routes,
            markers = markers,
            onMarkerTap = { tapped = it.id },
            chromeInsets = MapInsets(top = with(density) { controlsHeight.toDp().value.toDouble() }),
        )
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp).onSizeChanged { controlsHeight = it.height },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(stringResource(R.string.debug_map_dataset, dataset.key)) { dataset = dataset.next() }
                Chip(stringResource(R.string.debug_map_style, style.rawValue)) {
                    style = AtlasMapAppearance.Style.entries[(style.ordinal + 1) % AtlasMapAppearance.Style.entries.size]
                }
                Chip(stringResource(if (halfCoverage) R.string.debug_map_coverage_half else R.string.debug_map_coverage_full)) {
                    halfCoverage = !halfCoverage
                }
                Chip(stringResource(if (fogOn) R.string.debug_map_fog_on else R.string.debug_map_fog_off)) { fogOn = !fogOn }
                Chip(stringResource(if (routeOn) R.string.debug_map_route_on else R.string.debug_map_route_off)) { routeOn = !routeOn }
                Chip(stringResource(if (revealOn) R.string.debug_map_reveal_on else R.string.debug_map_reveal_off)) { revealOn = !revealOn }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (preset in Preset.entries) {
                    Chip(stringResource(preset.label)) { mapState.execute(MapCameraCommand.Move(focus(dataset), preset.zoom)) }
                }
                Chip(stringResource(R.string.debug_map_fit_all)) {
                    built?.let { mapState.execute(MapCameraCommand.Fit(it.bounds, MapCameraCommand.Padding.overview)) }
                }
                Chip(stringResource(R.string.debug_map_fit_route)) {
                    shape.bounds?.let { mapState.execute(MapCameraCommand.FitRoute(it)) }
                }
            }
            Status(dataset, built, stats, mapState, tapped)
        }
    }
}

@Composable
private fun Status(dataset: DemoDataset, built: Built?, stats: FogStats?, mapState: MapHostState, tapped: String?) {
    val none = stringResource(R.string.debug_map_none)
    val gpu = stats?.gpuMs?.let { stringResource(R.string.debug_map_gpu, it) }
    val lines = buildList {
        if (mapState.fogAvailable == false) add(stringResource(R.string.debug_map_unavailable))
        if (built == null) {
            add(stringResource(R.string.debug_map_building, dataset.key))
        } else {
            val segments = RevealedLayer.LOD.entries.joinToString("/") { lod -> built.layer.polylines(lod).sumOf { maxOf(0, it.size - 1) }.toString() }
            add(stringResource(R.string.debug_map_layer, built.dataset.key, built.trips, built.revealMs, segments))
        }
        mapState.fogMesh?.let { mesh ->
            add(stringResource(R.string.debug_map_mesh, mesh.buildMs, mesh.byteSize / 1048576.0, mesh.chunks.joinToString("/")))
        }
        stats?.let { s ->
            add(
                stringResource(
                    R.string.debug_map_frame, s.frames, s.encodeMsPerFrame, s.lastEncodeMs, s.lastLod, s.lastChunks, s.lastSegments,
                    s.lastHalfWidthPoints, gpu ?: none,
                ),
            )
            add(
                stringResource(
                    R.string.debug_map_life, s.uploads, s.initializations, s.contextLosses, s.coverageReleases, s.suspendedFrames,
                    if (s.suspended) stringResource(R.string.debug_map_suspended) else "",
                ),
            )
        }
        mapState.camera?.let { add(stringResource(R.string.debug_map_camera, it.zoom, it.metresPerPoint, it.bearing)) }
        tapped?.let { add(stringResource(R.string.debug_map_marker, it)) }
    }
    BasicText(
        lines.joinToString("\n"),
        Modifier.background(Color(0xCC000000), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
        style = TextStyle(color = Color.White, fontSize = 11.sp),
    )
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .background(Color(0xE6FFFFFF), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = TextStyle(color = Color(0xFF1E1E23), fontSize = 13.sp))
    }
}

/** A stand-in photo: a sunset gradient, so the marker shows a real image with its ring. */
private fun samplePhoto(): Bitmap {
    val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
    val paint = Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, 96f, 0xFFFF9A3C.toInt(), 0xFF4A2C8A.toInt(), Shader.TileMode.CLAMP)
    }
    Canvas(bitmap).drawRect(0f, 0f, 96f, 96f, paint)
    return bitmap
}
