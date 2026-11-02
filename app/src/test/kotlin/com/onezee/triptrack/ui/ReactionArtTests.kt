// iOS: ReactionIconView.swift (assetBases), ReactionEmojiTests.swift, Assets.xcassets reaction_*.
package com.onezee.triptrack.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.drawable.VectorDrawable
import com.onezee.triptrack.model.ReactionEmoji
import com.onezee.triptrack.ui.reactions.ReactionArt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every key the server can send draws the owner's icon: the six canon keys, the three legacy ones
 * with and without U+FE0F, and each of them in both states. Only a key no build knows falls back to
 * the emoji. The drawings are checked as drawings: a 24 dp vector each, and an outline that leaves
 * most of its box empty where the filled variant covers it (a lost `fill="none"` would turn every
 * outline into a solid blob and still pass a name check).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReactionArtTests {
    private val app = RuntimeEnvironment.getApplication()
    private val selector = "️"

    private val everyKey: List<String> = (ReactionEmoji.all + ReactionEmoji.legacyToCanonical.keys).flatMap { listOf(it, it.replace(selector, "")) }.distinct()

    @Test fun everyReactionKeyResolvesToItsDrawnIcon() {
        val expected = mapOf("🔥" to "fire", "🤯" to "wow", "🏁" to "finish", "🛣️" to "pass", "🌅" to "frame", "👍" to "like")
        for (key in everyKey) {
            val base = assertNotNull(ReactionArt.base(key), "no drawing for $key")
            assertEquals(expected.getValue(ReactionEmoji.canonical(key)), base, key)
            val art = assertNotNull(ReactionArt.of(key), key)
            assertEquals("reaction_${base}_outline", app.resources.getResourceEntryName(art.outline), key)
            assertEquals("reaction_${base}_fill", app.resources.getResourceEntryName(art.fill), key)
        }
        assertEquals(setOf("fire", "wow", "finish", "pass", "frame", "like"), ReactionEmoji.all.mapNotNull(ReactionArt::base).toSet())
        assertEquals("like", ReactionArt.base("❤️"))
        assertEquals("finish", ReactionArt.base("🏎"))
        assertEquals("pass", ReactionArt.base("🗺"))
    }

    @Test fun anUnknownKeyHasNoDrawing() {
        assertNull(ReactionArt.of("🦄"))
        assertNull(ReactionArt.of(""))
    }

    @Test fun theDrawingsAre24dpTemplatesWithAnOutlineAndAFill() {
        val side = (24 * app.resources.displayMetrics.density).toInt()
        for (emoji in ReactionEmoji.all) {
            val art = assertNotNull(ReactionArt.of(emoji))
            val outline = coverage(art.outline)
            val fill = coverage(art.fill)
            assertTrue(outline > 0.05, "$emoji outline draws nothing: $outline")
            assertTrue(fill > outline * 1.3, "$emoji: the filled drawing ($fill) must cover more than the outline ($outline)")
            for (id in listOf(art.outline, art.fill)) {
                val drawable = app.getDrawable(id)
                assertTrue(drawable is VectorDrawable, "$emoji is not a vector")
                assertEquals(side, drawable.intrinsicWidth)
                assertEquals(side, drawable.intrinsicHeight)
            }
            assertNotEquals(art.outline, art.fill)
        }
    }

    /** The share of a 96 px box the drawing covers once tinted, as the app draws it (SrcIn). */
    private fun coverage(id: Int): Double {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val drawable = app.getDrawable(id)!!.mutate()
        drawable.setTint(android.graphics.Color.BLACK)
        drawable.setTintMode(PorterDuff.Mode.SRC_IN)
        drawable.setBounds(0, 0, 96, 96)
        drawable.draw(Canvas(bitmap))
        val pixels = IntArray(96 * 96).also { bitmap.getPixels(it, 0, 96, 0, 0, 96, 96) }
        return pixels.sumOf { (it ushr 24) / 255.0 } / pixels.size
    }
}
