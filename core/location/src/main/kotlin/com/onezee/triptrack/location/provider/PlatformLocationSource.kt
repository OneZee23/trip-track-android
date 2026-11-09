package com.onezee.triptrack.location.provider

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import com.onezee.triptrack.logic.LocationFix
import java.time.Instant

/** iOS: RealGPSProvider's two modes. */
enum class TrackingMode { idle, recording }

/** iOS: LocationProviding. Fixes come with their age in seconds, for `FixGate`. */
interface LocationSource {
    fun start(mode: TrackingMode, onFix: (fix: LocationFix, ageSeconds: Double) -> Unit)
    fun stop()
}

/**
 * iOS: RealGPSProvider.swift, on the platform GPS provider.
 *
 * Recording asks for raw GNSS fixes every second with NO distance filter, the way iOS records
 * (`kCLDistanceFilterNone`): a filter drops the slow manoeuvres in a yard and the Kalman filter,
 * the continuity gate and the odometer door downstream are the app's own and expect raw input.
 * The fused provider would hand them positions it already smoothed, with speeds it estimated, and
 * would not exist at all on phones without Google services; it only stands in while this one is
 * silent (`StandbyLocationSource`).
 *
 * Idle (the record screen before a start) mirrors iOS's low-power mode: the network provider for a
 * quick first position plus GPS every five seconds or 50 metres.
 */
class PlatformLocationSource(
    context: Context,
    private val wallClock: () -> Instant = Instant::now,
) : LocationSource {
    private val manager = context.getSystemService(LocationManager::class.java)
    private var thread: HandlerThread? = null
    private var listener: LocationListener? = null

    @SuppressLint("MissingPermission")
    @Synchronized
    override fun start(mode: TrackingMode, onFix: (LocationFix, Double) -> Unit) {
        stop()
        val worker = HandlerThread("TripTrackLocation").also { it.start() }
        thread = worker
        val created = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val (fix, age) = convert(location)
                onFix(fix, age)
            }

            override fun onProviderDisabled(provider: String) {
                Log.w(TAG, "provider disabled: $provider")
            }

            override fun onProviderEnabled(provider: String) {
                Log.i(TAG, "provider enabled: $provider")
            }
        }
        listener = created
        try {
            when (mode) {
                TrackingMode.recording ->
                    manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, RECORDING_INTERVAL_MS, 0f, created, worker.looper)
                TrackingMode.idle -> {
                    if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, IDLE_INTERVAL_MS, IDLE_DISTANCE_M, created, worker.looper)
                    }
                    manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, IDLE_INTERVAL_MS, IDLE_DISTANCE_M, created, worker.looper)
                }
            }
            Log.i(TAG, "start mode=$mode")
        } catch (denied: SecurityException) {
            Log.w(TAG, "no location permission", denied)
        } catch (missing: IllegalArgumentException) {
            // No GPS hardware or provider: nothing will be delivered.
            Log.w(TAG, "provider unavailable", missing)
        }
    }

    @Synchronized
    override fun stop() {
        listener?.let { manager.removeUpdates(it) }
        listener = null
        thread?.quitSafely()
        thread = null
    }

    /**
     * Unknown measurements stay -1, as CoreLocation reports them; the gates and the filter tell
     * «unknown» from zero by the sign. The time is the phone's clock minus the fix's age on the
     * monotonic clock: GNSS time can differ from the clock every trip date is written with.
     */
    internal fun convert(location: Location): Pair<LocationFix, Double> {
        val ageNanos = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos).coerceAtLeast(0)
        val fix = LocationFix(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = if (location.hasAltitude()) location.altitude else 0.0,
            horizontalAccuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else -1.0,
            verticalAccuracy = if (location.hasVerticalAccuracy()) location.verticalAccuracyMeters.toDouble() else -1.0,
            speed = if (location.hasSpeed()) location.speed.toDouble() else -1.0,
            speedAccuracy = if (location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond.toDouble() else -1.0,
            course = if (location.hasBearing()) location.bearing.toDouble() else -1.0,
            courseAccuracy = if (location.hasBearingAccuracy()) location.bearingAccuracyDegrees.toDouble() else -1.0,
            timestamp = wallClock().minusNanos(ageNanos),
        )
        return fix to ageNanos / 1e9
    }

    companion object {
        const val RECORDING_INTERVAL_MS = 1_000L
        const val IDLE_INTERVAL_MS = 5_000L
        const val IDLE_DISTANCE_M = 50f
        private const val TAG = "PlatformLocationSource"
    }
}
