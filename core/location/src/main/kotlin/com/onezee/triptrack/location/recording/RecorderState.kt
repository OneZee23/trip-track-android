package com.onezee.triptrack.location.recording

import android.content.Context
import android.content.SharedPreferences
import com.onezee.triptrack.location.world.TripRewardsResult
import com.onezee.triptrack.model.TripConfirmation
import com.onezee.triptrack.model.TripJunkClassifier
import com.onezee.triptrack.model.Trip
import java.time.Instant
import java.util.UUID

/** What the record screen, the ongoing notification and the auto-trip service read. Metres and m/s. */
data class RecorderState(
    val tripId: UUID? = null,
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val startDate: Instant? = null,
    val vehicleId: UUID? = null,
    val isTransfer: Boolean = false,
    val confirmation: TripConfirmation = TripConfirmation.confirmed,
    val distance: Double = 0.0,
    val maxSpeed: Double = 0.0,
    /** The speedometer (`LiveSpeed`), not a stored number. */
    val speed: Double = 0.0,
    /** Accuracy of the last accepted fix; zero means no fix has been accepted yet («Finding GPS»). */
    val gpsAccuracy: Double = 0.0,
    val gpsStale: Boolean = true,
    val pausedSeconds: Double = 0.0,
    val pauseStartedAt: Instant? = null,
    /** A recording the process died in, waiting for «Continue» or «Finish and save». */
    val recovery: RecoverableRecording? = null,
    /**
     * The trip has stopped and is being finished (rewards, post-processing, places, name). The
     * foreground service stays until this clears, so the process is not reclaimed halfway.
     */
    val isFinishing: Boolean = false,
) {
    val isDraft: Boolean get() = confirmation == TripConfirmation.draft

    /** Driving time: wall time minus every pause, the clock the notification shows. */
    fun elapsedSeconds(now: Instant): Double {
        val start = startDate ?: return 0.0
        val pausedNow = pauseStartedAt?.let { secondsBetween(it, now) } ?: 0.0
        return maxOf(0.0, secondsBetween(start, now) - pausedSeconds - pausedNow)
    }
}

/**
 * iOS: MapViewModel.StartRefusal. A refused start is a value somebody can show. `unknown` is also
 * Android's «the system did not let the foreground service start».
 */
enum class StartRefusal { alreadyRecording, locationDenied, recoveryPending, unknown }

sealed interface StartResult {
    data class Started(val tripId: UUID) : StartResult
    data class Refused(val reason: StartRefusal) : StartResult
}

/** Where a finished recording went (iOS: `TripWorldEntry.Route` and what `stopRecording` did with it). */
sealed interface StopOutcome {
    data object NotRecording : StopOutcome
    data class Discarded(val tripId: UUID, val reason: TripJunkClassifier.Reason?) : StopOutcome
    data class AwaitingConfirmation(val trip: Trip) : StopOutcome
    data class Saved(val trip: Trip, val rewards: TripRewardsResult) : StopOutcome
}

/** Who asked for the pause: the log needs it, the logic does not (iOS: `PauseSource`). */
enum class PauseSource { screen, notification, carScreen }

/** Hooks of the auto-trip service into the recording (iOS: AutoTripService.recordingDidStart/End…). */
interface RecorderListener {
    fun recordingDidStart(tripId: UUID) = Unit
    fun recordingDidEnd() = Unit
    fun manualPause() = Unit
    /** Every accepted fix, recording or not (iOS: updateMovementForInactivity). */
    fun onAcceptedFix(state: RecorderState) = Unit
}

/**
 * The pause state of the open recording, kept outside the database so a recording the process died
 * in resumes paused if it was paused. iOS does not need this: a paused recording keeps its app
 * alive there, while Android vendors kill even foreground services.
 */
data class RecorderSession(
    val tripId: UUID,
    val isPaused: Boolean,
    val pausedSeconds: Double,
    val pauseStartedAt: Instant?,
)

interface RecorderSessionStore {
    fun load(): RecorderSession?
    fun save(session: RecorderSession?)

    class InMemory : RecorderSessionStore {
        private var value: RecorderSession? = null
        override fun load(): RecorderSession? = value
        override fun save(session: RecorderSession?) {
            value = session
        }
    }
}

class PreferencesRecorderSessionStore(private val preferences: SharedPreferences) : RecorderSessionStore {
    constructor(context: Context) : this(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE))

    override fun load(): RecorderSession? {
        val id = preferences.getString(KEY_TRIP, null)?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
        return RecorderSession(
            tripId = id,
            isPaused = preferences.getBoolean(KEY_PAUSED, false),
            pausedSeconds = preferences.getFloat(KEY_PAUSED_SECONDS, 0f).toDouble(),
            pauseStartedAt = preferences.getLong(KEY_PAUSE_STARTED, -1).takeIf { it >= 0 }?.let(Instant::ofEpochMilli),
        )
    }

    override fun save(session: RecorderSession?) {
        val editor = preferences.edit().clear()
        if (session != null) {
            editor.putString(KEY_TRIP, session.tripId.toString())
                .putBoolean(KEY_PAUSED, session.isPaused)
                .putFloat(KEY_PAUSED_SECONDS, session.pausedSeconds.toFloat())
                .putLong(KEY_PAUSE_STARTED, session.pauseStartedAt?.toEpochMilli() ?: -1)
        }
        // Commit, not apply: the next thing that happens may be the process dying.
        editor.commit()
    }

    companion object {
        const val FILE_NAME = "triptrack.recording.session"
        private const val KEY_TRIP = "tripId"
        private const val KEY_PAUSED = "isPaused"
        private const val KEY_PAUSED_SECONDS = "pausedSeconds"
        private const val KEY_PAUSE_STARTED = "pauseStartedAt"
    }
}
