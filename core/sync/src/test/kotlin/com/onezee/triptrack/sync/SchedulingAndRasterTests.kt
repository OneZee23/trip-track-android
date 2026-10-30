package com.onezee.triptrack.sync

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import android.net.NetworkCapabilities
import androidx.work.BackoffPolicy
import androidx.work.NetworkType
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

// iOS: NetworkMonitor.swift and SyncCoordinator foreground timer; WorkManager is the Android adapter.
class SchedulingTests : SyncTestCase() {
    @Test fun persistentRequestsRequireInternetAndUseExponentialBackoff() {
        val immediate = SyncScheduler.immediateRequest().workSpec
        val periodic = SyncScheduler.periodicRequest().workSpec
        assertEquals(NetworkType.CONNECTED, immediate.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, immediate.backoffPolicy); assertEquals(10_000, immediate.backoffDelayDuration)
        assertEquals(900_000, periodic.intervalDuration); assertEquals(NetworkType.CONNECTED, periodic.constraints.requiredNetworkType)
    }
    @Test fun wifiMeansAValidatedInternetPath() {
        val capabilities = NetworkCapabilities()
        shadowOf(capabilities).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        assertEquals(NetworkState(isOffline = true), NetworkMonitor.fromCapabilities(capabilities))
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        assertEquals(NetworkState(isOnWiFi = true), NetworkMonitor.fromCapabilities(capabilities))
        shadowOf(capabilities).removeTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        shadowOf(capabilities).addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        assertEquals(NetworkState(), NetworkMonitor.fromCapabilities(capabilities))
    }
    @Test fun monitorStartsAndClosesIdempotently() { val monitor = NetworkMonitor(context); monitor.start(); monitor.start(); monitor.close(); monitor.close() }
}

// iOS: APISyncTransport.encodePhotoVariants, R2PhotoStorage.stripImageMetadata.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RasterPhotoEncoderTests : SyncTestCase() {
    @Test fun variantsHaveExactLimitsNoUpscalingAndNoExifGps() = runBlocking<Unit> {
        val bitmap = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.GREEN)
        val output = ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output); bitmap.recycle()
        val source = File(directory, "source.jpg").apply { writeBytes(output.toByteArray()) }
        ExifInterface(source.path).apply { setAttribute(ExifInterface.TAG_GPS_LATITUDE, "45/1,2/1,6/1"); setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N"); setAttribute(ExifInterface.TAG_USER_COMMENT, "test metadata"); saveAttributes() }
        val encoder = RasterPhotoEncoder()
        for ((limit, quality, width) in listOf(Triple(200, 50, 200), Triple(1440, 70, 400))) {
            val bytes = assertNotNull(encoder.jpeg(source.readBytes(), limit, quality))
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertEquals(width, decoded.width); assertEquals(width / 2, decoded.height); decoded.recycle()
            val file = File(directory, "variant-$limit.jpg").apply { writeBytes(bytes) }
            val exif = ExifInterface(file.path)
            assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)); assertNull(exif.getAttribute(ExifInterface.TAG_USER_COMMENT))
        }
    }
    @Test fun invalidImageCannotBeUploadedAsOriginalBytes() = runBlocking<Unit> {
        assertNull(RasterPhotoEncoder().jpeg(byteArrayOf(1, 2, 3), 1440, 70))
    }
}
