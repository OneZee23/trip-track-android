package com.onezee.triptrack.fogspike.bench

import android.os.Debug
import android.os.Handler
import android.os.Looper
import com.onezee.triptrack.fogspike.fog.NativeFog
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * The scripted run: pan (drags and flings), pinch (two fingers in and out) and zoom (camera
 * animations from city to country to street, across every level of detail). Frame times come
 * from the native render callback, which runs once per map frame on the GL thread for every
 * candidate, so the numbers are comparable.
 */
class BenchRunner(
    private val map: MapLibreMap,
    private val mapView: MapView,
    private val frames: Long,
    private val centre: LatLng,
    private val onDone: (JSONObject) -> Unit,
) {
    private class Phase(val name: String, val start: Double, var end: Double = 0.0)

    private val driver = GestureDriver(mapView)
    private val handler = Handler(Looper.getMainLooper())
    private val phases = mutableListOf<Phase>()
    private val mapFrames = mutableListOf<DoubleArray>()
    private val listener = MapView.OnDidFinishRenderingFrameWithStatsListener { _, stats ->
        mapFrames += doubleArrayOf(nowMs(), stats.encodingTime, stats.renderingTime, stats.numDrawCalls.toDouble())
    }

    fun start() {
        mapView.addOnDidFinishRenderingFrameListener(listener)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(centre, 12.0))
        handler.postDelayed({
            NativeFog.takeSamples(frames)
            pan()
        }, 3000)
    }

    // Every phase keeps the camera moving from its first frame to its last: a finger at rest
    // renders nothing, and the gap would read as a slow frame.
    private fun pan() {
        val cx = mapView.width / 2f
        val cy = mapView.height / 2f
        val r = mapView.width * 0.3f
        phase("pan")
        // A finger tracing two circles: the map slides around the city and back.
        driver.path(4500, position = { t ->
            val a = 2 * Math.PI * 2 * t
            (cx + r * cos(a).toFloat()) to (cy + r * sin(a).toFloat())
        }) {
            endPhase()
            settle { pinch() }
        }
    }

    private fun pinch() {
        val cx = mapView.width / 2f
        val cy = mapView.height / 2f
        phase("pinch")
        // Three times in and out, about 1.7 zoom levels each way, fingers never lifted.
        driver.pinchSwing(cx, cy, mapView.width * 0.2f, mapView.width * 0.65f, cycles = 3, durationMs = 4500) {
            endPhase()
            settle { zoom() }
        }
    }

    // City to country to street and back, chained, across all three levels of detail.
    private fun zoom() {
        phase("zoom")
        animate(5.0, 2500) {
            animate(15.5, 3000) {
                animate(12.0, 1000) {
                    endPhase()
                    finish()
                }
            }
        }
    }

    private fun animate(zoom: Double, durationMs: Int, next: () -> Unit) {
        map.animateCamera(CameraUpdateFactory.zoomTo(zoom), durationMs, object : MapLibreMap.CancelableCallback {
            override fun onFinish() = next()
            override fun onCancel() = next()
        })
    }

    private fun settle(next: () -> Unit) {
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(centre, 12.0))
        handler.postDelayed(next, 1500)
    }

    private fun phase(name: String) {
        phases += Phase(name, nowMs())
    }

    private fun endPhase() {
        phases.last().end = nowMs()
    }

    private fun finish() {
        mapView.removeOnDidFinishRenderingFrameListener(listener)
        val samples = NativeFog.takeSamples(frames)
        val result = JSONObject()
        val byPhase = JSONObject()
        for (phase in phases) byPhase.put(phase.name, summarise(phase, samples))
        result.put("phases", byPhase)
        result.put("memory", memory())
        onDone(result)
    }

    private fun summarise(phase: Phase, samples: DoubleArray): JSONObject {
        val times = ArrayList<Double>()
        val cpu = ArrayList<Double>()
        val gpu = ArrayList<Double>()
        val instances = ArrayList<Double>()
        val encode = ArrayList<Double>()
        var i = 0
        while (i + 5 < samples.size) {
            val t = samples[i]
            if (t >= phase.start && t <= phase.end) {
                times += t
                cpu += samples[i + 1]
                if (samples[i + 2] >= 0) gpu += samples[i + 2]
                instances += samples[i + 3]
                encode += samples[i + 1] - samples[i + 5]
            }
            i += 6
        }
        val intervals = times.zipWithNext { a, b -> b - a }
        val inPhase = mapFrames.filter { it[0] >= phase.start && it[0] <= phase.end }
        return JSONObject()
            .put("durationMs", phase.end - phase.start)
            .put("frames", times.size)
            .put("fps", times.size / ((phase.end - phase.start) / 1000.0))
            .put("intervalMs", percentiles(intervals))
            .put("over25ms", intervals.count { it > 25.0 })
            .put("over50ms", intervals.count { it > 50.0 })
            .put("fogCpuMs", percentiles(cpu))
            .put("fogEncodeMs", percentiles(encode))
            .put("fogGpuMs", percentiles(gpu))
            .put("segmentsPerFrame", percentiles(instances))
            // MapLibre reports seconds. With the GL backend the GPU commands are issued while the
            // frame is encoded, so this is the map's whole CPU cost per frame, the fog included.
            .put("mapFrameCpuMs", percentiles(inPhase.map { it[1] * 1000 }))
            .put("mapDrawCalls", percentiles(inPhase.map { it[3] }))
    }

    private fun memory(): JSONObject {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        val runtime = Runtime.getRuntime()
        return JSONObject()
            .put("totalPssKb", info.getMemoryStat("summary.total-pss"))
            .put("graphicsKb", info.getMemoryStat("summary.graphics"))
            .put("nativeHeapKb", info.getMemoryStat("summary.native-heap"))
            .put("javaHeapKb", info.getMemoryStat("summary.java-heap"))
            .put("javaUsedKb", (runtime.totalMemory() - runtime.freeMemory()) / 1024)
            .put("nativeAllocatedKb", Debug.getNativeHeapAllocatedSize() / 1024)
    }

    companion object {
        fun nowMs(): Double = System.nanoTime() / 1e6

        fun percentiles(values: List<Double>): JSONObject {
            val out = JSONObject().put("n", values.size)
            if (values.isEmpty()) return out
            val sorted = values.sorted()
            fun rank(p: Double) = sorted[(ceil(p * sorted.size).toInt() - 1).coerceIn(0, sorted.size - 1)]
            return out.put("p50", rank(0.50)).put("p90", rank(0.90)).put("p95", rank(0.95))
                .put("p99", rank(0.99)).put("max", sorted.last()).put("mean", sorted.average())
        }

        fun list(values: List<Double>): JSONArray = JSONArray().apply { values.forEach { put(it) } }
    }
}
