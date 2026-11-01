package com.onezee.triptrack.ui

import androidx.compose.runtime.staticCompositionLocalOf
import java.time.Clock

/**
 * The wall clock screens read «today» and «now» from. Screenshot tests provide a fixed one: the
 * history calendar shows the current week and month, and its picture would otherwise change every
 * Monday and on the first of every month.
 */
val LocalClock = staticCompositionLocalOf<Clock> { Clock.systemUTC() }
