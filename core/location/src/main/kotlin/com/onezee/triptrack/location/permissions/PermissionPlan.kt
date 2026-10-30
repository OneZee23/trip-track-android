package com.onezee.triptrack.location.permissions

import android.Manifest
import android.content.Context
import android.content.SharedPreferences

/** The runtime permissions recording and auto-record use, in the order they are asked. */
enum class RecordingPermission(val manifestName: String, val minSdk: Int) {
    fineLocation(Manifest.permission.ACCESS_FINE_LOCATION, 23),
    backgroundLocation(Manifest.permission.ACCESS_BACKGROUND_LOCATION, 29),
    activityRecognition(Manifest.permission.ACTIVITY_RECOGNITION, 29),
    notifications(Manifest.permission.POST_NOTIFICATIONS, 33),
    bluetoothConnect(Manifest.permission.BLUETOOTH_CONNECT, 31),
}

/** How the system question is put once the person pressed Continue. */
enum class RequestMethod {
    /** The ordinary permission dialog. */
    dialog,
    /**
     * Android 11+: background location has no dialog. Requesting it alone opens its settings page,
     * where the person picks «Allow all the time»; the answer is read when the app returns.
     */
    settingsPage,
}

/** What the screen shows next. */
sealed interface PermissionStep {
    /** The house explanation with ONE Continue; the native question follows it, never stacked. */
    data class Explain(val permission: RecordingPermission, val method: RequestMethod) : PermissionStep
    /** Every question was asked once. Denials stay denials; the health screen links to settings. */
    data object Done : PermissionStep
}

/**
 * iOS: OnboardingPermissionCoordinator and OnboardingLocationRequest. The explanation leads to the
 * system's decision, including a refusal, and a refusal never traps anyone: the flow moves on.
 * Nothing is asked twice: a permission once asked is not asked again, whatever the answer was
 * (no nagging); a later change goes through the system settings.
 */
object PermissionPlan {
    val order = RecordingPermission.entries

    fun applies(permission: RecordingPermission, sdkInt: Int): Boolean = sdkInt >= permission.minSdk

    fun method(permission: RecordingPermission, sdkInt: Int): RequestMethod =
        if (permission == RecordingPermission.backgroundLocation && sdkInt >= 30) RequestMethod.settingsPage else RequestMethod.dialog

    /**
     * The next question, or Done. Background location only follows a granted precise location:
     * without it the system would not offer «all the time», and asking would be a dead end.
     */
    fun next(
        sdkInt: Int,
        granted: Set<RecordingPermission>,
        asked: Set<RecordingPermission>,
        wanted: Set<RecordingPermission> = order.toSet(),
    ): PermissionStep {
        for (permission in order) {
            if (permission !in wanted || !applies(permission, sdkInt)) continue
            if (permission in granted || permission in asked) continue
            if (permission == RecordingPermission.backgroundLocation && RecordingPermission.fineLocation !in granted) continue
            return PermissionStep.Explain(permission, method(permission, sdkInt))
        }
        return PermissionStep.Done
    }

    /** Granted on this phone: what never applies (older Android) counts as granted. */
    fun effectiveGrants(sdkInt: Int, isGranted: (RecordingPermission) -> Boolean): Set<RecordingPermission> =
        order.filter { !applies(it, sdkInt) || isGranted(it) }.toSet()
}

/** Which questions were already put, so none is repeated. */
interface PermissionLedger {
    fun asked(): Set<RecordingPermission>
    fun markAsked(permission: RecordingPermission)

    class InMemory : PermissionLedger {
        private val values = mutableSetOf<RecordingPermission>()
        override fun asked(): Set<RecordingPermission> = values.toSet()
        override fun markAsked(permission: RecordingPermission) {
            values += permission
        }
    }
}

class PreferencesPermissionLedger(private val preferences: SharedPreferences) : PermissionLedger {
    constructor(context: Context) : this(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE))

    override fun asked(): Set<RecordingPermission> =
        preferences.getStringSet(KEY, emptySet()).orEmpty().mapNotNull { name -> RecordingPermission.entries.find { it.name == name } }.toSet()

    override fun markAsked(permission: RecordingPermission) {
        preferences.edit().putStringSet(KEY, asked().map { it.name }.toSet() + permission.name).apply()
    }

    companion object {
        const val FILE_NAME = "triptrack.permissions"
        private const val KEY = "asked"
    }
}

/**
 * iOS: the request bookkeeping of OnboardingPermissionCoordinator. A double tap cannot put a second
 * question, and a late answer from a previous page cannot finish the next one.
 */
class PermissionFlow(private val ledger: PermissionLedger) {
    private var pending: RecordingPermission? = null
    private var token = 0

    val isRequesting: Boolean get() = pending != null

    /** Continue pressed. Returns the token the system answer must carry, or null when one is pending. */
    fun begin(permission: RecordingPermission): Int? {
        if (pending != null) return null
        pending = permission
        ledger.markAsked(permission)
        return ++token
    }

    /** The system answered (granted or not, both are decisions). True if this answer was awaited. */
    fun complete(token: Int): Boolean {
        if (pending == null || token != this.token) return false
        pending = null
        return true
    }
}
