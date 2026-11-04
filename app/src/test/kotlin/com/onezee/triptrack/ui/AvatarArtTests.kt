package com.onezee.triptrack.ui

import com.onezee.triptrack.feature.me.ProfileIdentityChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The avatar artwork that replaces the platform emoji, the same set as the iPhone's (`AvatarArt`). */
class AvatarArtTests {
    @Test
    fun everyPickableAvatarHasArt() {
        for (emoji in ProfileIdentityChoices.avatars) assertNotNull(emoji, AvatarArt.image(emoji))
    }

    /** iOS `MyProfileView.avatars` retired these from the picker; people still wear them. */
    @Test
    fun retiredAvatarsStillHaveArt() {
        for (emoji in listOf("🌅", "🎸", "📷", "🎮", "🚀", "🚙")) assertNotNull(emoji, AvatarArt.image(emoji))
    }

    @Test
    fun theVariationSelectorIsOptional() {
        assertEquals(AvatarArt.image("👱‍♀️"), AvatarArt.image("👱‍♀"))
        assertNotNull(AvatarArt.image("👱‍♀️"))
    }

    /** Anything else keeps the font: a car on an old account, a club's emoji in the inbox. */
    @Test
    fun otherEmojiFallBackToTheFont() {
        assertNull(AvatarArt.image("🚗"))
        assertNull(AvatarArt.image("🏅"))
    }

    @Test
    fun theSetIsTheIPhonesEighteen() {
        assertEquals(18, AvatarArt.emojis.size)
    }
}
