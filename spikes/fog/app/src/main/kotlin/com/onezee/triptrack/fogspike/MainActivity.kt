package com.onezee.triptrack.fogspike

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.onezee.triptrack.fogspike.bench.BenchRunner
import com.onezee.triptrack.fogspike.bench.GestureDriver
import com.onezee.triptrack.fogspike.candidates.FogCandidate
import com.onezee.triptrack.fogspike.candidates.FogTileInterceptor
import com.onezee.triptrack.fogspike.data.Dataset
import com.onezee.triptrack.fogspike.data.Datasets
import com.onezee.triptrack.fogspike.data.RevealLite
import com.onezee.triptrack.fogspike.data.RevealedLayerLite
import com.onezee.triptrack.fogspike.fog.FogMesh
import com.onezee.triptrack.fogspike.fog.FogStyle
import com.onezee.triptrack.fogspike.fog.NativeFog
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil
import java.io.File
import java.util.concurrent.Executors

/**
 * The spike app. Everything can be driven by intent extras, so the measurements run from a
 * script: `candidate` (a, b, c, none), `dataset` (small, stress, ios), `style` (fog, night,
 * cells), `preset` (street, city, region, country), `bench` (true) and `gesture` (pinch, pan:
 * a slow gesture to capture a frame in the middle of it).
 */
class MainActivity : Activity() {
    private class Prepared(val layer: RevealedLayerLite, val mesh: FogMesh, val timings: JSONObject)

    private lateinit var mapView: MapView
    private lateinit var status: TextView
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val frames = NativeFog.newState()
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var prepared: Prepared? = null
    private lateinit var candidate: FogCandidate
    private lateinit var dataset: Dataset
    private lateinit var fogStyle: FogStyle
    private var installed = false
    private var lastIdle = 0L
    private var lastMoveStart = 0L
    private var readyLogged = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        candidate = FogCandidate.of(intent.getStringExtra("candidate"))
        dataset = Dataset.of(intent.getStringExtra("dataset"))
        fogStyle = FogStyle.of(intent.getStringExtra("style"))
        NativeFog.halfResCoverage = intent.getStringExtra("coverage") == "half"

        MapLibre.getInstance(this)
        // Candidate C serves its tiles through this client; every other request passes through.
        HttpRequestUtil.setOkHttpClient(
            OkHttpClient.Builder()
                .dispatcher(Dispatcher().apply { maxRequestsPerHost = 8 })
                .addInterceptor(FogTileInterceptor)
                .build(),
        )

        mapView = MapView(this)
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(16, 8, 16, 8)
        }
        val root = FrameLayout(this)
        root.addView(mapView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(controls(), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
            topMargin = statusBarInset()
        })
        root.addView(status, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
            bottomMargin = 48
        })
        setContentView(root)
        mapView.onCreate(savedInstanceState)

        worker.execute { prepare() }
        mapView.getMapAsync { m ->
            map = m
            // No tilt: the fog frame is affine only without perspective, as on iOS.
            m.uiSettings.isTiltGesturesEnabled = false
            m.setMaxPitchPreference(0.0)
            m.cameraPosition = CameraPosition.Builder().target(CENTRE).zoom(presetZoom()).build()
            m.addOnCameraMoveStartedListener { lastMoveStart = SystemClock.elapsedRealtime() }
            mapView.addOnDidBecomeIdleListener { lastIdle = SystemClock.elapsedRealtime() }
            m.setStyle(Style.Builder().fromUri(STYLE_URL)) { loaded ->
                style = loaded
                maybeInstall()
            }
        }
    }

    private fun prepare() {
        val t0 = SystemClock.elapsedRealtimeNanos()
        val trips = Datasets.trips(dataset)
        val t1 = SystemClock.elapsedRealtimeNanos()
        val layer = RevealLite.build(trips)
        val t2 = SystemClock.elapsedRealtimeNanos()
        val mesh = FogMesh.build(layer)
        val t3 = SystemClock.elapsedRealtimeNanos()
        val timings = JSONObject()
            .put("trips", trips.size)
            .put("tripPoints", trips.sumOf { it.size / 2 })
            .put("generateMs", (t1 - t0) / 1e6)
            .put("revealMs", (t2 - t1) / 1e6)
            .put("meshMs", (t3 - t2) / 1e6)
            .put("cells", layer.cellCount)
            .put("segmentsFine", layer.segmentCount(0))
            .put("segmentsMid", layer.segmentCount(1))
            .put("segmentsFar", layer.segmentCount(2))
            .put("runsFine", layer.fine.size)
            .put("chunksFine", mesh.lods[0].chunkCount)
            .put("chunksMid", mesh.lods[1].chunkCount)
            .put("chunksFar", mesh.lods[2].chunkCount)
            .put("meshBytes", mesh.byteSize)
        Log.i(TAG, "prepared ${dataset.key}: $timings")
        main.post {
            prepared = Prepared(layer, mesh, timings)
            maybeInstall()
        }
    }

    private fun maybeInstall() {
        val m = map ?: return
        val s = style ?: return
        val p = prepared ?: return
        if (installed) return
        installed = true
        val started = SystemClock.elapsedRealtimeNanos()
        candidate.install(m, s, p.layer, p.mesh, fogStyle, frames)
        p.timings.put("installMs", (SystemClock.elapsedRealtimeNanos() - started) / 1e6)
        updateStatus()
        main.postDelayed(::watchReady, 500)
    }

    private fun watchReady() {
        if (readyLogged) return
        val now = SystemClock.elapsedRealtime()
        if (candidate.isReady() && lastIdle > lastMoveStart && now - lastIdle > 700) {
            readyLogged = true
            Log.i(TAG, "ready candidate=${candidate.key} dataset=${dataset.key} style=${fogStyle.key} preset=${intent.getStringExtra("preset")}")
            when {
                intent.getBooleanExtra("bench", false) -> bench()
                intent.getStringExtra("gesture") != null -> slowGesture(intent.getStringExtra("gesture")!!)
            }
            return
        }
        main.postDelayed(::watchReady, 250)
    }

    private fun bench() {
        val m = map ?: return
        status.text = "bench running…"
        BenchRunner(m, mapView, frames, CENTRE) { result ->
            result.put("candidate", candidate.key)
            result.put("dataset", dataset.key)
            result.put("style", fogStyle.key)
            result.put("coverage", if (NativeFog.halfResCoverage) "half" else "full")
            result.put("device", "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
            result.put("density", resources.displayMetrics.density)
            result.put("viewPx", "${mapView.width}x${mapView.height}")
            result.put("prepare", prepared?.timings)
            result.put("candidateReport", JSONObject(candidate.report()))
            val half = if (NativeFog.halfResCoverage) "-half" else ""
            val file = File(filesDir, "bench-${candidate.key}-${dataset.key}-${fogStyle.key}$half.json")
            file.writeText(result.toString(2))
            Log.i(TAG, "bench-done ${file.absolutePath}")
            status.text = summary(result)
        }.start()
    }

    // A slow gesture so a screenshot can catch the fog in the middle of it.
    private fun slowGesture(kind: String) {
        val driver = GestureDriver(mapView)
        val w = mapView.width.toFloat()
        val h = mapView.height.toFloat()
        Log.i(TAG, "gesture-start $kind")
        when (kind) {
            "pan" -> driver.drag(w * 0.8f, h / 2, w * 0.2f, h / 2, 6000, 200) { Log.i(TAG, "gesture-end") }
            // A slow camera zoom: the same render path as a pinch, two levels in six seconds.
            "zoom" -> map?.animateCamera(CameraUpdateFactory.zoomBy(2.0), 6000)
            else -> driver.pinchSwing(w / 2, h / 2, w * 0.25f, w * 0.85f, cycles = 0.5, durationMs = 6000) {
                Log.i(TAG, "gesture-end")
            }
        }
    }

    private fun summary(result: JSONObject): String {
        val phases = result.getJSONObject("phases")
        return buildString {
            append("${candidate.key} · ${dataset.key} · ${fogStyle.key}\n")
            for (name in phases.keys()) {
                val p = phases.getJSONObject(name)
                val interval = p.getJSONObject("intervalMs")
                val encode = p.getJSONObject("fogEncodeMs")
                val gpu = p.getJSONObject("fogGpuMs")
                append("$name: ${"%.0f".format(p.getDouble("fps"))} fps, frame p50 ${fmt(interval, "p50")} p95 ${fmt(interval, "p95")} p99 ${fmt(interval, "p99")} ms")
                if (candidate.key == "a") {
                    append(", fog cpu p95 ${fmt(encode, "p95")}")
                    append(", gpu p50 ${fmt(gpu, "p50")} p95 ${fmt(gpu, "p95")} ms")
                }
                append('\n')
            }
        }
    }

    private fun fmt(o: JSONObject, key: String): String = if (o.has(key)) "%.1f".format(o.getDouble(key)) else "–"

    private fun updateStatus() {
        val t = prepared?.timings ?: return
        status.text = "${candidate.key} · ${dataset.key} · ${fogStyle.key}\n" +
            "segments fine/mid/far ${t.optInt("segmentsFine")}/${t.optInt("segmentsMid")}/${t.optInt("segmentsFar")}\n" +
            "reveal ${"%.0f".format(t.optDouble("revealMs"))} ms, mesh ${"%.0f".format(t.optDouble("meshMs"))} ms"
    }

    private fun controls(): HorizontalScrollView {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun button(label: String, action: (Button) -> Unit) {
            row.addView(Button(this).apply {
                text = label
                isAllCaps = false
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setOnClickListener { action(this) }
            })
        }
        button("Fog ${candidate.key.uppercase()}") {
            val keys = FogCandidate.KEYS
            relaunch("candidate", keys[(keys.indexOf(candidate.key) + 1) % keys.size])
        }
        button("Data ${dataset.key}") {
            val all = Dataset.entries
            relaunch("dataset", all[(all.indexOf(dataset) + 1) % all.size].key)
        }
        button("Style ${fogStyle.key}") { b ->
            val all = FogStyle.entries
            fogStyle = all[(all.indexOf(fogStyle) + 1) % all.size]
            b.text = "Style ${fogStyle.key}"
            style?.let { candidate.setFogStyle(it, fogStyle) }
            updateStatus()
        }
        for (preset in PRESETS.keys) button(preset) { goTo(preset) }
        button("Cov ${if (NativeFog.halfResCoverage) "half" else "full"}") { b ->
            NativeFog.halfResCoverage = !NativeFog.halfResCoverage
            b.text = "Cov ${if (NativeFog.halfResCoverage) "half" else "full"}"
            style?.let { candidate.setFogStyle(it, fogStyle) }
        }
        button("Bench") { bench() }
        return HorizontalScrollView(this).apply { addView(row) }
    }

    private fun goTo(preset: String) {
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(CENTRE, PRESETS.getValue(preset)), 800)
    }

    private fun relaunch(key: String, value: String) {
        val next = Intent(this, MainActivity::class.java)
        intent.extras?.let { next.putExtras(it) }
        next.putExtra("candidate", candidate.key).putExtra("dataset", dataset.key).putExtra("style", fogStyle.key)
            .putExtra("coverage", if (NativeFog.halfResCoverage) "half" else "full")
        next.removeExtra("bench")
        next.removeExtra("gesture")
        next.putExtra(key, value)
        startActivity(next)
        finish()
    }

    private fun presetZoom(): Double = PRESETS[intent.getStringExtra("preset")] ?: PRESETS.getValue("city")

    private fun statusBarInset(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        candidate.release()
        mapView.onDestroy()
        worker.shutdownNow()
        // The layer's host keeps its own reference to the state; this drops only ours.
        NativeFog.releaseState(frames)
        super.onDestroy()
    }

    companion object {
        const val TAG = "FOGSPIKE"
        // OpenFreeMap: free vector tiles, no key, no account.
        const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
        val CENTRE = LatLng(Datasets.CENTRE_LAT, Datasets.CENTRE_LON)
        val PRESETS = linkedMapOf("street" to 15.5, "city" to 11.5, "region" to 8.0, "country" to 5.0)
    }
}
