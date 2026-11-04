package com.onezee.triptrack.ui

// iOS: MapSnapshotPreview.swift, CardMapPreview.swift

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.map.MapLabels
import com.onezee.triptrack.model.GeoPoint
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import kotlin.coroutines.resume

internal data class SnapshotRegion(val centreLat: Double, val centreLon: Double, val latitudeSpan: Double, val longitudeSpan: Double)

internal data class SnapshotKey(val identity: String, val dark: Boolean, val width: Int, val height: Int, val coordinateHash: Int, val variant: String)

internal class WeightedLruCache<K, V>(private val capacity: Int, private val weightOf: (V) -> Int) {
    private val entries = LinkedHashMap<K, V>(16, 0.75f, true)
    private var weight = 0

    @Synchronized fun get(key: K): V? = entries[key]

    @Synchronized fun put(key: K, value: V) {
        entries.remove(key)?.let { weight -= weightOf(it) }
        val itemWeight = weightOf(value).coerceAtLeast(0)
        if (itemWeight > capacity) return
        entries[key] = value
        weight += itemWeight
        while (weight > capacity) {
            val eldest = entries.entries.iterator().next()
            weight -= weightOf(eldest.value)
            entries.remove(eldest.key)
        }
    }

    @Synchronized fun clear() {
        entries.clear()
        weight = 0
    }
}

internal object SnapshotRules {
    val retryDelaysMillis = listOf(0L, 1_500L, 4_000L)

    fun region(points: List<GeoPoint>, minimumSpan: Double = 0.005): SnapshotRegion {
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }
        return SnapshotRegion((minLat + maxLat) / 2, (minLon + maxLon) / 2,
            max((maxLat - minLat) * 1.8, minimumSpan), max((maxLon - minLon) * 1.8, minimumSpan))
    }

    fun finishFlagPointsUp(endY: Float, poleHeight: Float = 6f, flagHeight: Float = 3f): Boolean = endY > poleHeight + flagHeight + 2f

    fun key(identity: String, dark: Boolean, width: Int, height: Int, points: List<GeoPoint>, variant: String) =
        SnapshotKey(identity, dark, width, height, points.hashCode(), variant)
}

@Suppress("DEPRECATION")
internal object SnapshotEngine {
    private val gate = Semaphore(2)
    private val cache = WeightedLruCache<SnapshotKey, Bitmap>(50 * 1024 * 1024) { it.allocationByteCount }
    private var memoryCallbackRegistered = false
    private var cacheGeneration = 0L

    fun cached(key: SnapshotKey) = cache.get(key)
    fun cache(key: SnapshotKey, bitmap: Bitmap) = cache.put(key, bitmap)
    @Synchronized fun clear() {
        cacheGeneration++
        cache.clear()
    }
    suspend fun withRenderPermit(action: suspend () -> Unit) = gate.withPermit { action() }

    @Synchronized fun installMemoryCallback(context: Context) {
        if (memoryCallbackRegistered) return
        memoryCallbackRegistered = true
        context.applicationContext.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            @Suppress("DEPRECATION")
            override fun onLowMemory() = clear()
            @Suppress("DEPRECATION")
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) clear()
            }
        })
    }

    suspend fun render(
        context: Context,
        points: List<GeoPoint>,
        key: SnapshotKey,
        density: Float,
        styleUri: String,
        language: String,
        minimumSpan: Double,
        overlay: Overlay,
    ): Bitmap? {
        cached(key)?.let { return it }
        return gate.withPermit {
            cached(key)?.let { return it }
            val generation = synchronized(this@SnapshotEngine) { cacheGeneration }
            MapLibre.getInstance(context.applicationContext)
            val region = SnapshotRules.region(points, minimumSpan)
            val bounds = LatLngBounds.from(
                (region.centreLat + region.latitudeSpan / 2).coerceAtMost(85.0),
                (region.centreLon + region.longitudeSpan / 2).coerceAtMost(180.0),
                (region.centreLat - region.latitudeSpan / 2).coerceAtLeast(-85.0),
                (region.centreLon - region.longitudeSpan / 2).coerceAtLeast(-180.0),
            )
            val options = MapSnapshotter.Options((key.width / density).roundToInt().coerceAtLeast(2), (key.height / density).roundToInt().coerceAtLeast(2))
                .withRegion(bounds).withPixelRatio(density).withStyleBuilder(Style.Builder().fromUri(styleUri))
                .withLogo(false).withAttribution(false)
            val snapshotter = MapSnapshotter(context, options)
            snapshotter.setObserver(object : MapSnapshotter.Observer {
                override fun onDidFinishLoadingStyle() {
                    MapLabels.styleLabelLayers.forEach { id -> MapLabels.localize(snapshotter.getLayer(id), language) }
                }
                override fun onStyleImageMissing(imageName: String) = Unit
            })
            try {
                suspendCancellableCoroutine { continuation ->
                    continuation.invokeOnCancellation { runCatching { snapshotter.cancel() } }
                    snapshotter.start({ result ->
                        if (continuation.isActive) continuation.resume(overlay.draw(result.bitmap, result, points, density).also {
                            if (synchronized(this@SnapshotEngine) { cacheGeneration == generation }) cache(key, it)
                        })
                    }, { if (continuation.isActive) continuation.resume(null) })
                }
            } finally {
                runCatching { snapshotter.cancel() }
            }
        }
    }
}

internal sealed interface Overlay {
    fun draw(source: Bitmap, snapshot: org.maplibre.android.snapshotter.MapSnapshot, points: List<GeoPoint>, density: Float): Bitmap

    data class Trip(val accent: Color, val green: Color, val label: Color) : Overlay {
        override fun draw(source: Bitmap, snapshot: org.maplibre.android.snapshotter.MapSnapshot, points: List<GeoPoint>, density: Float): Bitmap {
            val bitmap = source.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(bitmap)
            val locations = points.map { snapshot.pixelForLatLng(LatLng(it.latitude, it.longitude)) }
            val path = Path().apply {
                moveTo(locations.first().x, locations.first().y)
                locations.drop(1).forEach { lineTo(it.x, it.y) }
            }
            canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(191, (accent.red * 255).roundToInt(), (accent.green * 255).roundToInt(), (accent.blue * 255).roundToInt())
                strokeWidth = 2f * density; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            })
            val start = locations.first()
            canvas.drawCircle(start.x, start.y, 2.5f * density, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green.toArgbInt() })
            val end = locations.last()
            val pole = 6f * density; val flagWidth = 4f * density; val flagHeight = 3f * density
            val up = SnapshotRules.finishFlagPointsUp(end.y / density)
            val direction = if (up) -1 else 1
            canvas.drawLine(end.x, end.y, end.x, end.y + pole * direction, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = label.toArgbInt(); strokeWidth = density
            })
            val left = end.x
            val top = if (up) end.y - pole else end.y + pole
            val cellW = flagWidth / 2; val cellH = flagHeight / 2
            val black = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK }
            val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
            canvas.drawRect(left, top, left + cellW, top + cellH, black)
            canvas.drawRect(left + cellW, top + cellH, left + flagWidth, top + flagHeight, black)
            canvas.drawRect(left + cellW, top, left + flagWidth, top + cellH, white)
            canvas.drawRect(left, top + cellH, left + cellW, top + flagHeight, white)
            return bitmap
        }
    }

    data class Route(val color: Color, val width: Float) : Overlay {
        override fun draw(source: Bitmap, snapshot: org.maplibre.android.snapshotter.MapSnapshot, points: List<GeoPoint>, density: Float): Bitmap {
            val bitmap = source.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(bitmap)
            val path = Path()
            points.forEachIndexed { index, point ->
                val p = snapshot.pixelForLatLng(LatLng(point.latitude, point.longitude))
                if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = this@Route.color.toArgbInt(); strokeWidth = width * density; style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            })
            return bitmap
        }
    }

    data class MultiRoute(val routes: List<List<GeoPoint>>, val color: Color, val width: Float) : Overlay {
        override fun draw(source: Bitmap, snapshot: org.maplibre.android.snapshotter.MapSnapshot, points: List<GeoPoint>, density: Float): Bitmap {
            val bitmap = source.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = this@MultiRoute.color.toArgbInt(); strokeWidth = width * density; style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            }
            routes.filter { it.size > 1 }.forEach { route ->
                val path = Path()
                route.forEachIndexed { index, point ->
                    val p = snapshot.pixelForLatLng(LatLng(point.latitude, point.longitude))
                    if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                canvas.drawPath(path, paint)
            }
            return bitmap
        }
    }
}

private fun Color.toArgbInt() = android.graphics.Color.argb((alpha * 255).roundToInt(), (red * 255).roundToInt(), (green * 255).roundToInt(), (blue * 255).roundToInt())

@Composable
internal fun MapSnapshotPreview(
    coordinates: List<GeoPoint>,
    tripId: UUID,
    modifier: Modifier = Modifier,
    height: Dp = 80.dp,
    width: Dp? = 340.dp,
    cornerRadius: Dp = 12.dp,
) = MapSnapshotPreview(coordinates, tripId.toString(), modifier, height, width, cornerRadius)

@Composable
internal fun MapSnapshotPreview(
    coordinates: List<GeoPoint>,
    identity: String,
    modifier: Modifier = Modifier,
    height: Dp = 80.dp,
    width: Dp? = 340.dp,
    cornerRadius: Dp = 12.dp,
) = MapSnapshotPreviewContent(listOf(coordinates), identity, modifier, height, width, cornerRadius,
    Overlay.Trip(AppTheme.accent, SystemColors.green(AppTheme.isDark), AppTheme.colors.text), "trip", placeholderAccent = null)

@Composable
internal fun MapSnapshotRoutesPreview(
    routes: List<List<GeoPoint>>,
    identity: String,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    cornerRadius: Dp = 16.dp,
) = MapSnapshotPreviewContent(routes, identity, modifier, height, null, cornerRadius,
    Overlay.MultiRoute(routes, AppTheme.accent, 3f), "journey", placeholderAccent = AppTheme.accent)

@Composable
private fun MapSnapshotPreviewContent(
    routes: List<List<GeoPoint>>,
    identity: String,
    modifier: Modifier,
    height: Dp,
    width: Dp?,
    cornerRadius: Dp,
    overlay: Overlay,
    variant: String,
    /** iOS: the placeholder is LightRoutePreview's own orange; journeys keep their accent line. */
    placeholderAccent: Color?,
) {
    val context = LocalContext.current.applicationContext
    val density = LocalDensity.current.density
    val dark = AppTheme.isDark
    // The activity's configuration carries the app's own language; the application context's may not.
    val language = LocalConfiguration.current.locales[0].language
    val live = LocalLiveMaps.current && !LocalInspectionMode.current
    val coordinates = remember(routes) { routes.flatten() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var bitmap by remember(identity, coordinates, dark) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(identity, coordinates, dark) { mutableStateOf(false) }
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(cornerRadius)
    val shimmer = rememberInfiniteTransition(label = "snapshot_shimmer").animateFloat(
        initialValue = -0.7f, targetValue = 1.7f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing), RepeatMode.Restart), label = "snapshot_shimmer_offset",
    ).value
    val fallback = @Composable {
        Box(Modifier.fillMaxSize().background(colors.cardAlt)) {
            val line = Modifier.matchParentSize().alpha(if (live) 0.6f else 1f)
            if (placeholderAccent != null) LightRoutePreview(routes, line, accent = placeholderAccent) else LightRoutePreview(routes, line)
            if (live && bitmap == null && !failed) {
                Box(Modifier.matchParentSize().background(Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = if (dark) 0.08f else 0.32f), Color.Transparent),
                    start = androidx.compose.ui.geometry.Offset(size.width * shimmer, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width * (shimmer + 0.45f), size.height.toFloat()),
                )))
            }
            if (failed && live) Glyph(AppGlyph.mapSlash, null, tint = colors.textTertiary, size = 12.dp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp))
        }
    }
    val snapshotAlpha = animateFloatAsState(if (bitmap != null) 1f else 0f,
        tween(350, easing = androidx.compose.animation.core.LinearOutSlowInEasing), label = "snapshot_fade").value
    Box(modifier.height(height).clip(shape).onSizeChanged { size = it }) {
        if (!live || coordinates.size < 2) fallback()
        else if (bitmap == null) fallback()
        else Image(bitmap!!.asImageBitmap(), null, Modifier.matchParentSize().alpha(snapshotAlpha), contentScale = ContentScale.Crop)
    }
    LaunchedEffect(identity, coordinates, dark, size, live, language) {
        bitmap = null
        failed = false
        if (!live || coordinates.size < 2 || size.width < 2 || size.height < 2) return@LaunchedEffect
        SnapshotEngine.installMemoryCallback(context)
        val renderWidthPx = width?.let { (it.value * density).roundToInt() } ?: size.width
        val renderHeightPx = (height.value * density).roundToInt()
        val key = SnapshotRules.key(identity, dark, renderWidthPx, renderHeightPx, coordinates, "$variant-$language")
        SnapshotEngine.cached(key)?.let { bitmap = it; return@LaunchedEffect }
        SnapshotRules.retryDelaysMillis.forEachIndexed { index, wait ->
            if (wait > 0) delay(wait)
            try {
                val image = SnapshotEngine.render(context, coordinates, key, density, LiveMaps.style(dark), language, 0.005, overlay)
                if (image != null) { bitmap = image; return@LaunchedEffect }
                if (index == 0) failed = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Throwable) { if (index == 0) failed = true }
        }
    }
}
