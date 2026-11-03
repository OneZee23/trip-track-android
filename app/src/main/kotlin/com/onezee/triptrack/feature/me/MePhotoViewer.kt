// iOS: PhotoFullScreenView.swift, VehiclePhotoImage.swift
package com.onezee.triptrack.feature.me

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.designsystem.components.CustomNavBar
import com.onezee.triptrack.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val LocalMePhotoClient = staticCompositionLocalOf<ApiClient?> { null }

// iOS: VehiclePhotoImage cache. Signed URLs rotate; the object path is stable for a photo id.
internal object MePhotoCache {
    private var generation = 0L
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    @Synchronized fun clear() { generation++; cache.evictAll() }
    @Synchronized private fun epoch() = generation
    @Synchronized private fun keep(epoch: Long, key: String, image: Bitmap): Bitmap? = if (epoch == generation) image.also { cache.put(key, it) } else null
    suspend fun remote(url: String, client: ApiClient): Bitmap? {
        val epoch = epoch()
        val key = url.substringBefore('?')
        cache.get(key)?.let { return it }
        val bytes = client.getBytes(url)
        return withContext(Dispatchers.Default) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val options = BitmapFactory.Options().apply { while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 1440) inSampleSize *= 2 }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.let { keep(epoch, key, it) }
        }
    }
}

@Composable
internal fun MePhotoViewer(title: String, initialIndex: Int, count: Int, onDismiss: () -> Unit,
                           photo: @Composable (Int) -> Unit, actions: @Composable ColumnScope.(Int) -> Unit = {}) {
    if (count == 0) return
    val language = appStrings.language
    val pager = rememberPagerState(initialIndex.coerceIn(0, count - 1)) { count }
    var zoomed by remember { mutableStateOf(false) }
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false)) {
        TripTrackTheme(isDark = true, language = language) {
            Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
                CustomNavBar(title, onDismiss)
                HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), userScrollEnabled = !zoomed) { index ->
                    ZoomableMePhoto(index == pager.currentPage, { zoomed = it }) { photo(index) }
                }
                actions(pager.currentPage)
            }
        }
    }
}

/** iOS: ZoomableImageView. Zoom belongs to a page; paging is available again at rest. */
@Composable
internal fun ZoomableMePhoto(current: Boolean, onZoomChanged: (Boolean) -> Unit, photo: @Composable () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val zoomChanged by rememberUpdatedState(onZoomChanged)
    LaunchedEffect(current) {
        if (!current) { scale = 1f; offset = Offset.Zero }
        else zoomChanged(scale > 1.01f)
    }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().testTag("me_photo_zoom")) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        fun bounded(value: Offset, zoom: Float) = Offset(value.x.coerceIn(-width * (zoom - 1) / 2, width * (zoom - 1) / 2),
            value.y.coerceIn(-height * (zoom - 1) / 2, height * (zoom - 1) / 2))
        Box(Modifier.fillMaxSize()
            .pointerInput(width, height) {
                detectTapGestures(onDoubleTap = { at ->
                    if (scale > 1.01f) { scale = 1f; offset = Offset.Zero }
                    else { scale = 2.5f; offset = bounded((Offset(width / 2, height / 2) - at) * 1.5f, scale) }
                    zoomChanged(scale > 1.01f)
                })
            }
            .pointerInput(width, height) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var pan = Offset.Zero
                    var panning = false
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            val next = (scale * event.calculateZoom()).coerceIn(1f, 4f)
                            val ratio = next / scale
                            val centroid = event.calculateCentroid(useCurrent = false) - Offset(width / 2, height / 2)
                            offset = bounded(offset * ratio + centroid * (1 - ratio) + event.calculatePan(), next)
                            scale = next
                            zoomChanged(scale > 1.01f)
                            event.changes.forEach { it.consume() }
                        } else if (pressed == 1 && scale > 1.01f) {
                            val delta = event.changes.first { it.pressed }.positionChange()
                            pan += delta
                            if (!panning && pan.getDistance() > viewConfiguration.touchSlop) panning = true
                            if (panning) {
                                offset = bounded(offset + delta, scale)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }) { photo() }
    }
}
