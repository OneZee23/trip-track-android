// iOS: VehicleDetailView.stereoStatusCard (bluetoothDetector.isBluetoothOff, AudioRouteDetector.currentBluetoothOutputName)
package com.onezee.triptrack.feature.garage

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.onezee.triptrack.AppGraph
import com.onezee.triptrack.feature.settings.SettingsState
import com.onezee.triptrack.location.auto.AutoTripController
import com.onezee.triptrack.model.SavedBluetoothDevice
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the vehicle page says about this car's stereo, kept live: Bluetooth switched off, a stereo linked (and whether the phone
 * is playing through it right now), or none. «Connected» is read from the audio outputs, as auto-record itself keys off them: a
 * car stereo is a classic Bluetooth sink that scanning would never see. A stereo that is Android Auto does not need Bluetooth, so
 * Bluetooth being off says nothing about it.
 */
@Composable
internal fun rememberStereoStatus(graph: AppGraph, settings: SettingsState, vehicleId: UUID): StereoStatus {
    val context = LocalContext.current
    var bluetoothOn by remember { mutableStateOf(bluetoothEnabled(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) bluetoothOn = bluetoothEnabled(context) }
        val receiver = object : BroadcastReceiver() { override fun onReceive(c: Context?, intent: Intent?) { bluetoothOn = bluetoothEnabled(context) } }
        owner.lifecycle.addObserver(observer)
        context.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        onDispose { owner.lifecycle.removeObserver(observer); context.unregisterReceiver(receiver) }
    }
    val outputs by produceState<List<SavedBluetoothDevice>>(emptyList(), graph) {
        graph.location.carStereos.changes.collect { value = withContext(Dispatchers.IO) { graph.location.carStereos.outputs() } }
    }
    return stereoStatus(bluetoothOn, settings.stereos.firstOrNull { it.vehicleId == vehicleId }, outputs)
}

/**
 * The three faces of the status card. Bluetooth off comes first and covers a vehicle with no stereo too, as iOS checks the radio
 * before it looks at the car; Android Auto is the one link that needs no Bluetooth, so it never reads as «off». A linked stereo is
 * «connected» when the phone plays through its output now, matched by the id without regard to case.
 */
internal fun stereoStatus(bluetoothOn: Boolean, linked: SavedBluetoothDevice?, outputs: List<SavedBluetoothDevice>): StereoStatus = when {
    !bluetoothOn && linked?.uuid != AutoTripController.ANDROID_AUTO -> StereoStatus.BluetoothOff
    linked != null -> StereoStatus.Linked(linked.name, outputs.any { it.uuid.equals(linked.uuid, ignoreCase = true) })
    else -> StereoStatus.NotLinked
}

private fun bluetoothEnabled(context: Context): Boolean =
    try { context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled != false } catch (_: SecurityException) { true }
