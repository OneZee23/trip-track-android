package com.onezee.triptrack.map.replay

import com.onezee.triptrack.logic.GeometryUtils
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.TripReplayInput
import java.time.Duration
import java.time.Instant

// iOS: FullscreenMapSheet.swift, TripReplayEngine. The caller supplies the monotonic display clock.
class TripReplayEngine {
    var phase = ReplayBarState.idle; private set
    val isPlaying: Boolean get() = phase == ReplayBarState.playing
    var progress = 0.0; private set
    var headCoord: GeoPoint? = null; private set
    var trailIndex = -1; private set
    var currentSpeedMS = 0.0; private set
    var currentSpeedKnown = true; private set
    val hasSpeeds: Boolean get() = speeds.isNotEmpty()
    var distanceFraction = 0.0; private set
    var rate = 1.0; private set
    var holdFractions = emptyList<Double>(); private set
    var holdingIndex: Int? = null; private set

    private var coords = emptyList<GeoPoint>()
    private var timestamps = emptyList<Instant>()
    private var speeds = emptyList<Double>()
    private var cumulativeMetres = emptyList<Double>()
    private var baseDuration = 12.0
    private var lastTick = 0.0
    private var holdUntil = 0.0
    private var cursor = 0

    fun configure(input: TripReplayInput) {
        if (input.coords.size < 2 || input.timestamps.size != input.coords.size) return
        coords = input.coords
        timestamps = input.timestamps
        speeds = input.speeds.takeIf { it.size == coords.size }.orEmpty()
        var metres = 0.0
        cumulativeMetres = coords.mapIndexed { i, point ->
            if (i > 0) metres += GeometryUtils.haversineDistance(coords[i - 1], point)
            metres
        }
        baseDuration = maxOf(1.0, seconds(timestamps.first(), timestamps.last()))
        rate = 1.0
        stop()
    }

    fun setHolds(dates: List<Instant>) {
        if (timestamps.size < 2) { holdFractions = emptyList(); return }
        val span = seconds(timestamps.first(), timestamps.last())
        if (span <= 0) { holdFractions = emptyList(); return }
        holdFractions = dates.map { seconds(timestamps.first(), it) / span }.filter { it > 0 && it < 1 }.sorted()
        holdingIndex = null
    }

    fun play(now: Double) {
        if (coords.size < 2 || isPlaying) return
        if (progress >= 1) { progress = 0.0; cursor = 0 }
        phase = ReplayBarState.playing
        applyFrame()
        lastTick = now
    }

    fun pause() {
        if (isPlaying) phase = phase.reduce(ReplayBarState.Event.tapPlayPause)
        holdingIndex = null
    }

    fun togglePlay(now: Double) { if (isPlaying) pause() else play(now) }

    fun stop() {
        pause()
        phase = phase.reduce(ReplayBarState.Event.tapStop)
        headCoord = null
        trailIndex = -1
        progress = 0.0
        distanceFraction = 0.0
        currentSpeedMS = 0.0
        currentSpeedKnown = true
        cursor = 0
        holdingIndex = null
    }

    fun cycleRate() {
        val index = rateLadder.indexOf(rate)
        rate = rateLadder[if (index >= 0) (index + 1) % rateLadder.size else 0]
    }

    fun seek(progress: Double) {
        if (coords.size < 2) return
        phase = phase.reduce(ReplayBarState.Event.scrub)
        this.progress = progress.coerceIn(0.0, 1.0)
        cursor = 0
        holdingIndex = null
        applyFrame()
    }

    fun tick(now: Double) {
        if (!isPlaying) return
        // A suspended display clock must not jump across the trip on returning to the foreground.
        val dt = minOf(now - lastTick, 0.5)
        lastTick = now
        if (holdingIndex != null) {
            if (now < holdUntil) return
            holdingIndex = null
        }
        val before = progress
        progress = minOf(1.0, before + dt * rate / baseDuration)
        val hit = holdFractions.indexOfFirst { it > before && it <= progress }
        if (hit >= 0) {
            progress = holdFractions[hit]
            holdingIndex = hit
            // Checkpoint cards need the same reading time at every playback rate.
            holdUntil = now + holdDuration
        }
        applyFrame()
        if (progress >= 1) { phase = phase.reduce(ReplayBarState.Event.reachedEnd); holdingIndex = null }
    }

    private fun applyFrame() {
        if (coords.size < 2 || phase == ReplayBarState.idle) return
        val span = seconds(timestamps.first(), timestamps.last())
        if (span <= 0) {
            headCoord = coords.last()
            trailIndex = coords.lastIndex
            distanceFraction = 1.0
            return
        }
        val target = span * progress
        if (cursor > 0 && seconds(timestamps.first(), timestamps[cursor]) > target) cursor = 0
        while (cursor + 1 < timestamps.size && seconds(timestamps.first(), timestamps[cursor + 1]) <= target) cursor++
        val lo = cursor
        val hi = minOf(lo + 1, coords.lastIndex)
        val segmentDuration = seconds(timestamps[lo], timestamps[hi])
        val fraction = if (segmentDuration > 0) ((target - seconds(timestamps.first(), timestamps[lo])) / segmentDuration).coerceIn(0.0, 1.0) else 0.0
        headCoord = GeoPoint(
            coords[lo].latitude + (coords[hi].latitude - coords[lo].latitude) * fraction,
            coords[lo].longitude + (coords[hi].longitude - coords[lo].longitude) * fraction,
        )
        trailIndex = lo
        if (speeds.isNotEmpty()) {
            currentSpeedKnown = speeds[lo] >= 0 && speeds[hi] >= 0
            currentSpeedMS = if (currentSpeedKnown) maxOf(0.0, speeds[lo] + (speeds[hi] - speeds[lo]) * fraction) else 0.0
        }
        val total = cumulativeMetres.last()
        if (total > 0) distanceFraction = ((cumulativeMetres[lo] + (cumulativeMetres[hi] - cumulativeMetres[lo]) * fraction) / total).coerceIn(0.0, 1.0)
    }

    companion object {
        val rateLadder = listOf(1.0, 1.5, 2.0, 3.0, 5.0)
        const val holdDuration = 1.8
        fun seconds(from: Instant, to: Instant): Double = Duration.between(from, to).let { it.seconds + it.nano / 1e9 }
    }
}
