package com.onezee.triptrack.location.auto

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import java.time.Instant

/** One IN_VEHICLE transition, dated by when it happened rather than when it arrived. */
data class VehicleTransition(val entered: Boolean, val at: Instant)

/**
 * iOS: MotionDetector.swift (CMMotionActivityManager, automotive). Android has no platform activity
 * recognition: the Transition API of Google Play services delivers IN_VEHICLE enter and exit to a
 * broadcast, even to a process that is not running, and its delivery is one of the exemptions that
 * may start a foreground service from the background.
 *
 * Registrations do not survive a reboot or an app update; [register] is called again then.
 */
class ActivityTransitions(private val context: Context) {
    val isAvailable: Boolean
        get() = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    val hasPermission: Boolean
        get() = context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun register() {
        if (!isAvailable || !hasPermission) return
        val request = ActivityTransitionRequest(
            listOf(ActivityTransition.ACTIVITY_TRANSITION_ENTER, ActivityTransition.ACTIVITY_TRANSITION_EXIT).map {
                ActivityTransition.Builder()
                    .setActivityType(DetectedActivity.IN_VEHICLE)
                    .setActivityTransition(it)
                    .build()
            },
        )
        ActivityRecognition.getClient(context).requestActivityTransitionUpdates(request, pendingIntent())
            .addOnFailureListener { Log.w(TAG, "activity transitions not registered", it) }
    }

    @SuppressLint("MissingPermission")
    fun unregister() {
        if (!isAvailable || !hasPermission) return
        ActivityRecognition.getClient(context).removeActivityTransitionUpdates(pendingIntent())
    }

    // Mutable: Play services writes the result into the intent's extras.
    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_CODE,
        Intent(context, ActivityTransitionReceiver::class.java).setAction(ACTION_TRANSITION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    companion object {
        const val ACTION_TRANSITION = "com.onezee.triptrack.location.ACTIVITY_TRANSITION"
        private const val REQUEST_CODE = 41
        private const val TAG = "ActivityTransitions"

        /** IN_VEHICLE events of a delivered result, oldest first, dated on the wall clock. */
        fun vehicleTransitions(intent: Intent, now: Instant = Instant.now()): List<VehicleTransition> {
            if (!ActivityTransitionResult.hasResult(intent)) return emptyList()
            val result = ActivityTransitionResult.extractResult(intent) ?: return emptyList()
            val elapsedNow = SystemClock.elapsedRealtimeNanos()
            return result.transitionEvents.filter { it.activityType == DetectedActivity.IN_VEHICLE }.map {
                val ago = (elapsedNow - it.elapsedRealTimeNanos).coerceAtLeast(0)
                VehicleTransition(it.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER, now.minusNanos(ago))
            }
        }
    }
}
