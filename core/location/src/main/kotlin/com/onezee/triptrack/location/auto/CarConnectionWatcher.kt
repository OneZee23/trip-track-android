// iOS: AudioRouteDetector.swift
package com.onezee.triptrack.location.auto

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.util.Log

/**
 * iOS: AudioRouteDetector.swift (.carAudio). Android Auto announces itself through the protocol of the Android for
 * Cars library's `CarConnection`: a content provider that answers the connection type, and a
 * broadcast when it changes. This reads that protocol directly; the library would bring Guava, media
 * and lifecycle along for one query.
 *
 * The broadcast reaches only a registered receiver, so this is an extra signal while the process
 * lives (a recording, the app on screen); a car connection with the process dead is caught by the
 * stereo's Bluetooth connection and by activity recognition instead.
 */
class CarConnectionWatcher(
    private val context: Context,
    private val onChange: (connected: Boolean) -> Unit,
) {
    private var receiver: BroadcastReceiver? = null
    private var lastConnected: Boolean? = null

    @SuppressLint("InlinedApi")
    @Synchronized
    fun start() {
        if (receiver != null) return
        val created = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = publish()
        }
        receiver = created
        // Exported: Android Auto is another app. Before Android 13 the flag is ignored and a
        // dynamic receiver is exported anyway.
        context.registerReceiver(created, IntentFilter(ACTION_CAR_CONNECTION_UPDATED), Context.RECEIVER_EXPORTED)
        publish()
    }

    @Synchronized
    fun stop() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
        lastConnected = null
    }

    /** NOT_CONNECTED (0), NATIVE (1) or PROJECTION (2); any failure reads as not connected. */
    fun connectionType(): Int = try {
        context.contentResolver.query(CONNECTION_URI, arrayOf(CAR_CONNECTION_STATE), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(CAR_CONNECTION_STATE)
            if (column < 0 || !cursor.moveToNext()) CONNECTION_TYPE_NOT_CONNECTED else cursor.getInt(column)
        } ?: CONNECTION_TYPE_NOT_CONNECTED
    } catch (failure: Exception) {
        Log.d(TAG, "car connection query failed", failure)
        CONNECTION_TYPE_NOT_CONNECTED
    }

    @Synchronized
    private fun publish() {
        val connected = isConnected(connectionType())
        if (connected == lastConnected) return
        val first = lastConnected == null
        lastConnected = connected
        // The first answer only sets the baseline unless a car is already there.
        if (!first || connected) onChange(connected)
    }

    companion object {
        // The constants of androidx.car.app.connection.CarConnection.
        const val ACTION_CAR_CONNECTION_UPDATED = "androidx.car.app.connection.action.CAR_CONNECTION_UPDATED"
        const val CAR_CONNECTION_STATE = "CarConnectionState"
        const val CONNECTION_TYPE_NOT_CONNECTED = 0
        const val CONNECTION_TYPE_NATIVE = 1
        const val CONNECTION_TYPE_PROJECTION = 2
        fun isConnected(type: Int): Boolean = type == CONNECTION_TYPE_NATIVE || type == CONNECTION_TYPE_PROJECTION
        val CONNECTION_URI: Uri = Uri.Builder().scheme("content").authority("androidx.car.app.connection").build()
        private const val TAG = "CarConnectionWatcher"
    }
}
