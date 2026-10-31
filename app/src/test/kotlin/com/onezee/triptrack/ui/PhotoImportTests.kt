package com.onezee.triptrack.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// iOS: PhotoMetadataTests.swift (capture timestamps from a real JPEG).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoImportTests {
    private val stamp = "2026:09:10 14:23:05"
    private val shot = LocalDateTime.of(2026, 9, 10, 14, 23, 5)

    @Test fun testTimeWithoutAPlaceStaysTimeWithoutAPlace() {
        val exif = jpeg(ExifInterface.TAG_DATETIME_ORIGINAL to stamp)
        assertEquals(shot.atZone(ZoneId.systemDefault()).toInstant(), PhotoImport.capturedAt(exif))
    }

    @Test fun testAnExplicitZoneOffsetWins() {
        val exif = jpeg(ExifInterface.TAG_DATETIME_ORIGINAL to stamp, ExifInterface.TAG_OFFSET_TIME_ORIGINAL to "+03:00")
        assertEquals(shot.toInstant(ZoneOffset.ofHours(3)), PhotoImport.capturedAt(exif))
    }

    @Test fun testAFrameWithoutExifInventsNothing() {
        assertNull(PhotoImport.capturedAt(jpeg()))
    }

    @Test fun digitizationIsTheFallbackAndEditTimeIsNeverCaptureTime() {
        assertEquals(shot.toInstant(ZoneOffset.ofHours(3)), PhotoImport.capturedAt(jpeg(
            ExifInterface.TAG_DATETIME_DIGITIZED to stamp, ExifInterface.TAG_OFFSET_TIME_DIGITIZED to "+03:00",
        )))
        assertNull(PhotoImport.capturedAt(jpeg(ExifInterface.TAG_DATETIME to stamp)))
    }

    @Test fun zeroOrInvalidCaptureDatesAreUnknown() {
        assertNull(PhotoImport.capturedAt(jpeg(ExifInterface.TAG_DATETIME_ORIGINAL to "0000:00:00 00:00:00")))
        assertNull(PhotoImport.capturedAt(jpeg(ExifInterface.TAG_DATETIME_ORIGINAL to "2026:02:30 14:23:05")))
    }

    private fun jpeg(vararg attributes: Pair<String, String>): ExifInterface {
        val file = Files.createTempFile("photo-metadata", ".jpg").toFile()
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            ExifInterface(file).apply {
                attributes.forEach { (tag, value) -> setAttribute(tag, value) }
                saveAttributes()
            }
            return ExifInterface(file)
        } finally {
            bitmap.recycle()
            file.delete()
        }
    }
}
