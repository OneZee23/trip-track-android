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

    private fun pan() {
        val w = mapView.width.toFloat()
        val h = mapView.height.toFloat()
        val cx = w / 2
        val cy = h / 2
        val dist = w * 0.4f
        phase("pan")
        driver.drag(cx + dist / 2, cy, cx - dist / 2, cy, 500, 120) {
            driver.drag(cx - dist / 2, cy, cx + dist / 2, cy, 500, 120) {
                driver.drag(cx, cy + dist / 2, cx, cy - dist / 2, 500, 120) {
                    driver.drag(cx, cy - dist / 2, cx, cy + dist / 2, 500, 120) {
                        driver.fling(cx - dist / 2, cy - dist / 2, cx + dist / 2, cy + dist / 2, 160) {
                            handler.postDelayed({
                                driver.fling(cx + dist / 2, cy + dist / 2, cx - dist / 2, cy - dist / 2, 160) {
                                    handler.postDelayed({ endPhase(); settle { pinch() } }, 1400)
                                }
                            }, 1400)
                        }
                    }
                }
            }
        }
    }

    private fun pinch() {
        val cx = mapView.width / 2f
        val cy = mapView.height / 2f
        val small = mapView.width * 0.2f
        val large = mapView.width * 0.7f
        phase("pinch")
        fun cycle(n: Int) {
            if (n == 3) {
                endPhase()
                settle { zoom() }
                return
            }
            val angle = n * 0.7
            driver.pinch(cx, cy, small, large, angle, 700) {
                driver.pinch(cx, cy, large, small, angle, 700) { cycle(n + 1) }
            }
        }
        cycle(0)
    }

    private fun zoom() {
        phase("zoom")
        map.animateCamera(CameraUpdateFactory.zoomTo(5.0), 2500)
        handler.postDelayed({
            map.animateCamera(CameraUpdateFactory.zoomTo(15.5), 3000)
            handler.postDelayed({
                map.animateCamera(CameraUpdateFactory.zoomTo(12.0), 1000)
                handler.postDelayed({ endPhase(); finish() }, 1300)
            }, 3200)
        }, 2700)
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
        var i = 0
        while (i + 4 < samples.size) {
            val t = samples[i]
            if (t >= phase.start && t <= phase.end) {
                times += t
                cpu += samples[i + 1]
                if (samples[i + 2] >= 0) gpu += samples[i + 2]
                instances += samples[i + 3]
            }
            i += 5
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
            .put("fogGpuMs", percentiles(gpu))
            .put("segmentsPerFrame", percentiles(instances))
            .put("mapEncodingRaw", percentiles(inPhase.map { it[1] }))
            .put("mapRenderingRaw", percentiles(inPhase.map { it[2] }))
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
