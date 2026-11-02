// iOS: SocialDTOs.swift (ReactionEmoji)
package com.onezee.triptrack.model

/**
 * iOS: SocialDTOs.swift (ReactionEmoji). The emoji IS the wire key; the drawn icon is only how it
 * renders. The six canon keys in palette order — Огонь, Вау, Финиш, Перевал, Кадр, Класс — and the
 * pre-6.1 keys still stored on prod trips, each of which renders as (and merges into) its canonical
 * replacement, so no reaction is lost and no lookalike pill appears.
 */
object ReactionEmoji {
    val all: List<String> = listOf("🔥", "🤯", "🏁", "🛣️", "🌅", "👍")

    /** ❤️ love → 👍 like, 🏎️ fast → 🏁 finish, 🗺️ places → 🛣️ road. Never offered in the picker again. */
    val legacyToCanonical: Map<String, String> = mapOf("❤️" to "👍", "🏎️" to "🏁", "🗺️" to "🛣️")

    private const val VARIATION_SELECTOR = "️"

    /**
     * The canonical display key for any stored emoji, robust to U+FE0F: 🏎️/🗺️ round-trip through the
     * server with or without it depending on the client build. Android also folds a palette key that
     * lost its selector (`🛣` → `🛣️`); iOS leaves that one raw. An unknown key passes through.
     */
    fun canonical(emoji: String): String {
        legacyToCanonical[emoji]?.let { return it }
        val bare = emoji.replace(VARIATION_SELECTOR, "")
        all.firstOrNull { it.replace(VARIATION_SELECTOR, "") == bare }?.let { return it }
        return legacyToCanonical.entries.firstOrNull { it.key.replace(VARIATION_SELECTOR, "") == bare }?.value ?: emoji
    }

    /**
     * Server breakdown → display tallies: legacy keys folded into their canonical replacement (counts
     * summed), most first. Ties break on the palette order so the pill row is stable across refreshes
     * instead of hopping with map order; keys outside the palette come last.
     */
    fun mergedTallies(breakdown: List<ReactionTally>): List<ReactionTally> {
        val sums = LinkedHashMap<String, Int>()
        for (tally in breakdown) {
            val key = canonical(tally.emoji)
            sums[key] = (sums[key] ?: 0) + tally.count
        }
        return sums.entries.filter { it.value > 0 }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { all.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } })
            .map { ReactionTally(it.key, it.value) }
    }
}
