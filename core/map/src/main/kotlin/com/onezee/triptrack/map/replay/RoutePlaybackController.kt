package com.onezee.triptrack.map.replay

import com.onezee.triptrack.model.GeoPoint
import kotlin.math.max
import kotlin.math.min

// iOS: RoutePlaybackController.swift. Fullscreen's no-timestamp crawl, resampled once at 60 Hz.
class RoutePlaybackController {
    var isPlaying = false; private set
    var currentCoord: GeoPoint? = null; private set
    var currentTrailIndex = -1; private set
    var progress = 0.0; private set
    private var resampled = emptyList<GeoPoint>()
    private var trailHead = emptyList<Int>()
    private var startedAt = 0.0
    private var durationSeconds = 0.0
    private var finishedAt: Double? = null

    fun toggle(coords: List<GeoPoint>, distanceMetres: Double, now: Double) {
        if (isPlaying) stop() else start(coords, distanceMetres, now)
    }

    fun start(coords: List<GeoPoint>, distanceMetres: Double, now: Double) {
        stop()
        if (coords.size < 2) return
        durationSeconds = max(7.0, min(40.0, max(0.0, distanceMetres / 1000) * 1.4))
        val frames = max(60, (durationSeconds * 60).toInt())
        val indices = ArrayList<Int>(frames)
        resampled = (0 until frames).map { frame ->
            val exact = frame.toDouble() / max(frames - 1, 1) * (coords.size - 1)
            val lo = exact.toInt().coerceIn(0, coords.size - 2)
            val fraction = exact - lo
            indices += lo
            GeoPoint(coords[lo].latitude + (coords[lo + 1].latitude - coords[lo].latitude) * fraction,
                coords[lo].longitude + (coords[lo + 1].longitude - coords[lo].longitude) * fraction)
        }
        trailHead = indices
        currentCoord = resampled.first()
        currentTrailIndex = trailHead.first()
        isPlaying = true
        startedAt = now
    }

    fun tick(now: Double) {
        if (!isPlaying) return
        val finish = finishedAt
        if (finish != null) { if (now >= finish + 1.2) stop(); return }
        val position = (now - startedAt) / durationSeconds
        if (position >= 1) {
            currentCoord = resampled.last()
            currentTrailIndex = trailHead.last()
            progress = 1.0
            finishedAt = now
            return
        }
        val index = (position * resampled.size).toInt().coerceIn(0, resampled.lastIndex)
        currentCoord = resampled[index]
        currentTrailIndex = trailHead[index]
        progress = position
    }

    fun stop() {
        resampled = emptyList()
        trailHead = emptyList()
        currentCoord = null
        currentTrailIndex = -1
        isPlaying = false
        progress = 0.0
        finishedAt = null
    }
}
