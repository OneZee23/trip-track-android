package com.onezee.triptrack.logic

import java.text.Normalizer
import java.util.Locale

// iOS: ContentFilter.swift (sanitize and validate for .comment)
object CommentContentRules {
    const val maxLength = 500
    // iOS: ContentFilter.swift (validation result before AppStrings resolution)
    enum class Failure { tooLong, inappropriateLanguage }
    private val denylist = setOf("nigger", "nigga", "faggot", "chink", "spic", "kike", "tranny", "retard",
        "raghead", "kyke", "wetback", "beaner", "пидор", "пидорас", "пидарас", "нигер", "жид", "хач", "чурка", "даун", "педик")

    fun sanitize(text: String): String {
        val stripped = StringBuilder()
        text.codePoints().forEach { code ->
            if (code !in 0x200B..0x200F && code !in 0x202A..0x202E && code !in 0x2060..0x206F && code != 0xFEFF && code != 0x180E) stripped.appendCodePoint(code)
        }
        return stripped.toString().split(Regex("(?U)\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
    }
    fun containsObjectionable(text: String): Boolean {
        val normalized = Normalizer.normalize(text.lowercase(Locale.US), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return denylist.any { it in normalized }
    }
    fun validate(text: String): Failure? {
        val cleaned = sanitize(text)
        if (cleaned.isEmpty()) return null
        if (Regex("\\X").findAll(cleaned).take(maxLength + 1).count() > maxLength) return Failure.tooLong
        return Failure.inappropriateLanguage.takeIf { containsObjectionable(cleaned) }
    }
}
