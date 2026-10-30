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
        fun fromRawValue(value: String): RouteLineStyle? = entries.find { it.rawValue == value }
    }
}
