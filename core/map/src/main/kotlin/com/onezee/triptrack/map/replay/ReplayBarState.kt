package com.onezee.triptrack.map.replay

// iOS: ReplayBarState.swift
enum class ReplayBarState {
    idle, playing, paused;

    // iOS: ReplayBarState.Event
    enum class Event { tapPlayPause, tapStop, scrub, reachedEnd }

    val showsStop: Boolean get() = this != idle
    val showsPauseIcon: Boolean get() = this == playing
    val showsHead: Boolean get() = this != idle

    fun reduce(event: Event): ReplayBarState = when (event) {
        Event.tapPlayPause -> if (this == playing) paused else playing
        Event.tapStop -> idle
        Event.scrub -> if (this == idle) paused else this
        Event.reachedEnd -> if (this == playing) paused else this
    }
}
