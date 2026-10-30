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
    }
}
