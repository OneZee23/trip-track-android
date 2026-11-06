// iOS: ProfileBackground.swift (gradient definitions and display names)
package com.onezee.triptrack.feature.me

import androidx.compose.ui.graphics.Color
import com.onezee.triptrack.model.ProfileBackground
import com.onezee.triptrack.model.PlusFeature
import com.onezee.triptrack.model.PlusAccessLevel
import com.onezee.triptrack.logic.PlusGate
import com.onezee.triptrack.designsystem.strings.AppStrings

// iOS: PlusGate.swift. Android's promo entitlement opens the same feature gate.
internal object MePlusGate {
    fun access(feature: PlusFeature, isPlus: Boolean) = PlusGate.allows(feature, isPlus = isPlus, storefrontHidesPlus = true)
    fun backgrounds(isPlus: Boolean) = ProfileBackground.entries.filter { !it.rawValue.startsWith("plus_") || access(PlusFeature.profileBackgrounds, isPlus) != PlusAccessLevel.hidden }
}

// iOS: ProfileBackground.effective. Saved premium choices remain stored and return when PRO is active again.
internal fun ProfileBackground.effective(isPlus: Boolean): ProfileBackground = if (rawValue.startsWith("plus_") && !isPlus) ProfileBackground.none else this

internal fun ProfileBackground.colors(): List<Color> = when (this) {
    ProfileBackground.none -> emptyList()
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
    ProfileBackground.plusNebula -> listOf(Color(.11f, .07f, .24f), Color(.36f, .14f, .53f), Color(.12f, .32f, .62f))
    ProfileBackground.plusLava -> listOf(Color(.88f, .36f, .13f), Color(.55f, .13f, .10f), Color(.16f, .05f, .06f))
    ProfileBackground.plusGlacier -> listOf(Color(.76f, .91f, .95f), Color(.42f, .68f, .84f), Color(.16f, .34f, .54f))
    ProfileBackground.plusNeon -> listOf(Color(.08f, .06f, .16f), Color(.16f, .86f, .78f), Color(.55f, .24f, .92f))
    ProfileBackground.plusCarbon -> listOf(Color(.16f, .17f, .20f), Color(.09f, .10f, .12f), Color(.04f, .04f, .05f))
    ProfileBackground.plusGold -> listOf(Color(.99f, .87f, .53f), Color(.78f, .57f, .18f), Color(.34f, .22f, .06f))
    ProfileBackground.plusTropic -> listOf(Color(.16f, .72f, .62f), Color(.13f, .50f, .47f), Color(.96f, .76f, .35f))
    ProfileBackground.plusStorm -> listOf(Color(.15f, .18f, .24f), Color(.28f, .34f, .42f), Color(.08f, .10f, .13f))
}

internal val ProfileBackground.displayName: String
    get() = when (this) {
        ProfileBackground.none -> ""
        ProfileBackground.sunset -> "Sunset"
        ProfileBackground.ocean -> "Ocean"
        ProfileBackground.forest -> "Forest"
        ProfileBackground.mountain -> "Mountain"
        ProfileBackground.midnight -> "Midnight"
        ProfileBackground.dawn -> "Dawn"
        ProfileBackground.copper -> "Copper"
        ProfileBackground.slate -> "Slate"
        ProfileBackground.aurora -> "Aurora"
        ProfileBackground.sand -> "Sand"
        ProfileBackground.plusNebula -> "Nebula"
        ProfileBackground.plusLava -> "Lava"
        ProfileBackground.plusGlacier -> "Glacier"
        ProfileBackground.plusNeon -> "Neon"
        ProfileBackground.plusCarbon -> "Carbon"
        ProfileBackground.plusGold -> "Gold"
        ProfileBackground.plusTropic -> "Tropic"
        ProfileBackground.plusStorm -> "Storm"
    }

internal fun ProfileBackground.displayNameOrDefault(strings: AppStrings): String =
    if (this == ProfileBackground.none) strings.cosmeticDefaultOption() else displayName
