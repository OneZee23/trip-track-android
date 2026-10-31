// iOS: VehicleModTags.swift, Drive2Link.swift
package com.onezee.triptrack.logic

import com.onezee.triptrack.model.VehicleCardStyle
import com.onezee.triptrack.model.VehicleFinish
import com.onezee.triptrack.model.VehicleType
import com.onezee.triptrack.model.serialization.ModelJson
import java.net.URI
import java.net.URISyntaxException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/** iOS: VehicleModTags. Three tags of at most 24 characters, trimmed and without repeats. */
object VehicleModTags {
    private val strings = ListSerializer(String.serializer())

    fun sanitized(input: List<String>): List<String> {
        val result = mutableListOf<String>()
        for (value in input) {
            val tag = prefixCharacters(value.trim(), 24)
            if (tag.isNotEmpty() && !result.contains(tag)) result.add(tag)
            if (result.size == 3) break
        }
        return result
    }

    fun decodeJSON(json: String?): List<String> {
        if (json == null) return emptyList()
        return try {
            ModelJson.storage.decodeFromString(strings, json)
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    /** An empty list is written as `[]`, not NULL: on the wire that is an explicit clear. */
    fun encodeJSON(tags: List<String>): String = ModelJson.storage.encodeToString(strings, sanitized(tags))

    // Swift prefixes grapheme clusters, including emoji modifiers and joined families.
    private fun prefixCharacters(text: String, count: Int): String =
        Regex("\\X").findAll(text).take(count).joinToString("") { it.value }
}

/**
 * iOS: Drive2Link.init?(_:). Only http(s) DRIVE2 logbook links: the five known hosts, no user, no
 * port, `/r/<make>/<model>/<digits>/` and anything after it. Query and fragment are dropped and the
 * link is rebuilt as `https://www.drive2.ru|com/r/<make>/<model>/<id>/`, at most 200 characters.
 */
object Drive2Link {
    private val prefix = Regex("^https?://[^/?#:]+(?:[/?#]|$)", RegexOption.IGNORE_CASE)
    private val path = Regex("^/r/[a-z0-9_-]+/[a-z0-9_-]+/[0-9]+(?:/.*)?$")
    private val hosts = setOf("drive2.ru", "www.drive2.ru", "m.drive2.ru", "drive2.com", "www.drive2.com")

    fun normalized(input: String): String? {
        val text = input.trim()
        if (!prefix.containsMatchIn(text)) return null
        val uri = try {
            URI(text)
        } catch (_: URISyntaxException) {
            return null
        }
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null
        if (host !in hosts) return null
        if (uri.rawUserInfo != null || uri.port != -1) return null
        val rawPath = uri.rawPath ?: return null
        if (!path.matches(rawPath)) return null
        val parts = rawPath.split("/").filter { it.isNotEmpty() }
        val normalizedHost = if (host.endsWith(".com")) "www.drive2.com" else "www.drive2.ru"
        val result = "https://$normalizedHost/r/${parts[1]}/${parts[2]}/${parts[3]}/"
        return result.takeIf { it.length <= 200 }
    }
}

// iOS: VehicleFinish.swift (parse), VehicleType.swift (hasPlate).
fun VehicleFinish.Companion.parse(raw: String?): VehicleFinish? = raw?.let(VehicleFinish::fromRawValue)
val VehicleType.hasPlate: Boolean get() = this == VehicleType.car || this == VehicleType.moto

// iOS: VehicleCardStyle.swift, VehicleFinish.swift, VehicleModTags.swift. Display fallbacks never erase a saved choice.
fun VehicleCardStyle.Companion.effective(raw: String?, isPlus: Boolean): VehicleCardStyle =
    if (isPlus) raw?.let { fromRawValue(it) } ?: VehicleCardStyle.none else VehicleCardStyle.none

fun VehicleFinish.Companion.effective(raw: String?, isPlus: Boolean): VehicleFinish =
    (parse(raw) ?: VehicleFinish.gloss).let { if (it != VehicleFinish.gloss && !isPlus) VehicleFinish.gloss else it }

fun VehicleModTags.effective(tags: List<String>, isPlus: Boolean): List<String> = if (isPlus) sanitized(tags) else emptyList()
