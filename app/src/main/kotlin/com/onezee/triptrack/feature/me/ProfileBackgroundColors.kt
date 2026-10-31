// iOS: ProfileBackground.swift (free gradient definitions)
package com.onezee.triptrack.feature.me

import androidx.compose.ui.graphics.Color
import com.onezee.triptrack.model.ProfileBackground
import com.onezee.triptrack.model.PlusFeature
import com.onezee.triptrack.model.PlusAccessLevel
import com.onezee.triptrack.logic.PlusGate

// iOS: PlusGate. Android launch has no purchase storefront or entitlement provider.
internal object MePlusGate {
    fun access(feature: PlusFeature) = PlusGate.allows(feature, isPlus = false, storefrontHidesPlus = true)
    val backgrounds get() = ProfileBackground.entries.filter { !it.rawValue.startsWith("plus_") || access(PlusFeature.profileBackgrounds) != PlusAccessLevel.hidden }
}

// iOS: ProfileBackground.effective. Saved premium choices survive; launch storefront shows the ordinary card.
internal fun ProfileBackground.effective(isPlus: Boolean): ProfileBackground = if (rawValue.startsWith("plus_") && !isPlus) ProfileBackground.none else this

internal fun ProfileBackground.colors(): List<Color> = when (this) {
    ProfileBackground.sunset -> listOf(Color(.98f, .55f, .24f), Color(.95f, .31f, .46f), Color(.56f, .22f, .54f))
    ProfileBackground.ocean -> listOf(Color(.25f, .55f, .85f), Color(.14f, .37f, .67f), Color(.08f, .22f, .46f))
    ProfileBackground.forest -> listOf(Color(.32f, .59f, .36f), Color(.19f, .42f, .28f), Color(.10f, .25f, .18f))
    ProfileBackground.mountain -> listOf(Color(.55f, .62f, .70f), Color(.32f, .42f, .52f), Color(.16f, .22f, .30f))
    ProfileBackground.midnight -> listOf(Color(.18f, .15f, .38f), Color(.09f, .08f, .22f), Color(.03f, .02f, .08f))
    ProfileBackground.dawn -> listOf(Color(.99f, .85f, .62f), Color(.98f, .65f, .48f), Color(.76f, .42f, .47f))
    ProfileBackground.copper -> listOf(Color(.72f, .44f, .28f), Color(.48f, .27f, .17f), Color(.26f, .13f, .08f))
    ProfileBackground.slate -> listOf(Color(.42f, .47f, .52f), Color(.27f, .31f, .36f), Color(.14f, .16f, .20f))
    ProfileBackground.aurora -> listOf(Color(.24f, .85f, .66f), Color(.32f, .44f, .82f), Color(.56f, .28f, .74f))
    ProfileBackground.sand -> listOf(Color(.95f, .86f, .70f), Color(.84f, .70f, .49f), Color(.55f, .40f, .24f))
    else -> emptyList()
}
