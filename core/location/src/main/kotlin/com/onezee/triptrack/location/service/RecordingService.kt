package com.onezee.triptrack.location.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import com.onezee.triptrack.location.LocationGraph
import com.onezee.triptrack.location.LocationGraphOwner
import com.onezee.triptrack.location.provider.FusedLocationSource
import com.onezee.triptrack.location.provider.PlatformLocationSource
import com.onezee.triptrack.location.provider.StandbyLocationSource
import com.onezee.triptrack.location.provider.TrackingMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The foreground service of a recording, type `location`: it owns the GPS subscription and the
 * ongoing notification, and lives exactly as long as a recording (and its finishing) does.
 *
 * Android 14+ rules: the type is declared in the manifest and passed to `startForeground`, with
 * FOREGROUND_SERVICE_LOCATION and a granted location permission; `startForeground` runs at once in
 * `onStartCommand`. A start from the background is allowed only in the exempt cases the callers
 * are in (activity transition, Bluetooth broadcast, notification action, boot, the app on screen).
 *
 * START_STICKY: if the system kills the process mid-drive it restarts the service, the launch
 * recovery adopts the fresh orphan, and the recording continues as one trip.
 */
class RecordingService : Service() {
    private lateinit var graph: LocationGraph
    private var job: Job? = null
    private val source by lazy {
        StandbyLocationSource(PlatformLocationSource(this), FusedLocationSource.createIfAvailable(this)) { usingStandby ->
            graph.rawFixLog.mark(if (usingStandby) "standbyOn" else "standbyOff")
        }
    }

    override fun onCreate() {
        super.onCreate()
        graph = (application as LocationGraphOwner).locationGraph
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(RecordingNotifications.ONGOING_ID, graph.notifications.ongoing(graph.recorder.state.value),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } catch (refused: Exception) {
            // No location permission any more, or a background start the system did not allow.
            Log.w(TAG, "foreground start refused", refused)
            graph.scope.launch { graph.serviceRefused() }
            stopSelf()
            return START_NOT_STICKY
        }
        if (job == null) job = graph.scope.launch { run() }
        else if (intent?.action == ACTION_REFRESH_LOCATION && graph.recorder.state.value.isRecording) startRecordingSource()
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        job = null
        source.stop()
        super.onDestroy()
    }

    private suspend fun run() {
        graph.awaitStarted()
        // iOS: AutoTripService.swift, significant-location wake. A sticky service can return
        // without the stereo's original connect broadcast reaching this process.
        if (!graph.recorder.state.value.isRecording) graph.autoTrip.onWake()
        if (!graph.recorder.state.value.isRecording && !graph.recorder.state.value.isFinishing) return finish()
        var gpsOn = false
        coroutineScope {
            val ticks = launch {
                while (isActive) {
                    delay(TICK_MS)
                    graph.recorder.tick()
                }
            }
            var gpsLostNotified = false
            // At most one repost a second (a StateFlow skips what a slow collector missed); the
            // chronometer ticks on its own in between.
            graph.recorder.state.first { state ->
                // GPS follows the recording: off as soon as it ends, on again if the next trip
                // starts while the previous one is still being finished.
                if (state.isRecording != gpsOn) {
                    gpsOn = state.isRecording
                    if (gpsOn) {
                        startRecordingSource()
                    } else {
                        source.stop()
                    }
                }
                if (state.gpsLost != gpsLostNotified) {
                    gpsLostNotified = state.gpsLost
                    if (gpsLostNotified) graph.notifications.gpsLost() else graph.notifications.clearGpsLost()
                }
                when {
                    state.isRecording -> graph.notifications.updateOngoing(state)
                    state.isFinishing -> graph.notifications.updateFinishing()
                    else -> return@first true
                }
                delay(UPDATE_MS)
                false
            }
            ticks.cancel()
        }
        finish()
    }

    private fun finish() {
        source.stop()
        graph.notifications.clearGpsLost()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        job = null
    }

    private fun startRecordingSource() {
        source.start(TrackingMode.recording) { fix, age ->
            graph.scope.launch { graph.recorder.onProviderFix(fix, age) }
        }
    }

    companion object {
        private const val TAG = "RecordingService"
        private const val TICK_MS = 500L
        private const val UPDATE_MS = 1_000L
        private const val ACTION_REFRESH_LOCATION = "com.onezee.triptrack.location.REFRESH_LOCATION"

        /** False when the system refused the start (a background start outside the exemptions). */
        fun start(context: Context): Boolean = try {
            context.startForegroundService(Intent(context, RecordingService::class.java))
            true
        } catch (refused: IllegalStateException) {
            Log.w(TAG, "service start refused", refused)
            false
        } catch (refused: SecurityException) {
            Log.w(TAG, "service start refused", refused)
            false
        }

        fun refreshLocation(context: Context): Boolean = try {
            context.startForegroundService(Intent(context, RecordingService::class.java).setAction(ACTION_REFRESH_LOCATION))
            true
        } catch (refused: IllegalStateException) {
            Log.w(TAG, "location refresh refused", refused)
            false
        } catch (refused: SecurityException) {
            Log.w(TAG, "location refresh refused", refused)
            false
        }
    }
}
