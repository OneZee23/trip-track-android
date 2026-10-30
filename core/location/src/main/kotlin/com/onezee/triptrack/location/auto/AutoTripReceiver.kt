package com.onezee.triptrack.location.auto

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.onezee.triptrack.location.LocationGraphOwner
import kotlinx.coroutines.launch

/**
 * The background doors of auto-record, all in the manifest so they reach a dead process:
 * - activity transitions from Play services (IN_VEHICLE entered or left);
 * - the car stereo's Bluetooth connection (ACL and A2DP; both are exempt from the implicit
 *   broadcast ban, and need BLUETOOTH_CONNECT from Android 12);
 * - boot and app update, after which the transition registration must be renewed.
 *
 * The launch recovery runs before any event is handled (`awaitStarted`).
 */
open class AutoTripReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = (context.applicationContext as? LocationGraphOwner)?.locationGraph ?: return
        val action = intent.action ?: return
        // This receiver is exported for the system broadcasts; a transition is only believed when it
        // arrives at the private one, where only our own PendingIntent can deliver it.
        if (action == ActivityTransitions.ACTION_TRANSITION && this !is ActivityTransitionReceiver) return
        val transitions = if (action == ActivityTransitions.ACTION_TRANSITION) ActivityTransitions.vehicleTransitions(intent) else emptyList()
        val device = stereo(intent)
        val a2dpState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
        val pending = goAsync()
        graph.scope.launch {
            try {
                graph.awaitStarted()
                when (action) {
                    ActivityTransitions.ACTION_TRANSITION -> for (transition in transitions) {
                        if (transition.entered) graph.autoTrip.onAutomotiveDetected(transition.at) else graph.autoTrip.onAutomotiveEnded()
                    }
                    BluetoothDevice.ACTION_ACL_CONNECTED -> device?.let { graph.autoTrip.onStereoConnected(it.first, it.second) }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> device?.let { graph.autoTrip.onStereoDisconnected(it.first, it.second) }
                    BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> device?.let {
                        when (a2dpState) {
                            BluetoothProfile.STATE_CONNECTED -> graph.autoTrip.onStereoConnected(it.first, it.second)
                            BluetoothProfile.STATE_DISCONNECTED -> graph.autoTrip.onStereoDisconnected(it.first, it.second)
                        }
                    }
                    Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> graph.rearmAutoRecord()
                }
            } catch (failure: Exception) {
                Log.w(TAG, "auto-trip event $action failed", failure)
            } finally {
                pending.finish()
            }
        }
    }

    /** Address and name of the device in a Bluetooth broadcast; the name needs BLUETOOTH_CONNECT. */
    private fun stereo(intent: Intent): Pair<String?, String?>? {
        val device = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        } ?: return null
        val name = try {
            device.name
        } catch (denied: SecurityException) {
            null
        }
        return device.address to name
    }

    private companion object {
        const val TAG = "AutoTripReceiver"
    }
}

/** The non-exported receiver of the activity-transition PendingIntent. */
class ActivityTransitionReceiver : AutoTripReceiver()
