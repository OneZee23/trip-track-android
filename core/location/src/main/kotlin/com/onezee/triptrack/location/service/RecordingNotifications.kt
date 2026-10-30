package com.onezee.triptrack.location.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.onezee.triptrack.designsystem.R as Strings
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.location.R
import com.onezee.triptrack.location.auto.AutoTripNotices
import com.onezee.triptrack.location.auto.StopPromptReason
import com.onezee.triptrack.location.recording.RecorderState
import com.onezee.triptrack.location.recording.StartRefusal
import com.onezee.triptrack.location.recording.TripNotices
import com.onezee.triptrack.logic.distance
import com.onezee.triptrack.logic.speed
import com.onezee.triptrack.model.DistanceUnit
import com.onezee.triptrack.model.Measure
import java.time.Instant
import java.util.Locale
import java.util.UUID

/** iOS: Trip.formattedDuration, `H:MM:SS` from an hour on, `MM:SS` below. */
fun formattedDuration(seconds: Double): String {
    val total = maxOf(0, seconds.toLong())
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val rest = total % 60
    return if (hours > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest)
    else String.format(Locale.ROOT, "%02d:%02d", minutes, rest)
}

/**
 * The texts of the recording notifications, apart from Android so they can be checked without
 * building a notification. Distances and speeds go through `Measure`, words through `AppStrings`.
 */
class RecordingTexts(private val strings: AppStrings, private val unit: DistanceUnit) {
    private val lang get() = strings.language.code

    fun ongoingTitle(state: RecorderState): String = if (state.isPaused) strings.live_paused() else strings.live_recording()

    /** «12.4 km · 54 km/h» while driving; the clock replaces the speed during a pause. */
    fun ongoingText(state: RecorderState, now: Instant): String {
        val distance = Measure.distance(state.distance, unit, lang, strings)
        return if (state.isPaused) "$distance · ${formattedDuration(state.elapsedSeconds(now))}"
        else "$distance · ${Measure.speed(state.speed, unit, lang, strings)}"
    }

    fun pauseAction(state: RecorderState): String = if (state.isPaused) strings.live_resume() else strings.live_pause()
    fun finishAction(): String = strings.live_finish()
    fun draftLabel(): String = strings.draftRecordingLabel()
    fun routeSaved(): String = strings.live_routeSaved()

    fun stopPromptTitle(): String = strings.notifTripStopTitle()
    fun stopPromptBody(minutes: Int?, reason: StopPromptReason): String = strings.notifTripStopBody(
        minutes, if (reason == StopPromptReason.bluetooth) AppStrings.TripStopReason.bluetooth else AppStrings.TripStopReason.inactivity,
    )

    fun autoStartFailedTitle(): String = strings.notifAutoStartFailedTitle()

    /** iOS: notifAutoStartFailedBody. */
    fun autoStartFailedBody(reason: StartRefusal): String = strings.tr(
        when (reason) {
            StartRefusal.locationDenied -> Strings.string.notifStartFailedNoGeo
            StartRefusal.recoveryPending -> Strings.string.notifStartFailedRecovery
            StartRefusal.alreadyRecording, StartRefusal.unknown -> Strings.string.notifStartFailedManual
        },
    )

    fun autoStoppedTitle(): String = strings.notifAutoStopTitle()
    fun autoStoppedBody(metres: Double, seconds: Double): String = strings.notifAutoStopSummary(
        Measure.distance(metres, unit, lang, strings, Measure.Style.tenths), formattedDuration(seconds),
    )

    fun draftConfirmTitle(): String = strings.notifDraftConfirmTitle()
    fun draftConfirmBody(metres: Double): String =
        strings.notifDraftConfirmBody(Measure.distance(metres, unit, lang, strings, Measure.Style.tenths))

    fun confirmAction(): String = strings.draftConfirm()
    fun discardAction(): String = strings.draftDiscard()
    fun stopNowAction(): String = strings.notifStopNowAction()
    fun continueAction(): String = strings.notifContinueAction()

    // Channel names are shown in the system settings.
    fun recordingChannel(): String = strings.live_recording()
    fun autoTripChannel(): String = strings.autoRecord()
    fun draftsChannel(): String = strings.draftsTitle()
}

/**
 * iOS: LiveActivityManager and NotificationManager. On Android the Live Activity is the foreground
 * service's ongoing notification: distance, speed and the driving clock (a chronometer, so it ticks
 * without reposting), with Pause/Resume and Finish. The other notifications keep the iOS
 * identifiers as tags, so «clear the question of this trip» means one notification.
 */
class RecordingNotifications(
    private val context: Context,
    private val texts: () -> RecordingTexts,
    private val clock: () -> Instant = Instant::now,
) : TripNotices, AutoTripNotices {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        val texts = texts()
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_RECORDING, texts.recordingChannel(), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
                NotificationChannel(CHANNEL_AUTO_TRIP, texts.autoTripChannel(), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_DRAFTS, texts.draftsChannel(), NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    /** The foreground service's notification. */
    fun ongoing(state: RecorderState): Notification {
        val texts = texts()
        val now = clock()
        val builder = Notification.Builder(context, CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_stat_recording)
            .setContentTitle(texts.ongoingTitle(state))
            .setContentText(texts.ongoingText(state, now))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(openApp())
            .addAction(action(texts.pauseAction(state), if (state.isPaused) NotificationActionReceiver.ACTION_RESUME else NotificationActionReceiver.ACTION_PAUSE, null))
            .addAction(action(texts.finishAction(), NotificationActionReceiver.ACTION_FINISH, null))
        if (state.isDraft) builder.setSubText(texts.draftLabel())
        if (!state.isPaused && state.startDate != null) {
            // The chronometer counts from `when`: shifting it by the paused time shows driving time.
            builder.setUsesChronometer(true).setShowWhen(true)
                .setWhen(System.currentTimeMillis() - (state.elapsedSeconds(now) * 1000).toLong())
        } else {
            builder.setShowWhen(false)
        }
        if (Build.VERSION.SDK_INT >= 31) builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        return builder.build()
    }

    fun updateOngoing(state: RecorderState) {
        manager.notify(ONGOING_ID, ongoing(state))
    }

    /** The recording ended and the trip is being saved: no buttons left to press. */
    fun updateFinishing() {
        val notification = Notification.Builder(context, CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_stat_recording)
            .setContentTitle(texts().routeSaved())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openApp())
            .build()
        manager.notify(ONGOING_ID, notification)
    }

    // MARK: AutoTripNotices

    override fun tripStopPrompt(minutes: Int?, reason: StopPromptReason) {
        val texts = texts()
        val notification = Notification.Builder(context, CHANNEL_AUTO_TRIP)
            .setSmallIcon(R.drawable.ic_stat_recording)
            .setContentTitle(texts.stopPromptTitle())
            .setContentText(texts.stopPromptBody(minutes, reason))
            .setStyle(Notification.BigTextStyle().bigText(texts.stopPromptBody(minutes, reason)))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .addAction(action(texts.stopNowAction(), NotificationActionReceiver.ACTION_STOP_NOW, null))
            .addAction(action(texts.continueAction(), NotificationActionReceiver.ACTION_CONTINUE, null))
            .build()
        manager.notify(STOP_PROMPT_TAG, PROMPT_ID, notification)
    }

    override fun cancelTripStopPrompt() {
        manager.cancel(STOP_PROMPT_TAG, PROMPT_ID)
    }

    override fun autoStartFailed(reason: StartRefusal) {
        val texts = texts()
        post(AUTO_START_FAILED_TAG, CHANNEL_AUTO_TRIP, texts.autoStartFailedTitle(), texts.autoStartFailedBody(reason))
    }

    override fun autoStopped(metres: Double, durationSeconds: Double) {
        val texts = texts()
        post(AUTO_STOPPED_TAG, CHANNEL_AUTO_TRIP, texts.autoStoppedTitle(), texts.autoStoppedBody(metres, durationSeconds))
    }

    // MARK: TripNotices

    override fun draftConfirmPrompt(tripId: UUID, metres: Double) {
        val texts = texts()
        val notification = Notification.Builder(context, CHANNEL_DRAFTS)
            .setSmallIcon(R.drawable.ic_stat_recording)
            .setContentTitle(texts.draftConfirmTitle())
            .setContentText(texts.draftConfirmBody(metres))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .addAction(action(texts.confirmAction(), NotificationActionReceiver.ACTION_CONFIRM_DRAFT, tripId))
            .addAction(action(texts.discardAction(), NotificationActionReceiver.ACTION_DISCARD_DRAFT, tripId))
            .build()
        manager.notify(draftConfirmTag(tripId), PROMPT_ID, notification)
    }

    override fun clearDraftPrompt(tripId: UUID) {
        manager.cancel(draftConfirmTag(tripId), PROMPT_ID)
    }

    /** iOS posts «Recording a trip» on its own; here the ongoing notification carries the draft label. */
    override fun clearDraftStartedNotice() = Unit

    private fun post(tag: String, channel: String, title: String, body: String) {
        val notification = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_recording)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        manager.notify(tag, PROMPT_ID, notification)
    }

    private fun openApp(): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun action(title: String, action: String, tripId: UUID?): Notification.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).setAction(action)
        tripId?.let { intent.putExtra(NotificationActionReceiver.EXTRA_TRIP_ID, it.toString()) }
        // One request code per action and trip, so two drafts' buttons never share an intent.
        val requestCode = (action + (tripId ?: "")).hashCode()
        val pending = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Action.Builder(null, title, pending).build()
    }

    companion object {
        const val CHANNEL_RECORDING = "recording"
        const val CHANNEL_AUTO_TRIP = "auto_trip"
        const val CHANNEL_DRAFTS = "drafts"
        const val ONGOING_ID = 1001
        private const val PROMPT_ID = 1002
        // The iOS request identifiers.
        const val STOP_PROMPT_TAG = "trip-stop-prompt"
        const val AUTO_START_FAILED_TAG = "trip-auto-start-failed"
        const val AUTO_STOPPED_TAG = "trip-auto-stopped"
        fun draftConfirmTag(tripId: UUID) = "trip-draft-${tripId.toString().uppercase()}"
    }
}
