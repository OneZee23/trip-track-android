package com.onezee.triptrack.designsystem.components

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Scrolls a field's whole frame into view once it has the focus and the keyboard is up. A scroll
 * container that ends on the keyboard keeps the text itself in view, which leaves the frame around
 * it, its title and its bottom edge, cut by the keys: what iOS shows whole. The wait lets the
 * keyboard finish rising first, so the scroll lands against its final top.
 */
fun Modifier.revealOnFocus(): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    bringIntoViewRequester(requester).onFocusEvent { state ->
        if (state.hasFocus) scope.launch { delay(KEYBOARD_RISE_MS); requester.bringIntoView() }
    }
}

/** A little longer than the system's keyboard animation (about 250 ms on Android 11 and later). */
private const val KEYBOARD_RISE_MS = 350L
