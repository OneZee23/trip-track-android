package com.onezee.triptrack.debug

import android.app.AppOpsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import com.onezee.triptrack.location.LocationGraphOwner
import com.onezee.triptrack.location.permissions.RecordingHealth
import com.onezee.triptrack.location.recording.PauseSource
import kotlinx.coroutines.launch

/**
 * Debug builds only: drives a recording from adb, so a bench test can run on a phone that stays
 * locked (the screen-off minute is the test). Only the shell can send it: the receiver asks for
 * DUMP, a permission apps never get.
 *
 * adb shell am broadcast -n com.onezee.triptrack.dev/com.onezee.triptrack.debug.BenchRecordingReceiver -a start
 * (start | pause | resume | stop | health)
 */
class BenchRecordingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = (context.applicationContext as? LocationGraphOwner)?.locationGraph ?: return
        val action = intent.action ?: return
        val pending = goAsync()
        graph.scope.launch {
            try {
                val result = when (action) {
                    "start" -> graph.startRecording()
                    "pause" -> graph.recorder.setPaused(true, PauseSource.notification)
                    "resume" -> graph.recorder.setPaused(false, PauseSource.notification)
                    "stop" -> graph.stopRecording()
                    // What the phone tells the app about its own restrictions, read from the background.
                    "health" -> "${RecordingHealth.read(context, autoRecord = true)} miuiAutostartOp=${miuiAutostart(context)}"
                    else -> "unknown action"
                }
                Log.i(TAG, "$action -> $result")
            } catch (failure: Exception) {
                Log.w(TAG, "$action failed", failure)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BenchRecording"

        /** MIUI's autostart switch is app op 10008; 0 allowed, 1 ignored. Hidden API, so it may fail. */
        fun miuiAutostart(context: Context): String = runCatching {
            val method = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java,
            )
            method.invoke(context.getSystemService(AppOpsManager::class.java), 10008, Process.myUid(), context.packageName).toString()
        }.getOrElse { it.toString() }
    }
}
