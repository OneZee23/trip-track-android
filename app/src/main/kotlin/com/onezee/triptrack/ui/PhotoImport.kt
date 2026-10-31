package com.onezee.triptrack.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.onezee.triptrack.data.TripRepository
import com.onezee.triptrack.data.files.TripPhotoFiles
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * iOS: TripPhotoPicker.swift. The capture time and the place are read from the picked file HERE and
 * nowhere else: once the photo is a JPEG in the trip's folder neither survives (CLAUDE.md 0.6.5).
 * The stored image is re-encoded, at most 2048 on its long edge, without its metadata.
 */
object PhotoImport {
    private const val MAX_EDGE = 2048

    /** Returns how many photos were added. */
    suspend fun import(context: Context, uris: List<Uri>, tripId: UUID, repository: TripRepository): Int = withContext(Dispatchers.IO) {
        uris.count { uri ->
            try {
                importOne(context, uri, tripId, repository)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
        }
    }

    private suspend fun importOne(context: Context, uri: Uri, tripId: UUID, repository: TripRepository): Boolean {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
        val exif = runCatching { ExifInterface(ByteArrayInputStream(bytes)) }.getOrNull()
        val location = exif?.latLong
        val captured = exif?.dateTimeOriginal?.let(Instant::ofEpochMilli)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return false
        val scale = minOf(1f, MAX_EDGE.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate((exif?.rotationDegrees ?: 0).toFloat())
        }
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val jpeg = ByteArrayOutputStream().use { out ->
            upright.compress(Bitmap.CompressFormat.JPEG, TripPhotoFiles.JPEG_QUALITY, out)
            out.toByteArray()
        }
        return repository.addPhoto(tripId, jpeg, caption = null, capturedAt = captured, latitude = location?.get(0), longitude = location?.get(1)) != null
    }
}
