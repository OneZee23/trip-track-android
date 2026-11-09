package com.onezee.triptrack.debug

import android.annotation.SuppressLint
import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log

/**
 * Debug builds only: a drive on the bench. Replaces the phone's GPS provider with a test one and moves
 * it north from [START_LATITUDE]/[START_LONGITUDE] at a steady speed, one fix a second, so a recording
 * with the screen off can be checked without leaving the desk. Every app on the phone sees the fake
 * position while it runs: always end with `mockstop`.
 *
 * Needs `adb shell appops set com.onezee.triptrack.dev android:mock_location allow` first.
 */
object BenchMockGps {
    private const val TAG = "BenchMockGps"
    // Open sea off West Africa: a bench drive must not open real roads in the Atlas of the phone it runs on.
    private const val START_LATITUDE = 1.0
    private const val START_LONGITUDE = 1.0
    private var thread: HandlerThread? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @SuppressLint("MissingPermission", "WrongConstant")
    @Synchronized
    fun start(context: Context, metresPerSecond: Float, seconds: Int): String {
        stop(context)
        val manager = context.getSystemService(LocationManager::class.java)
        manager.addTestProvider(
            LocationManager.GPS_PROVIDER, false, false, false, false, true, true, true,
            Criteria.POWER_HIGH, Criteria.ACCURACY_FINE,
        )
        manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
        // The injector must not sleep with the screen: a silent bench would read as a cut GPS.
        wakeLock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TripTrack:benchMock")
            .apply { acquire(seconds * 1_000L + 10_000L) }
        val worker = HandlerThread("BenchMockGps").also { it.start() }
        thread = worker
        val handler = Handler(worker.looper)
        var second = 0
        handler.post(object : Runnable {
            override fun run() {
                if (second > seconds) {
                    stop(context)
                    return
                }
                val north = second * metresPerSecond
                val fix = Location(LocationManager.GPS_PROVIDER).apply {
                    latitude = START_LATITUDE + north / 111_320.0
                    longitude = START_LONGITUDE
                    altitude = 30.0
                    accuracy = 5f
                    speed = metresPerSecond
                    bearing = 0f
                    time = System.currentTimeMillis()
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                }
                runCatching { manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, fix) }
                    .onFailure { Log.w(TAG, "inject failed at $second s", it) }
                if (second % 10 == 0) Log.i(TAG, "injected t=$second")
                second++
                handler.postDelayed(this, 1_000)
            }
        })
        return "mock drive ${seconds}s at $metresPerSecond m/s"
    }

    @Synchronized
    fun stop(context: Context): String {
        thread?.quitSafely()
        thread = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        runCatching { context.getSystemService(LocationManager::class.java).removeTestProvider(LocationManager.GPS_PROVIDER) }
        return "mock stopped"
    }
}
