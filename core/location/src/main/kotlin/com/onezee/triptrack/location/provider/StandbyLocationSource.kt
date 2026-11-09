package com.onezee.triptrack.location.provider

import android.annotation.SuppressLint
import android.content.Context
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.onezee.triptrack.logic.LocationFix

/**
 * A recording's GPS through two subscriptions: the platform provider, whose raw fixes the pipeline
 * was built for, and a standby that speaks only while the platform one is silent.
 *
 * MIUI's power keeper removes the platform location request of an app in its default battery saver
 * a minute after the screen locks, foreground service or not, and gives it back only when the screen
 * comes on (the owner's POCO F1, 7 and 9 Nov 2026). Google Play services is exempt: on the bench
 * drive of 9 Nov the platform request went at +70 s and the fused one kept the recording at one fix a
 * second to the end. A phone whose platform provider works records exactly what it did before:
 * standby fixes are dropped while platform ones arrive. Only GNSS-grade standby fixes pass, so a
 * tunnel gets the usual gap and its road fill, not a zigzag of Wi-Fi positions. There is no iOS
 * counterpart; CoreLocation is not cut by anyone.
 */
class StandbyLocationSource(
    private val primary: LocationSource,
    private val standby: LocationSource?,
    private val elapsedMillis: () -> Long = SystemClock::elapsedRealtime,
    /** `true` when the standby starts speaking, `false` when the primary is back. */
    private val onSwitch: (usingStandby: Boolean) -> Unit = {},
) : LocationSource {
    // The two providers call back on their own threads.
    private val lock = Any()
    private var lastPrimaryAt = 0L
    private var usingStandby = false

    @Synchronized
    override fun start(mode: TrackingMode, onFix: (LocationFix, Double) -> Unit) {
        stop()
        synchronized(lock) {
            lastPrimaryAt = elapsedMillis()
            usingStandby = false
        }
        primary.start(mode) { fix, age ->
            val switched = synchronized(lock) {
                lastPrimaryAt = elapsedMillis()
                usingStandby.also { usingStandby = false }
            }
            if (switched) onSwitch(false)
            onFix(fix, age)
        }
        // Idle needs no rescue: nothing is recorded, and the record screen is on.
        if (mode != TrackingMode.recording) return
        standby?.start(mode) { fix, age ->
            if (fix.horizontalAccuracy !in 0.0..STANDBY_MAX_ACCURACY_M) return@start
            val switched = synchronized(lock) {
                if (elapsedMillis() - lastPrimaryAt < PRIMARY_SILENCE_MS) return@start
                (!usingStandby).also { usingStandby = true }
            }
            if (switched) onSwitch(true)
            onFix(fix, age)
        }
    }

    @Synchronized
    override fun stop() {
        primary.stop()
        standby?.stop()
    }

    companion object {
        /** Three missed one-second fixes: the primary is gone, not late. */
        const val PRIMARY_SILENCE_MS = 3_000L

        /** GNSS grade. Wi-Fi and cell positions of the fused provider stay out of the track. */
        const val STANDBY_MAX_ACCURACY_M = 20.0
    }
}

/**
 * The fused provider at GNSS accuracy, one fix a second, for [StandbyLocationSource]. Null on phones
 * without Google Play services, where the platform provider stays alone.
 */
class FusedLocationSource private constructor(context: Context) : LocationSource {
    private val client = LocationServices.getFusedLocationProviderClient(context)
    private val converter = PlatformLocationSource(context)
    private var thread: HandlerThread? = null
    private var callback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    @Synchronized
    override fun start(mode: TrackingMode, onFix: (LocationFix, Double) -> Unit) {
        stop()
        val worker = HandlerThread("TripTrackFused").also { it.start() }
        thread = worker
        val created = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    val (fix, age) = converter.convert(location)
                    onFix(fix, age)
                }
            }
        }
        callback = created
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
            .setMinUpdateIntervalMillis(INTERVAL_MS)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            client.requestLocationUpdates(request, created, worker.looper)
        } catch (denied: SecurityException) {
            Log.w(TAG, "no location permission", denied)
        }
    }

    @Synchronized
    override fun stop() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
        thread?.quitSafely()
        thread = null
    }

    companion object {
        private const val TAG = "FusedLocationSource"
        private const val INTERVAL_MS = 1_000L

        fun createIfAvailable(context: Context): FusedLocationSource? =
            if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS) {
                FusedLocationSource(context)
            } else {
                null
            }
    }
}
