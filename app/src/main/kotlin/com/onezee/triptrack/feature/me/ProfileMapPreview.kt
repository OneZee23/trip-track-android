// iOS: MapSnapshotPreview.swift, VehicleMiniMap.swift
package com.onezee.triptrack.feature.me

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.IntSize
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.map.MapStyles
import com.onezee.triptrack.model.GeoPoint
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import kotlin.math.roundToInt

// iOS: MapSnapshotPreview cache. Geometry is part of the key: same-count route edits must redraw.
private data class ProfileMapKey(val routes: List<List<GeoPoint>>, val size: IntSize, val dark: Boolean, val density: Float)

internal object ProfileMapCache {
    private var generation = 0L
    private val cache = object : LruCache<ProfileMapKey, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: ProfileMapKey, value: Bitmap) = value.allocationByteCount
    }
    @Synchronized internal fun clear() { generation++; cache.evictAll() }
    @Synchronized internal fun epoch() = generation
    internal fun read(routes: List<List<GeoPoint>>, size: IntSize, dark: Boolean, density: Float): Bitmap? = cache.get(ProfileMapKey(routes, size, dark, density))
    @Synchronized internal fun put(routes: List<List<GeoPoint>>, size: IntSize, dark: Boolean, density: Float, bitmap: Bitmap, epoch: Long) {
        if (epoch == generation) cache.put(ProfileMapKey(routes, size, dark, density), bitmap)
    }
}

@Composable
internal fun ProfileMapPreview(routes: List<List<GeoPoint>>, modifier: Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val preview = LocalInspectionMode.current
    val dark = AppTheme.isDark
    var size by remember { mutableStateOf(IntSize.Zero) }
    var bitmap by remember(routes, dark) { mutableStateOf<Bitmap?>(null) }
    val points = remember(routes) { routes.flatten() }
    Box(modifier.onSizeChanged { size = it }) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
        else RoutePreview(points, Modifier.matchParentSize())
    }
    DisposableEffect(routes, size, dark, preview) {
        if (preview || points.size < 2 || size.width < 2 || size.height < 2) return@DisposableEffect onDispose {}
        val epoch = ProfileMapCache.epoch()
        val cached = ProfileMapCache.read(routes, size, dark, density)
        if (cached != null) { bitmap = cached; return@DisposableEffect onDispose {} }
        MapLibre.getInstance(context.applicationContext)
        val centreLat = (points.minOf { it.latitude } + points.maxOf { it.latitude }) / 2
        val centreLon = (points.minOf { it.longitude } + points.maxOf { it.longitude }) / 2
        val latSpan = maxOf((points.maxOf { it.latitude } - points.minOf { it.latitude }) * 1.4, .02)
        val lonSpan = maxOf((points.maxOf { it.longitude } - points.minOf { it.longitude }) * 1.4, .02)
        val region = LatLngBounds.from((centreLat + latSpan / 2).coerceAtMost(85.0), (centreLon + lonSpan / 2).coerceAtMost(180.0),
            (centreLat - latSpan / 2).coerceAtLeast(-85.0), (centreLon - lonSpan / 2).coerceAtLeast(-180.0))
        // Options are logical pixels; pixelRatio expands the result to the measured physical size.
        val options = MapSnapshotter.Options((size.width / density).roundToInt().coerceAtLeast(2), (size.height / density).roundToInt().coerceAtLeast(2)).withRegion(region).withPixelRatio(density)
            .withStyleBuilder(Style.Builder().fromUri(MapStyles.OPEN_FREE_MAP_LIBERTY)).withLogo(false).withAttribution(true)
        val snapshotter = MapSnapshotter(context, options)
        var current = true
        snapshotter.start({ snapshot ->
            if (current && ProfileMapCache.epoch() == epoch) {
                val drawn = snapshot.bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(drawn)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(194, 69, 43); strokeWidth = 3 * density
                    style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                }
                routes.forEach { route ->
                    val path = Path()
                    route.forEachIndexed { index, point ->
                        val screen = snapshot.pixelForLatLng(LatLng(point.latitude, point.longitude))
                        if (index == 0) path.moveTo(screen.x, screen.y) else path.lineTo(screen.x, screen.y)
                    }
                    canvas.drawPath(path, paint)
                }
                ProfileMapCache.put(routes, size, dark, density, drawn, epoch)
                bitmap = drawn
            }
        }, { /* A failed tile request leaves the same static route fallback as iOS. */ })
        onDispose { current = false; snapshotter.cancel() }
    }
}
