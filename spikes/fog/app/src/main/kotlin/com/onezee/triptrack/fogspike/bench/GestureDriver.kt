package com.onezee.triptrack.fogspike.bench

import android.os.SystemClock
import android.view.Choreographer
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Injects finger gestures into a view, one move per display frame, so MapLibre's own gesture
 * detectors run exactly as they do under a real finger. `adb shell input` has no multi-touch,
 * and a camera animation would skip the gesture pipeline.
 */
class GestureDriver(private val view: View) {
    private val choreographer = Choreographer.getInstance()

    /** Two fingers on a line through ([cx], [cy]) moving from [fromSpan] to [toSpan] apart. */
    fun pinch(cx: Float, cy: Float, fromSpan: Float, toSpan: Float, angle: Double, durationMs: Long, done: () -> Unit) {
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        fun points(span: Float) = listOf(
            cx - dx * span / 2 to cy - dy * span / 2,
            cx + dx * span / 2 to cy + dy * span / 2,
        )
        val down = SystemClock.uptimeMillis()
        val start = points(fromSpan)
        send(MotionEvent.ACTION_DOWN, down, down, start.take(1))
        send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), down, down, start)
        frames(durationMs, onFrame = { t ->
            send(MotionEvent.ACTION_MOVE, down, SystemClock.uptimeMillis(), points(fromSpan + (toSpan - fromSpan) * t))
        }) {
            val now = SystemClock.uptimeMillis()
            val end = points(toSpan)
            send(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), down, now, end)
            send(MotionEvent.ACTION_UP, down, now, end.take(1))
            done()
        }
    }

    /**
     * One finger from ([x0], [y0]) to ([x1], [y1]). With [settleMs] the finger stops before it
     * lifts, so there is no fling; without it the map flings on.
     */
    fun drag(x0: Float, y0: Float, x1: Float, y1: Float, durationMs: Long, settleMs: Long, done: () -> Unit) {
        val down = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, down, down, listOf(x0 to y0))
        frames(durationMs, onFrame = { t ->
            val eased = t * t * (3 - 2 * t)
            send(MotionEvent.ACTION_MOVE, down, SystemClock.uptimeMillis(), listOf(x0 + (x1 - x0) * eased to y0 + (y1 - y0) * eased))
        }) {
            frames(settleMs, onFrame = { _ ->
                send(MotionEvent.ACTION_MOVE, down, SystemClock.uptimeMillis(), listOf(x1 to y1))
            }) {
                send(MotionEvent.ACTION_UP, down, SystemClock.uptimeMillis(), listOf(x1 to y1))
                done()
            }
        }
    }

    /** One finger following [position] (t from 0 to 1) without ever resting, then lifted. */
    fun path(durationMs: Long, position: (Float) -> Pair<Float, Float>, done: () -> Unit) {
        val down = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, down, down, listOf(position(0f)))
        frames(durationMs, onFrame = { t ->
            send(MotionEvent.ACTION_MOVE, down, SystemClock.uptimeMillis(), listOf(position(t)))
        }) {
            send(MotionEvent.ACTION_UP, down, SystemClock.uptimeMillis(), listOf(position(1f)))
            done()
        }
    }

    /** Two fingers that stay down while their span swings between [small] and [large] [cycles] times. */
    fun pinchSwing(cx: Float, cy: Float, small: Float, large: Float, cycles: Double, durationMs: Long, done: () -> Unit) {
        fun points(t: Float): List<Pair<Float, Float>> {
            val span = small + (large - small) * (1 - cos(2 * Math.PI * cycles * t).toFloat()) / 2
            val angle = 0.6 * sin(2 * Math.PI * t)
            val dx = cos(angle).toFloat() * span / 2
            val dy = sin(angle).toFloat() * span / 2
            return listOf(cx - dx to cy - dy, cx + dx to cy + dy)
        }
        val down = SystemClock.uptimeMillis()
        val start = points(0f)
        send(MotionEvent.ACTION_DOWN, down, down, start.take(1))
        send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), down, down, start)
        frames(durationMs, onFrame = { t ->
            send(MotionEvent.ACTION_MOVE, down, SystemClock.uptimeMillis(), points(t))
        }) {
            val now = SystemClock.uptimeMillis()
            val end = points(1f)
            send(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), down, now, end)
            send(MotionEvent.ACTION_UP, down, now, end.take(1))
            done()
        }
    }

    /** A fast swipe released while moving: MapLibre continues with its fling animation. */
    fun fling(x0: Float, y0: Float, x1: Float, y1: Float, durationMs: Long, done: () -> Unit) {
        val down = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, down, down, listOf(x0 to y0))
        frames(durationMs, onFrame = { t ->
            send(MotionEvent.ACTION_MOVE, down, SystemClock.uptimeMillis(), listOf(x0 + (x1 - x0) * t to y0 + (y1 - y0) * t))
        }) {
            send(MotionEvent.ACTION_UP, down, SystemClock.uptimeMillis(), listOf(x1 to y1))
            done()
        }
    }

    private fun frames(durationMs: Long, onFrame: (Float) -> Unit, done: () -> Unit) {
        val started = System.nanoTime()
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                val t = ((System.nanoTime() - started) / 1e6 / durationMs).toFloat().coerceIn(0f, 1f)
                onFrame(t)
                if (t < 1f) choreographer.postFrameCallback(this) else done()
            }
        }
        choreographer.postFrameCallback(callback)
    }

    private fun send(action: Int, downTime: Long, eventTime: Long, points: List<Pair<Float, Float>>) {
        val properties = Array(points.size) { i ->
            MotionEvent.PointerProperties().apply {
                id = i
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coords = Array(points.size) { i ->
            MotionEvent.PointerCoords().apply {
                x = points[i].first
                y = points[i].second
                pressure = 1f
                size = 1f
            }
        }
        val event = MotionEvent.obtain(
            downTime, eventTime, action, points.size, properties, coords,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
        )
        view.dispatchTouchEvent(event)
        event.recycle()
    }
}
