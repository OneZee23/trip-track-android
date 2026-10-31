// iOS: OnboardingPermissionCoordinator.swift, OnboardingLocationRequest.swift, TripTrackApp
// (@AppStorage("hasCompletedOnboarding"))
package com.onezee.triptrack.feature.onboarding

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.onezee.triptrack.location.permissions.PermissionFlow
import com.onezee.triptrack.location.permissions.PermissionLedger
import com.onezee.triptrack.location.permissions.PermissionPlan
import com.onezee.triptrack.location.permissions.RecordingPermission

/**
 * iOS: `@AppStorage("hasCompletedOnboarding")`, which gates the whole window. Same key, in the
 * app's default preferences, like the selected tab.
 */
class OnboardingStore(private val preferences: SharedPreferences) {
    var completed: Boolean by mutableStateOf(preferences.getString(KEY, null) == "true")
        private set

    fun complete() {
        completed = true
        preferences.edit { putString(KEY, "true") }
    }

    /** Settings «show onboarding again» and the UI tests. */
    fun reset() {
        completed = false
        preferences.edit { putString(KEY, "false") }
    }

    companion object {
        const val KEY = "hasCompletedOnboarding"
    }
}

/**
 * iOS: OnboardingPermissionCoordinator. Each explanation page has ONE neutral Continue, and Continue
 * leads to the system's own question; whatever the answer, refusal included, the page moves on. An
 * unsuccessful request never traps anyone in onboarding, and nothing is asked twice: the questions
 * are the recording's (`PermissionPlan`), and the ledger they are marked in is the one the record
 * screen reads, so a question answered here is not put again there.
 *
 * The background page asks location «all the time» and then physical activity, one after the
 * other: never one system question on top of another (iOS: never Motion & Fitness on top of a
 * location decision). Nearby devices are not asked here, as iOS asks nothing about Bluetooth in
 * onboarding; the car stereo screen asks when someone picks one.
 */
class OnboardingPermissionCoordinator(
    private val ledger: PermissionLedger,
    private val sdkInt: Int,
    /** Read fresh before every question: an answer, or the settings page, changes it. */
    private val granted: () -> Set<RecordingPermission>,
) {
    enum class Step { location, background, notifications }

    private val flow = PermissionFlow(ledger)
    private var step: Step? = null
    private var completion: (() -> Unit)? = null

    /** Puts the system question; its answer comes back through [answered] with the same token. */
    var ask: (permission: RecordingPermission, token: Int) -> Unit = { _, _ -> }

    /** From Continue until the page may move on, across all of its questions. */
    var isRequesting: Boolean by mutableStateOf(false)
        private set

    /**
     * iOS: proceed(from:completion:). A second tap while a page is still asking does nothing; a page
     * with nothing left to ask completes at once.
     */
    fun proceed(from: Step, completion: () -> Unit) {
        if (isRequesting) return
        step = from
        this.completion = completion
        isRequesting = true
        next()
    }

    /** The system answered, granted or not: both are decisions. A late answer of an earlier page is ignored. */
    fun answered(token: Int) {
        if (!flow.complete(token)) return
        next()
    }

    private fun next() {
        val current = step ?: return
        val question = nextQuestion(current, granted())
        if (question == null) {
            val done = completion
            completion = null
            step = null
            isRequesting = false
            done?.invoke()
            return
        }
        val token = flow.begin(question) ?: return
        ask(question, token)
    }

    /**
     * The next system question this page still has to put, or null when it may move on: not
     * applicable on this Android, already granted, already asked once (whatever the answer), or
     * background location without precise location, which the system would not offer.
     */
    fun nextQuestion(step: Step, granted: Set<RecordingPermission>): RecordingPermission? {
        val asked = ledger.asked()
        for (permission in questions(step)) {
            if (!PermissionPlan.applies(permission, sdkInt)) continue
            if (permission in granted || permission in asked) continue
            if (permission == RecordingPermission.backgroundLocation && RecordingPermission.fineLocation !in granted) continue
            return permission
        }
        return null
    }

    companion object {
        fun questions(step: Step): List<RecordingPermission> = when (step) {
            Step.location -> listOf(RecordingPermission.fineLocation)
            Step.background -> listOf(RecordingPermission.backgroundLocation, RecordingPermission.activityRecognition)
            Step.notifications -> listOf(RecordingPermission.notifications)
        }

        /**
         * What goes to the system for one question. From Android 12 a precise location request is
         * ignored unless approximate location is asked with it, and the dialog then offers both.
         */
        fun manifestNames(permission: RecordingPermission): Array<String> =
            PermissionPlan.manifestNames(permission)
    }
}
