package com.onezee.triptrack.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.onezee.triptrack.model.DistanceUnit

/**
 * iOS `@Environment(\.distanceUnit)`. Screens read the person's unit from here and hand it to
 * `Measure`, which takes it as a required argument: a place that shows a distance cannot be built
 * without naming the unit. The shell provides the stored choice; the default is kilometres.
 */
val LocalDistanceUnit = staticCompositionLocalOf { DistanceUnit.km }
