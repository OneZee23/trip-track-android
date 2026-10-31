package com.onezee.triptrack.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.onezee.triptrack.data.files.TripPhotoFiles
import com.onezee.triptrack.designsystem.theme.AppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where a photo's bytes come from: the trip's JPEG on this phone, or the server copy. */
sealed interface PhotoSource {
    val key: String

    /** A file under `TripPhotos`, by the row's relative name. */
    data class Local(val filename: String) : PhotoSource {
        override val key: String get() = "file:$filename"
    }

    /** A signed URL the server returned for a thumbnail or an original. */
    data class Remote(val url: String) : PhotoSource {
        // The signature changes with every response; the object path names the photo.
        override val key: String get() = "url:" + url.substringBefore('?')
    }
}

/**
 * iOS: ThumbnailCache (memory, keyed by «name + size» since 0.7.0) and AsyncThumbnailView. Every
 * size is its OWN entry: before 0.7.0 all sizes on iOS shared one slot and whoever asked first
 * decided the size everyone else got. Decoding is sampled to the requested size and runs on [io].
 *
 * [remote] fetches a server photo (ApiClient.getBytes, which keeps the photo host allowlist);
 * null while there is no account client, and then only local files show.
 */
class TripPhotoImages(
    private val files: TripPhotoFiles,
    private val remote: (suspend (String) -> ByteArray)? = null,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val memory = object : LruCache<String, ImageBitmap>(MEMORY_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun cached(source: PhotoSource, maxPixels: Int): ImageBitmap? = memory.get(cacheKey(source, maxPixels))

    suspend fun load(source: PhotoSource, maxPixels: Int): ImageBitmap? {
        val key = cacheKey(source, maxPixels)
        memory.get(key)?.let { return it }
        return try {
            val bytes = withContext(io) {
                when (source) {
                    is PhotoSource.Local -> files.read(source.filename)
                    is PhotoSource.Remote -> remote?.invoke(source.url)
                }
            } ?: return null
            withContext(io) { decode(bytes, maxPixels) }?.also { memory.put(key, it) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A missing or refused photo is a grey tile, never a failed screen.
            null
        }
    }

    /** iOS: the cache drops a deleted photo's every size, the catalogue is enumerated, not listed. */
    fun forget(source: PhotoSource) {
        val prefix = source.key + "@"
        memory.snapshot().keys.filter { it.startsWith(prefix) }.forEach(memory::remove)
    }

    private fun cacheKey(source: PhotoSource, maxPixels: Int) = "${source.key}@$maxPixels"

    private fun decode(bytes: ByteArray, maxPixels: Int): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPixels) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }

    private companion object {
        const val MEMORY_BYTES = 48 * 1024 * 1024
    }
}

val LocalTripPhotoImages = staticCompositionLocalOf<TripPhotoImages?> { null }

/**
 * A photo filling its frame (iOS `AsyncThumbnailView`): the card-alt ground while it loads or when
 * the bytes are gone, the image cropped to fill once it arrives. [maxPixels] is the decode size, the
 * longer side in pixels.
 */
@Composable
fun PhotoImage(
    source: PhotoSource?,
    maxPixels: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
    placeholder: Color = AppTheme.colors.cardAlt,
) {
    val images = LocalTripPhotoImages.current
    val bitmap by produceState(source?.let { images?.cached(it, maxPixels) }, source, maxPixels, images) {
        if (value == null && source != null && images != null) value = images.load(source, maxPixels)
    }
    val image = bitmap
    if (image == null) {
        Box(modifier.background(placeholder))
    } else {
        Image(
            image,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            filterQuality = FilterQuality.Medium,
        )
    }
}
