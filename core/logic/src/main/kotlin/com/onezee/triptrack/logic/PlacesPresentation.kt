package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.text.Collator
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

// iOS: PlacesPresentation.swift (PlacesSort). The settings adapter persists this key and rawValue.
val PlacesSort.Companion.defaultsKey: String get() = "places.sort"
fun PlacesSort.Companion.load(rawValue: String?): PlacesSort = rawValue?.let { fromRawValue(it) } ?: PlacesSort.recent

// iOS: PlacesPresentation.swift
val PlacesPresentation.manyPlaces: Int get() = 8
val PlacesPresentation.Group.id: String get() = kind.rawValue

fun PlacesPresentation.filter(items: List<PlaceListItem>, query: String, locale: Locale): List<PlaceListItem> {
    val term = query.trim { it.isFoundationWhitespace(includeNewlines = true) }
    if (term.isEmpty()) return items
    fun folded(text: String): String {
        // A case-insensitive search also matches multi-letter case folds such as Straße / STRASSE.
        val caseFolded = buildString {
            for (char in text.lowercase(locale)) {
                val expanded = char.uppercase(locale)
                when {
                    expanded.length > 1 -> append(expanded.lowercase(locale))
                    char == '\u03c2' -> append('\u03c3')
                    else -> append(char)
                }
            }
        }
        return Normalizer.normalize(caseFolded, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    }
    val needle = folded(term)
    return items.filter { it.place.name?.takeIf { name -> name.isNotEmpty() }?.let { name -> folded(name).contains(needle) } == true }
}

fun PlacesPresentation.sort(
    items: List<PlaceListItem>, by: PlacesSort, locale: Locale = Locale.getDefault(),
): List<PlaceListItem> {
    val recent = PlaceListItem.sorted(items)
    return when (by) {
        PlacesSort.recent -> recent
        PlacesSort.frequent -> recent.sortedByDescending { it.stats.passCount }
        PlacesSort.byName -> {
            val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
            recent.sortedWith { a, b ->
                val first = a.place.name ?: ""
                val second = b.place.name ?: ""
                when {
                    first.isEmpty() != second.isEmpty() -> if (first.isEmpty()) 1 else -1
                    else -> collator.compare(first, second)
                }
            }
        }
    }
}

fun PlacesPresentation.group(
    items: List<PlaceListItem>, grouped: Boolean, by: PlacesSort = PlacesSort.frequent,
    now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault(),
): List<PlacesPresentation.Group> {
    fun single() = if (items.isEmpty()) emptyList() else listOf(PlacesPresentation.Group(PlacesPresentation.Group.Kind.all, items))
    if (!grouped || by == PlacesSort.byName) return single()
    val groups = when (by) {
        PlacesSort.frequent -> listOf(
            PlacesPresentation.Group(PlacesPresentation.Group.Kind.frequent, items.filter { it.isFrequentGuest }),
            PlacesPresentation.Group(PlacesPresentation.Group.Kind.others, items.filter { !it.isFrequentGuest }),
        )
        PlacesSort.recent -> {
            val today = now.atZone(zone).toLocalDate()
            val startOfToday = today.atStartOfDay(zone).toInstant()
            val weekAgo = today.minusDays(7).atStartOfDay(zone).toInstant()
            // A rolling seven calendar days, including changes in the time-zone offset.
            val buckets = items.groupBy { item ->
                val last = item.lastAt
                when {
                    last == null -> PlacesPresentation.Group.Kind.earlier
                    last >= startOfToday -> PlacesPresentation.Group.Kind.today
                    last >= weekAgo -> PlacesPresentation.Group.Kind.thisWeek
                    else -> PlacesPresentation.Group.Kind.earlier
                }
            }
            listOf(PlacesPresentation.Group.Kind.today, PlacesPresentation.Group.Kind.thisWeek, PlacesPresentation.Group.Kind.earlier)
                .map { PlacesPresentation.Group(it, buckets[it] ?: emptyList()) }
        }
        PlacesSort.byName -> return single()
    }.filter { it.items.isNotEmpty() }
    return if (groups.size > 1) groups else single()
}

fun PlacesPresentation.build(
    items: List<PlaceListItem>, query: String, sort: PlacesSort, locale: Locale,
    now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault(),
): List<PlacesPresentation.Group> = group(sort(filter(items, query, locale), sort),
    items.size >= manyPlaces && query.isEmpty(), sort, now, zone)

// iOS: Foundation CharacterSet.whitespaces / whitespacesAndNewlines.
// Foundation includes zero-width space; JVM trim also removes unrelated control separators.
internal fun Char.isFoundationWhitespace(includeNewlines: Boolean): Boolean = when (this) {
    '\t', ' ', '\u00a0', '\u1680', in '\u2000'..'\u200b', '\u202f', '\u205f', '\u3000' -> true
    in '\n'..'\r', '\u0085', '\u2028', '\u2029' -> includeNewlines
    else -> false
}
