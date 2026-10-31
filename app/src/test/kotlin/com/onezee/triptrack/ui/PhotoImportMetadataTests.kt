// iOS: PhotoMetadataTests.swift (real JPEG capture-time fixtures).
package com.onezee.triptrack.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoImportMetadataTests {
    private fun captured(tags: Map<String, String>, zone: ZoneId = ZoneOffset.UTC): Instant? {
        val file = File.createTempFile("trip-photo-metadata", ".jpg")
        try {
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) } }
            finally { bitmap.recycle() }
            ExifInterface(file).apply { tags.forEach { (tag, value) -> setAttribute(tag, value) }; saveAttributes() }
            return PhotoImport.capturedAt(ExifInterface(file), zone)
        } finally { file.delete() }
    }
    @Test fun aFrameWithExifGivesUpItsTime() {
        assertEquals(Instant.parse("2026-09-10T11:23:05Z"), captured(mapOf(ExifInterface.TAG_DATETIME_ORIGINAL to STAMP), ZoneOffset.ofHours(3)))
    }
    @Test fun anExplicitZoneOffsetWins() {
        assertEquals(Instant.parse("2026-09-10T11:23:05Z"), captured(mapOf(ExifInterface.TAG_DATETIME_ORIGINAL to STAMP,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL to "+03:00"), ZoneOffset.ofHours(-5)))
    }
    @Test fun aFrameWithoutExifInventsNothing() { assertNull(captured(emptyMap())) }
    @Test fun editingTimeDoesNotBecomeCaptureTime() { assertNull(captured(mapOf(ExifInterface.TAG_DATETIME to STAMP))) }
    @Test fun digitizedTimeIsTheFallbackAndAnUnknownDateStaysUnknown() {
        assertEquals(Instant.parse("2026-09-10T14:23:05Z"), captured(mapOf(ExifInterface.TAG_DATETIME_DIGITIZED to STAMP)))
        assertNull(captured(mapOf(ExifInterface.TAG_DATETIME_ORIGINAL to "0000:00:00 00:00:00")))
    }
    companion object {
        private const val STAMP = "2026:09:10 14:23:05"
    }
}
