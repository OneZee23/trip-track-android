package com.onezee.triptrack.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.R
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.theme.inter

/**
 * A person's avatar emoji drawn from TripTrack's own artwork, the same images the iPhone app ships, so a person looks the
 * same on both phones. iOS showed Apple's emoji and Android Google's, and Apple's artwork cannot ship on Android (owner,
 * 4 Nov 2026: «хочу чтобы на андроиде были такие же как на IOS… что там что там одинаковые»). The stored value stays
 * the emoji; one outside the set (a retired pick, anything an old client wrote) falls back to the system font.
 */
object AvatarArt {
    private val images = mapOf(
        "😎" to R.drawable.avatar_1f60e, "🙂" to R.drawable.avatar_1f642, "😄" to R.drawable.avatar_1f604,
        "🤠" to R.drawable.avatar_1f920, "🥸" to R.drawable.avatar_1f978, "🧔" to R.drawable.avatar_1f9d4,
        "👨‍🦳" to R.drawable.avatar_1f468_200d_1f9b3, "👩" to R.drawable.avatar_1f469,
        "👩‍🦰" to R.drawable.avatar_1f469_200d_1f9b0, "👱‍♀" to R.drawable.avatar_1f471_200d_2640,
        "👵" to R.drawable.avatar_1f475, "🐶" to R.drawable.avatar_1f436,
        // Retired from the picker, still worn (iOS MyProfileView.avatars).
        "🌅" to R.drawable.avatar_1f305, "🎸" to R.drawable.avatar_1f3b8, "📷" to R.drawable.avatar_1f4f7,
        "🎮" to R.drawable.avatar_1f3ae, "🚀" to R.drawable.avatar_1f680, "🚙" to R.drawable.avatar_1f699,
    )

    /** The variation selector is optional in stored values: «👱‍♀️» arrives with U+FE0F and without it. */
    fun image(emoji: String): Int? = images[emoji.replace("️", "")]

    val emojis: Set<String> get() = images.keys
}

/**
 * Where an avatar used to be `Text(emoji, style = inter(fontSize))`: the artwork in the box the emoji glyph took, or the
 * text itself for an emoji outside the set. The art fills 86 % of its square, as an emoji fills its em.
 */
@Composable
fun AvatarGlyph(emoji: String, fontSize: Float, modifier: Modifier = Modifier) {
    val image = AvatarArt.image(emoji)
    if (image == null) Text(emoji, modifier, style = inter(fontSize))
    else Image(painterResource(image), contentDescription = null, modifier.size((fontSize * GLYPH_SCALE).dp))
}

/** An emoji glyph is about 1.15 em wide; the art's subject is 86 % of its image. */
private const val GLYPH_SCALE = 1.15f / 0.86f
