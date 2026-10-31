// iOS: AccountSyncComponents.swift (AccountFormat), RandomDisplayName.swift, ContentFilter.swift
// (sanitize, clampedDisplayName), ProfileSyncLatch.swift
package com.onezee.triptrack.feature.account

import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.feature.settings.PresentationPreferences
import kotlin.random.Random

// iOS: AccountFormat.
object AccountFormat {
    /**
     * «a•••@gmail.com»: the first character of the local part, a bullet mask and the full domain.
     * Pure and total: degenerate input (empty, no `@`, `@` first, one-character local part) returns
     * safely and never echoes the full address back.
     */
    fun maskedEmail(email: String): String {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return ""
        // Swift's `first` is a grapheme cluster: «юзер@почта.рф» keeps its «ю», an emoji stays whole.
        val first = Regex("\\X").find(trimmed)!!.value
        val at = trimmed.indexOf('@')
        if (at < 0) return "$first•••"
        val domain = trimmed.substring(at + 1)
        if (at == 0) return "•••@$domain"
        return "$first•••@$domain"
    }
}

// iOS: ContentFilter.sanitize / clampedDisplayName.
object DisplayNames {
    /** The server's `displayName` limit; an over-long name made it refuse the whole profile push. */
    const val maxLength = 30

    /**
     * Strips zero-width, bidi-control and other invisible characters (they let a name look like
     * another one), then collapses whitespace runs, NBSP included, to single spaces.
     */
    fun sanitize(text: String): String {
        val stripped = buildString {
            var index = 0
            while (index < text.length) {
                val code = text.codePointAt(index)
                val invisible = code in 0x200B..0x200F || code in 0x202A..0x202E || code in 0x2060..0x206F ||
                    code == 0xFEFF || code == 0x180E
                if (!invisible) appendCodePoint(code)
                index += Character.charCount(code)
            }
        }
        return stripped.split(Regex("[\\s\\u00A0\\u2028\\u2029\\u202F\\u205F\\u3000]+")).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /**
     * Sanitized first, so invisible padding cannot spend the budget. Counted in grapheme clusters
     * like Swift's `prefix`, the same `\X` rule `VehicleModTags` uses, so a flag is never split.
     */
    fun clamped(raw: String): String = Regex("\\X").findAll(sanitize(raw)).take(maxLength).joinToString("") { it.value }
}

/**
 * iOS: RandomDisplayName. A humane placeholder («Быстрый Путешественник 42») for an account that
 * arrives without any name; the person can rename themselves later.
 */
object RandomDisplayName {
    fun generate(language: AppLanguage, random: Random = Random.Default): String {
        val pool = if (language == AppLanguage.ru) ruPool else enPool
        return "${pool.adjectives.random(random)} ${pool.nouns.random(random)} ${random.nextInt(10, 10_000)}"
    }

    /** Three tokens, the last an integer, the first one of our adjectives. */
    fun isPlaceholder(name: String?): Boolean {
        if (name.isNullOrEmpty()) return false
        val parts = name.trim().split(' ').filter { it.isNotEmpty() }
        if (parts.size != 3 || parts[2].toIntOrNull() == null) return false
        return parts[0] in ruPool.adjectives || parts[0] in enPool.adjectives
    }

    private class Pool(val adjectives: List<String>, val nouns: List<String>)

    private val ruPool = Pool(
        listOf("Быстрый", "Смелый", "Ловкий", "Дерзкий", "Хитрый", "Зоркий", "Мудрый", "Шустрый", "Тихий", "Лихой",
            "Гордый", "Светлый", "Бодрый", "Крепкий", "Острый"),
        listOf("Водитель", "Путешественник", "Странник", "Кочевник", "Гонщик", "Картограф", "Навигатор", "Путник",
            "Турист", "Капитан", "Исследователь", "Дрифтер", "Райдер", "Покоритель", "Скиталец"),
    )

    private val enPool = Pool(
        listOf("Swift", "Bold", "Nimble", "Daring", "Sly", "Sharp", "Wise", "Quick", "Quiet", "Wild",
            "Steady", "Bright", "Lucky", "Cool", "Brave"),
        listOf("Driver", "Traveler", "Wanderer", "Nomad", "Racer", "Mapper", "Navigator", "Voyager", "Tourist", "Captain",
            "Explorer", "Drifter", "Rider", "Roamer", "Pathfinder"),
    )
}

/**
 * iOS: ProfileSyncLatch. Whether the SERVER acknowledged a profile push. `account.display_name`
 * has one writer, `POST /auth/profile-update`, and the server validates the request as a whole: one
 * rejected field and the name is gone. An absent record means «not confirmed», never «done».
 */
class ProfileSyncLatch(private val preferences: PresentationPreferences) {
    fun markConfirmed() = preferences.set(KEY, "true")
    fun markUnconfirmed() = preferences.set(KEY, "false")

    /** Sign-out: the next account on this phone inherits nothing, or its first push is swallowed. */
    fun reset() = preferences.set(KEY, null)

    fun needsPush(isSignedIn: Boolean, localName: String?): Boolean {
        if (!isSignedIn) return false
        if (localName.isNullOrBlank()) return true
        return preferences.string(KEY) != "true"
    }

    companion object {
        const val KEY = "com.triptrack.profile.syncConfirmed"
    }
}
