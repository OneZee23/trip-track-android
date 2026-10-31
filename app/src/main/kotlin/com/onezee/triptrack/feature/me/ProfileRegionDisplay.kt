// iOS: RegionDisplay.swift. Geocoded storage stays raw; display and region counts follow the same aliases.
package com.onezee.triptrack.feature.me

import java.util.Locale
import kotlinx.serialization.json.*

internal object ProfileRegionDisplay {
    // iOS: RegionDisplay.Entry. The generated table includes full names, short names and recognition aliases.
    private data class Entry(val english: String, val russian: String)
    private val index: Map<String, Entry> by lazy {
        val input = requireNotNull(ProfileRegionDisplay::class.java.getResourceAsStream("/ProfileRegions.json")).use { it.readBytes().decodeToString() }
        buildMap {
            Json.parseToJsonElement(input).jsonArray.forEach { value ->
                val item = value.jsonObject
                val entry = Entry(item.getValue("en").jsonPrimitive.content, item.getValue("ru").jsonPrimitive.content)
                item.getValue("names").jsonArray.forEach { put(normalize(it.jsonPrimitive.content), entry) }
            }
        }
    }
    fun localized(raw: String?, language: String): String? = raw?.let { value -> index[normalize(value)]?.let { if (language == "ru") it.russian else it.english } ?: value }
    fun canonical(raw: String) = index[normalize(raw)]?.english ?: raw.trim()
    private fun normalize(value: String) = value.trim().lowercase(Locale.ROOT).replace('ё', 'е').replace("'", "").replace("’", "")
}
