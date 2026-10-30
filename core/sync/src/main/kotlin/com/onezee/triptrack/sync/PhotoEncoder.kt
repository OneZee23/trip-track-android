package com.onezee.triptrack.sync

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// iOS: APISyncTransport.encodePhotoVariants, UIImage.resized, R2PhotoStorage.stripImageMetadata.
interface PhotoEncoder {
    suspend fun jpeg(data: ByteArray, maxDimension: Int, quality: Int): ByteArray?
}

// iOS: UIImage drawing + JPEG encoder. Drawing pixels strips EXIF/GPS/IPTC/XMP.
class RasterPhotoEncoder : PhotoEncoder {
    override suspend fun jpeg(data: ByteArray, maxDimension: Int, quality: Int): ByteArray? = withContext(Dispatchers.Default) {
        val decoded = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return@withContext null
        val matrix = Matrix()
        val orientation = try {
            ExifInterface(ByteArrayInputStream(data)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: java.io.IOException) { ExifInterface.ORIENTATION_NORMAL }
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        }
        val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val scale = minOf(maxDimension.toDouble() / oriented.width, maxDimension.toDouble() / oriented.height, 1.0)
        val raster = Bitmap.createBitmap(maxOf(1, (oriented.width * scale).toInt()), maxOf(1, (oriented.height * scale).toInt()), Bitmap.Config.ARGB_8888)
        try {
            Canvas(raster).drawBitmap(oriented, null, android.graphics.Rect(0, 0, raster.width, raster.height), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            val output = ByteArrayOutputStream()
            if (raster.compress(Bitmap.CompressFormat.JPEG, quality, output)) output.toByteArray() else null
        } finally {
            raster.recycle()
            if (oriented !== decoded) oriented.recycle()
            decoded.recycle()
        }
    }
}
