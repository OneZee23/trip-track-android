// iOS: RouteLineStyle.swift
package com.onezee.triptrack.model

// iOS: RouteLineStyle.swift
enum class RouteLineStyle(val rawValue: String) {
    speed(""),
    amber("amber"),
    crimson("crimson"),
    violet("violet"),
    teal("teal"),
    lime("lime"),
    ice("ice");

    companion object {
        const val storageKey = "com.triptrack.settings.routeLineStyle"
        @Volatile private var storedRawValue: String = ""
        @Volatile private var plusActive: Boolean = false
        fun fromRawValue(value: String): RouteLineStyle? = entries.find { it.rawValue == value }
        fun effective(id: String?, isPlus: Boolean) = (fromRawValue(id.orEmpty()) ?: speed).effective(isPlus)
        fun stored() = fromRawValue(storedRawValue) ?: speed
        fun setStored(value: RouteLineStyle) { storedRawValue = value.rawValue }
        fun rememberPlus(value: Boolean) { plusActive = value }
        fun currentColorArgb(): Int? = stored().effective(plusActive).colorArgb
    }

    val isPlus: Boolean get() = this != speed
    val displayName: String get() = when (this) {
        speed -> ""; amber -> "Amber"; crimson -> "Crimson"; violet -> "Violet"; teal -> "Teal"; lime -> "Lime"; ice -> "Ice"
    }
    fun effective(isPlus: Boolean) = if (this.isPlus && !isPlus) speed else this
    val colorArgb: Int? get() = when (this) {
        speed -> null
        amber -> 0xF2F5A623.toInt()
        crimson -> 0xF2E03344.toInt()
        violet -> 0xF28B5CF6.toInt()
        teal -> 0xF218B5A8.toInt()
        lime -> 0xF286CB2E.toInt()
        ice -> 0xF26FB6E8.toInt()
    }

}
