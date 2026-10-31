// iOS: ShareLinkPresenter.swift (LinkActivityItem), the «Скопировать ссылку» actions.
package com.onezee.triptrack.feature.social

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * The one way a link reaches the system share sheet. iOS had to wait for a presentable controller
 * and declare the item a URL so «Copy» copied the link; on Android the chooser is an activity of its
 * own, and a plain-text item with the URL is what every target (Copy included) reads.
 */
object ShareLinks {
    fun share(context: Context, url: String, title: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
            // The subject line for mail, the preview title in the chooser.
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TITLE, title)
        }
        val chooser = Intent.createChooser(send, title)
        if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /**
     * Puts [url] on the clipboard. Returns whether the app should confirm it with its own toast:
     * from Android 13 the system shows the copied content itself, and a second confirmation on top
     * is what the platform asks apps not to draw.
     */
    fun copy(context: Context, url: String, label: String): Boolean {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, url))
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    }
}
