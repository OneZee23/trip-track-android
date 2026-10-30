package com.onezee.triptrack.location.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.onezee.triptrack.location.LocationGraphOwner
import com.onezee.triptrack.location.drafts.DraftDecision
import com.onezee.triptrack.location.recording.PauseSource
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * iOS: the Live Activity intents and NotificationManager.didReceive. Every notification button
 * lands here, also when the process is dead: the graph is rebuilt by the Application, the launch
 * recovery runs first, then the action.
 *
 * A draft decision is written to the queue before anything else, so it survives even if the
 * process dies before it is applied; the next launch applies it.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = (context.applicationContext as? LocationGraphOwner)?.locationGraph ?: return
        val action = intent.action ?: return
        val tripId = intent.getStringExtra(EXTRA_TRIP_ID)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        when (action) {
            ACTION_CONFIRM_DRAFT -> tripId?.let { graph.queueDraftDecision(it, DraftDecision.confirm) }
            ACTION_DISCARD_DRAFT -> tripId?.let { graph.queueDraftDecision(it, DraftDecision.discard) }
        }
        val pending = goAsync()
        graph.scope.launch {
            try {
                graph.awaitStarted()
                when (action) {
                    ACTION_PAUSE -> graph.recorder.setPaused(true, PauseSource.notification)
                    ACTION_RESUME -> graph.recorder.setPaused(false, PauseSource.notification)
                    // The person pressed Finish: a pause does not protect the trip from its owner.
                    ACTION_FINISH -> graph.recorder.stop()
                    ACTION_STOP_NOW -> graph.autoTrip.stopRequestedByUser()
                    ACTION_CONTINUE -> graph.autoTrip.continueRequestedByUser()
                    ACTION_CONFIRM_DRAFT, ACTION_DISCARD_DRAFT -> graph.drafts.apply()
                }
            } catch (failure: Exception) {
                Log.w(TAG, "notification action $action failed", failure)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "NotificationAction"
        const val EXTRA_TRIP_ID = "tripId"
        const val ACTION_PAUSE = "com.onezee.triptrack.location.PAUSE"
        const val ACTION_RESUME = "com.onezee.triptrack.location.RESUME"
        const val ACTION_FINISH = "com.onezee.triptrack.location.FINISH"
        // The iOS action identifiers.
        const val ACTION_STOP_NOW = "STOP_NOW"
        const val ACTION_CONTINUE = "CONTINUE_RECORDING"
        const val ACTION_CONFIRM_DRAFT = "CONFIRM_DRAFT"
        const val ACTION_DISCARD_DRAFT = "DISCARD_DRAFT"
    }
}
