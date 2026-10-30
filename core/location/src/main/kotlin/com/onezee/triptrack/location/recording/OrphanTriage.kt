package com.onezee.triptrack.location.recording

import com.onezee.triptrack.data.RecordingOrphan
import com.onezee.triptrack.logic.isJunk
import com.onezee.triptrack.model.TripJunkClassifier
import java.time.Instant
import java.util.UUID

/** The one orphan worth asking about: the most recent one, younger than the restorable age. */
data class RecoverableRecording(
    val orphan: RecordingOrphan,
    /** From the start to the last recorded point. */
    val durationSeconds: Double,
    /** The recording stopped only moments ago: the person is still driving, so resume without asking. */
    val isFresh: Boolean,
)

// iOS: TripManager.swift, cleanupOrphanedTrips. Pure: the recorder applies the verdicts.
object OrphanTriage {
    /**
     * A long road trip killed mid-drive (memory, reboot) must still resume as ONE track the same day;
     * a far older orphan is a recording somebody forgot to stop.
     */
    const val maxRestorableAgeSeconds = 6 * 3600.0

    /**
     * Under fifteen minutes the answer is obvious enough not to ask: a crash at a traffic light must
     * not put a question in front of a person who is still driving. Above it, ask.
     */
    const val silentResumeWindowSeconds = 15 * 60.0

    data class Verdict(
        val delete: List<UUID>,
        val close: List<Pair<UUID, Instant>>,
        val recoverable: RecoverableRecording?,
    )

    fun triage(orphans: List<RecordingOrphan>, now: Instant): Verdict {
        val delete = mutableListOf<UUID>()
        val candidates = mutableListOf<Pair<RecordingOrphan, Instant>>()
        for (orphan in orphans) {
            // No points: nothing to keep.
            if (orphan.pointCount == 0) {
                delete += orphan.id
                continue
            }
            val last = orphan.lastPointAt ?: now
            val duration = maxOf(0.0, secondsBetween(orphan.startDate, last))
            if (TripJunkClassifier.isJunk(orphan.distance, duration, orphan.maxSpeed)) {
                delete += orphan.id
                continue
            }
            candidates += orphan to last
        }
        // At most ONE recording is offered; every other one is closed at its last fix, so it can
        // never linger as a zombie that triggers recovery again. A non-junk orphan is never discarded.
        val mostRecent = candidates.maxByOrNull { it.second }
        val close = mutableListOf<Pair<UUID, Instant>>()
        var recoverable: RecoverableRecording? = null
        for ((orphan, last) in candidates) {
            val age = secondsBetween(last, now)
            if (orphan === mostRecent?.first && age < maxRestorableAgeSeconds) {
                recoverable = RecoverableRecording(
                    orphan = orphan,
                    durationSeconds = secondsBetween(orphan.startDate, last),
                    isFresh = age < silentResumeWindowSeconds,
                )
            } else {
                close += orphan.id to last
            }
        }
        return Verdict(delete, close, recoverable)
    }
}
