// iOS: AvatarFrame.swift
package com.onezee.triptrack.model

enum class AvatarFrame(val rawValue: String) {
    none(""),
    gold("frame_gold"),
    carbon("frame_carbon"),
    neon("frame_neon"),
    chrome("frame_chrome"),
    laurel("frame_laurel"),
    flame("frame_flame");

    companion object {
        fun fromRawValue(value: String): AvatarFrame? = entries.find { it.rawValue == value }
        fun effective(id: String?, isPlus: Boolean) = (fromRawValue(id.orEmpty()) ?: none).effective(isPlus)
    }

    val isPlus: Boolean get() = this != none
    val displayName: String get() = when (this) {
        none -> ""; gold -> "Gold"; carbon -> "Carbon"; neon -> "Neon"; chrome -> "Chrome"; laurel -> "Laurel"; flame -> "Flame"
    }
    fun effective(isPlus: Boolean) = if (this.isPlus && !isPlus) none else this
}
