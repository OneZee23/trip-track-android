// iOS: PhotoFullScreenView.swift, VehiclePhotoImage.swift
package com.onezee.triptrack.feature.me

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.settings.MeNavBar
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
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false)) {
        TripTrackTheme(isDark = true, language = language) {
            Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
                MeNavBar(title, onDismiss)
                HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { index -> photo(index) }
                actions(pager.currentPage)
            }
        }
    }
}
