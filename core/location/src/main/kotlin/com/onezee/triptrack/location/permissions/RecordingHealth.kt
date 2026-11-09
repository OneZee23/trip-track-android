package com.onezee.triptrack.location.permissions

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import com.onezee.triptrack.designsystem.R
import com.onezee.triptrack.location.provider.FusedLocationSource
import java.util.Locale

/** The vendors whose power managers stop background apps beyond what Android itself does. */
enum class Oem {
    samsung, xiaomi, huawei, other;

    companion object {
        fun from(manufacturer: String, brand: String = manufacturer): Oem {
            val names = listOf(manufacturer, brand).map { it.lowercase(Locale.ROOT) }
            return when {
                names.any { it.contains("samsung") } -> samsung
                names.any { it.contains("xiaomi") || it.contains("redmi") || it.contains("poco") } -> xiaomi
                names.any { it.contains("huawei") || it.contains("honor") } -> huawei
                else -> other
            }
        }
    }
}

/** One thing that can stop or degrade a recording, worst first. */
enum class HealthIssue(val message: Int) {
    locationOff(R.string.android_health_location_off),
    noPreciseLocation(R.string.android_health_precise_off),
    notificationsOff(R.string.android_health_notifications_off),
    batteryOptimized(R.string.android_health_battery_optimized),
    backgroundRestricted(R.string.android_health_restricted),
    autoStartDisabled(R.string.android_health_autostart_off),
    noBackgroundLocation(R.string.android_health_background_off),
    noActivityRecognition(R.string.android_health_activity_off),
    noBluetooth(R.string.android_health_bluetooth_off),
    powerSave(R.string.android_health_power_save),
}

/**
 * Why a recording might not survive on this phone. There is no iOS counterpart: iOS does not let
 * the person or the vendor throttle a navigation-class background app.
 */
data class RecordingHealth(
    val locationEnabled: Boolean,
    val preciseLocation: Boolean,
    val backgroundLocation: Boolean,
    val activityRecognition: Boolean,
    val notifications: Boolean,
    val bluetoothConnect: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val backgroundRestricted: Boolean,
    val powerSave: Boolean,
    val oem: Oem,
    /** Auto-record is on: background location, activity and the stereo matter only then. */
    val autoRecord: Boolean,
    val rememberedBackgroundRestriction: BackgroundRestrictionReading = BackgroundRestrictionReading.unknown,
    val autostartDenied: Boolean? = null,
    /** Google Play services can stand in for a cut platform GPS (`StandbyLocationSource`). */
    val standbyGps: Boolean = true,
) {
    /**
     * The switch MIUI cut GPS with on the owner's POCO (7 Nov 2026): background-restricted, GPS gone a
     * minute after the screen locked although the service ran. Battery optimisation alone is the
     * default on nearly every phone and does not stop a location service, so it stays a health issue
     * and never raises the record-screen card.
     */
    val backgroundGpsRestricted: Boolean
        get() = shouldShowBackgroundGpsRestriction(
            oem, backgroundRestricted, rememberedBackgroundRestriction, autostartDenied, autoRecord, standbyGps,
        )

    val issues: List<HealthIssue>
        get() = buildList {
            if (!locationEnabled) add(HealthIssue.locationOff)
            if (!preciseLocation) add(HealthIssue.noPreciseLocation)
            if (!notifications) add(HealthIssue.notificationsOff)
            if (!ignoringBatteryOptimizations) add(HealthIssue.batteryOptimized)
            if (backgroundGpsRestricted) add(HealthIssue.backgroundRestricted)
            if (oem == Oem.xiaomi && autoRecord && autostartDenied == true) add(HealthIssue.autoStartDisabled)
            if (autoRecord && !backgroundLocation) add(HealthIssue.noBackgroundLocation)
            if (autoRecord && !activityRecognition) add(HealthIssue.noActivityRecognition)
            if (autoRecord && !bluetoothConnect) add(HealthIssue.noBluetooth)
            if (powerSave) add(HealthIssue.powerSave)
        }

    /** The vendor's own instructions, shown whatever the issues: their limits are invisible to apps. */
    val oemSteps: Int?
        get() = when (oem) {
            Oem.samsung -> R.string.android_health_oem_samsung
            Oem.xiaomi -> R.string.android_health_oem_xiaomi
            Oem.huawei -> R.string.android_health_oem_huawei
            Oem.other -> null
        }

    companion object {
        fun read(context: Context, autoRecord: Boolean): RecordingHealth {
            fun granted(permission: RecordingPermission) =
                !PermissionPlan.applies(permission, Build.VERSION.SDK_INT) ||
                    context.checkSelfPermission(permission.manifestName) == PackageManager.PERMISSION_GRANTED
            val power = context.getSystemService(PowerManager::class.java)
            val activity = context.getSystemService(ActivityManager::class.java)
            return RecordingHealth(
                locationEnabled = context.getSystemService(LocationManager::class.java).isLocationEnabled,
                preciseLocation = granted(RecordingPermission.fineLocation),
                backgroundLocation = granted(RecordingPermission.backgroundLocation),
                activityRecognition = granted(RecordingPermission.activityRecognition),
                notifications = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled(),
                bluetoothConnect = granted(RecordingPermission.bluetoothConnect),
                ignoringBatteryOptimizations = power.isIgnoringBatteryOptimizations(context.packageName),
                backgroundRestricted = activity.isBackgroundRestricted,
                powerSave = power.isPowerSaveMode,
                oem = Oem.from(Build.MANUFACTURER, Build.BRAND),
                autoRecord = autoRecord,
                rememberedBackgroundRestriction = BackgroundRestrictionStore(context).read().reading,
                autostartDenied = MiuiAutostart.denied(context),
                standbyGps = FusedLocationSource.isAvailable(context),
            )
        }
    }
}

/**
 * Settings screens for each issue. Vendor screens are private and move between releases, so every
 * target is a list tried in order, ending with the app's own details page that always exists.
 */
object HealthSettings {
    fun targets(context: Context, issue: HealthIssue?, oem: Oem): List<Intent> {
        val pkg = context.packageName
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
        val vendor = when (oem) {
            Oem.samsung -> listOf(
                component("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                component("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            )
            Oem.xiaomi -> listOf(
                xiaomiBatterySettings(context),
                component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            )
            Oem.huawei -> listOf(
                component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            )
            Oem.other -> emptyList()
        }
        val specific = when (issue) {
            HealthIssue.locationOff -> listOf(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            HealthIssue.notificationsOff -> listOf(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg))
            HealthIssue.batteryOptimized -> listOf(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            HealthIssue.backgroundRestricted -> when (oem) {
                Oem.xiaomi -> listOf(xiaomiBatterySettings(context))
                else -> listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)))
            }
            HealthIssue.autoStartDisabled -> listOf(
                component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            )
            HealthIssue.powerSave -> listOf(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
            null -> vendor
            else -> emptyList()
        }
        return specific + details
    }

    /** Opens the first target this phone has. */
    fun open(context: Context, targets: List<Intent>): Boolean {
        for (intent in targets) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
                // Some vendor screens are not exported to other apps.
            }
        }
        return false
    }

    private fun component(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))

    private fun xiaomiBatterySettings(context: Context): Intent =
        component("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
            .putExtra("package_name", context.packageName)
            .putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager).toString())
}

enum class BackgroundRestrictionReading { unknown, restricted, free }

data class StoredBackgroundRestriction(
    val reading: BackgroundRestrictionReading,
    val readAtMillis: Long?,
    val assumed: Boolean,
)

class BackgroundRestrictionStore(context: Context) {
    private val preferences = context.getSharedPreferences("triptrack.health.background", Context.MODE_PRIVATE)

    fun read(): StoredBackgroundRestriction = StoredBackgroundRestriction(
        reading = preferences.getString(KEY_READING, null)?.let { value ->
            BackgroundRestrictionReading.entries.firstOrNull { it.name == value }
        } ?: BackgroundRestrictionReading.unknown,
        readAtMillis = preferences.getLong(KEY_READ_AT, -1).takeIf { it >= 0 },
        assumed = preferences.getBoolean(KEY_ASSUMED, false),
    )

    fun record(restricted: Boolean, atMillis: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putString(KEY_READING, if (restricted) BackgroundRestrictionReading.restricted.name else BackgroundRestrictionReading.free.name)
            .putLong(KEY_READ_AT, atMillis)
            .putBoolean(KEY_ASSUMED, false)
            .apply()
    }

    fun assumeFree(atMillis: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putString(KEY_READING, BackgroundRestrictionReading.free.name)
            .putLong(KEY_READ_AT, atMillis)
            .putBoolean(KEY_ASSUMED, true)
            .apply()
    }

    private companion object {
        const val KEY_READING = "reading"
        const val KEY_READ_AT = "readAt"
        const val KEY_ASSUMED = "assumed"
    }
}

/**
 * The record-screen card. MIUI restricts every new app and reports it only to an app off the screen,
 * so on Xiaomi a remembered background reading counts, and an unknown one counts as restricted.
 * With Google Play services the fused standby keeps a recording's GPS through MIUI's cut (bench
 * drive, 9 Nov 2026), so there the restriction matters only to auto-record, which also needs the
 * app to wake from a broadcast: a measured restriction or autostart off.
 */
fun shouldShowBackgroundGpsRestriction(
    oem: Oem,
    liveRestricted: Boolean,
    remembered: BackgroundRestrictionReading,
    autostartDenied: Boolean?,
    autoRecord: Boolean,
    standbyGps: Boolean,
): Boolean = liveRestricted || (oem == Oem.xiaomi && (
    (!standbyGps && remembered != BackgroundRestrictionReading.free) ||
        (autoRecord && (autostartDenied == true || remembered == BackgroundRestrictionReading.restricted))
))

object MiuiAutostart {
    private const val OP_AUTO_START = 10008
    private const val MODE_IGNORED = 1

    fun denied(context: Context): Boolean? {
        if (Oem.from(Build.MANUFACTURER, Build.BRAND) != Oem.xiaomi) return null
        return try {
            val appOps = context.getSystemService(AppOpsManager::class.java) ?: return null
            val check = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java,
            )
            check.invoke(appOps, OP_AUTO_START, Process.myUid(), context.packageName) == MODE_IGNORED
        } catch (_: Exception) {
            null
        } catch (_: LinkageError) {
            null
        }
    }
}

