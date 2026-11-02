package com.onezee.triptrack.model

import org.junit.Test
import kotlin.test.assertEquals

// iOS: ReactionEmojiTests.swift. Prod trips carry legacy emoji (❤️ 🏎️ 🗺️) that must render as — and
// merge into — their canonical drawn-icon replacements without losing counts.
class ReactionEmojiTests {
    @Test fun canonPaletteIsSixDrawnIcons() {
        assertEquals(listOf("🔥", "🤯", "🏁", "🛣️", "🌅", "👍"), ReactionEmoji.all)
    }

    @Test fun legacyMapsToCanonical() {
        assertEquals("👍", ReactionEmoji.canonical("❤️"))
        assertEquals("🏁", ReactionEmoji.canonical("🏎️"))
        assertEquals("🛣️", ReactionEmoji.canonical("🗺️"))
    }

    @Test fun canonicalKeysPassThrough() {
        for (emoji in ReactionEmoji.all) assertEquals(emoji, ReactionEmoji.canonical(emoji))
    }

    @Test fun variationSelectorStrippedFormRecognized() {
        // Some client builds round-trip 🏎️/🗺️ without U+FE0F.
        assertEquals("🏁", ReactionEmoji.canonical("🏎"))
        assertEquals("🛣️", ReactionEmoji.canonical("🗺"))
        assertEquals("👍", ReactionEmoji.canonical("❤"))
    }

    @Test fun unknownEmojiPassesThrough() {
        assertEquals("🦄", ReactionEmoji.canonical("🦄"))
    }

    @Test fun mergedTalliesFoldsLegacyIntoCanonical() {
        val merged = ReactionEmoji.mergedTallies(listOf(ReactionTally("❤️", 2), ReactionTally("👍", 1), ReactionTally("🔥", 2)))
        assertEquals(2, merged.size)
        assertEquals(ReactionTally("👍", 3), merged.first())
        assertEquals(ReactionTally("🔥", 2), merged.last())
    }

    @Test fun mergedTalliesTieBreaksOnPaletteOrder() {
        val merged = ReactionEmoji.mergedTallies(listOf(ReactionTally("🌅", 1), ReactionTally("🔥", 1)))
        assertEquals(listOf("🔥", "🌅"), merged.map { it.emoji })
    }

    @Test fun mergedTalliesKeepsUnknownKeys() {
        assertEquals(listOf(ReactionTally("🦄", 4)), ReactionEmoji.mergedTallies(listOf(ReactionTally("🦄", 4))))
    }
}
