// iOS: #if DEBUG. Release never reads seed or road-time arguments.
package com.onezee.triptrack.feature.me

import android.content.Intent
import com.onezee.triptrack.AppGraph

object MeLaunch {
    fun apply(graph: AppGraph, intent: Intent) = Unit
}
