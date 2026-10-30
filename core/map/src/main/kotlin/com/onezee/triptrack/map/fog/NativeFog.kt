package com.onezee.triptrack.map.fog

import android.util.Log

/**
 * The native fog layer (`src/main/cpp`). A state handle is owned by the Kotlin side; a host handle is
 * handed to MapLibre's `CustomLayer`, which owns and deletes it.
 */
internal object NativeFog {
    private const val TAG = "TripTrackFog"

    // A phone without the library (an ABI MapLibre ships and we failed to build) shows the map without
    // fog instead of crashing, the way iOS falls back when Metal is unavailable.
    val isLoaded: Boolean = try {
        System.loadLibrary("triptrackfog")
        true
    } catch (error: UnsatisfiedLinkError) {
        Log.e(TAG, "fog library unavailable, the map draws without fog", error)
        false
    }

    @JvmStatic external fun nCreate(rules: DoubleArray): Long

    @JvmStatic external fun nRelease(state: Long)

    @JvmStatic external fun nCreateHost(state: Long): Long

    @JvmStatic external fun nStageLod(state: Long, lod: Int, segments: FloatArray, chunks: DoubleArray, ranges: IntArray): Boolean

    @JvmStatic external fun nCommit(state: Long)

    @JvmStatic external fun nSetStyle(
        state: Long, red: Float, green: Float, blue: Float, alpha: Float, cells: Boolean, halfResolution: Boolean, visible: Boolean,
    )

    @JvmStatic external fun nSetReveal(state: Long, latitude: Double, longitude: Double, radiusMetres: Double)

    @JvmStatic external fun nSetSuspended(state: Long, suspended: Boolean)

    @JvmStatic external fun nSetDiagnostics(state: Long, enabled: Boolean)

    /** MapLibre's GL thread only (`MapView.queueEvent`). */
    @JvmStatic external fun nReleaseCoverage(state: Long): Boolean

    /** The numbers of `fog_jni.cpp`'s nStats, in its order; see [FogStats]. */
    @JvmStatic external fun nStats(state: Long): DoubleArray
}

/** What the native layer counted (iOS: FogMetalVeil.frames and encodeSeconds, and the budget numbers). */
data class FogStats(
    val frames: Long,
    val encodeMsTotal: Double,
    val lastEncodeMs: Double,
    val lastLod: Int,
    val lastChunks: Int,
    val lastSegments: Int,
    val lastHalfWidthPoints: Double,
    /** GPU time of both passes from a timer query; null on drivers without one (the emulator). */
    val gpuMs: Double?,
    val uploads: Long,
    val lastUploadMs: Double,
    val uploadBytes: Double,
    val suspendedFrames: Long,
    val coverageReleases: Long,
    val contextLosses: Long,
    val initializations: Long,
    val shaderFailed: Boolean,
    val meshVersion: Long,
    val hostAlive: Boolean,
    val suspended: Boolean,
) {
    val encodeMsPerFrame: Double get() = if (frames > 0) encodeMsTotal / frames else 0.0

    internal companion object {
        fun parse(v: DoubleArray): FogStats = FogStats(
            frames = v[0].toLong(),
            encodeMsTotal = v[1],
            lastEncodeMs = v[2],
            lastLod = v[3].toInt(),
            lastChunks = v[4].toInt(),
            lastSegments = v[5].toInt(),
            lastHalfWidthPoints = v[6],
            gpuMs = v[7].takeIf { it >= 0 },
            uploads = v[8].toLong(),
            lastUploadMs = v[9],
            uploadBytes = v[10],
            suspendedFrames = v[11].toLong(),
            coverageReleases = v[12].toLong(),
            contextLosses = v[13].toLong(),
            initializations = v[14].toLong(),
            shaderFailed = v[15] > 0,
            meshVersion = v[16].toLong(),
            hostAlive = v[17] > 0,
            suspended = v[18] > 0,
        )
    }
}
