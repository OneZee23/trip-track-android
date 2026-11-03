package com.onezee.triptrack.feature.social.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.onezee.triptrack.R
import com.onezee.triptrack.designsystem.R as DesignR
import com.onezee.triptrack.model.GeoPoint

// iOS: SharePosterView.swift and SharePosterRenderer.swift.
data class TripPosterSpec(
    val title: String,
    val distance: String,
    val duration: String,
    val averageSpeed: String,
    val coordinates: List<GeoPoint>,
)

class TripPosterView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var spec: TripPosterSpec = TripPosterSpec("", "", "", "", emptyList())
        set(value) { field = value; invalidate() }
    var backgroundMode: String = "map"
        set(value) { field = value; invalidate() }
    var photoBitmap: Bitmap? = null
        set(value) { field = value; invalidate() }
    var transparent: Boolean = false
        set(value) { field = value; invalidate() }

    private val regular by lazy { ResourcesCompat.getFont(context, DesignR.font.inter_regular) ?: Typeface.create("sans-serif", Typeface.NORMAL) }
    private val bold by lazy { ResourcesCompat.getFont(context, DesignR.font.inter_extrabold) ?: Typeface.create("sans-serif", Typeface.BOLD) }
    private val pressStart by lazy { ResourcesCompat.getFont(context, DesignR.font.press_start_2p_regular) ?: Typeface.create("monospace", Typeface.BOLD) }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val car by lazy { BitmapFactory.decodeResource(resources, R.drawable.poster_car) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawPoster(canvas, width.toFloat().coerceAtLeast(1f), height.toFloat().coerceAtLeast(1f))
    }

    fun renderBitmap(width: Int = 1080, height: Int = width * 5 / 4): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
        drawPoster(Canvas(it), width.toFloat(), height.toFloat())
    }

    private fun drawPoster(canvas: Canvas, w: Float, h: Float) {
        val scale = w / 201f
        if (transparent) canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR) else {
            paint.shader = LinearGradient(0f, h * .07f, w, h * .79f, 0xff29364f.toInt(), 0xff161b2c.toInt(), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
            if (backgroundMode == "map") drawRoads(canvas, w, h, scale)
            if (backgroundMode == "photo") photoBitmap?.let { photo ->
                val crop = maxOf(w / photo.width, h / photo.height)
                val dw = photo.width * crop; val dh = photo.height * crop
                paint.alpha = 255
                canvas.drawBitmap(photo, null, android.graphics.RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), paint)
                paint.color = 0x38303850
                canvas.drawRect(0f, 0f, w, h, paint)
            }
        }
        drawRoute(canvas, w, h, scale)
        if (!transparent) {
            val scrim = Paint().apply { shader = LinearGradient(0f, h * .48f, 0f, h, intArrayOf(0x00000000, 0xc0000000.toInt()), null, Shader.TileMode.CLAMP) }
            canvas.drawRect(0f, h * .44f, w, h, scrim)
        }

        val carHeight = 18f * scale
        val carWidth = carHeight * car.width / car.height
        val carPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(car, null, android.graphics.RectF(14f * scale, 13f * scale, 14f * scale + carWidth, 13f * scale + carHeight), carPaint)
        paint.color = -1
        paint.typeface = pressStart
        paint.textSize = 7f * scale
        paint.setShadowLayer(4f * scale, 0f, scale, 0x80000000.toInt())
        canvas.drawText("TRIP", 14f * scale + carWidth + 7f * scale, 26f * scale, paint)
        canvas.drawText("TRACK", 14f * scale + carWidth + 7f * scale + paint.measureText("TRIP") + 5f * scale, 26f * scale, paint)
        paint.clearShadowLayer()

        paint.color = -1
        paint.typeface = bold
        paint.textSize = 19f * scale
        paint.setShadowLayer(6f * scale, 0f, scale, 0x99000000.toInt())
        val lines = splitTitle(spec.title, paint, w - 30f * scale)
        val titleY = h - 48f * scale - (lines.size - 1) * 22f * scale
        lines.take(2).forEachIndexed { index, line -> canvas.drawText(line, 16f * scale, titleY + index * 22f * scale, paint) }
        val metrics = listOf(spec.distance, spec.duration, spec.averageSpeed).joinToString("     ")
        paint.typeface = regular
        paint.textSize = 11f * scale
        if (transparent) {
            paint.clearShadowLayer()
            paint.color = 0x6b000000
            canvas.drawRoundRect(android.graphics.RectF(8f * scale, h - 55f * scale, w - 8f * scale, h - 6f * scale), 8f * scale, 8f * scale, paint)
            paint.color = -1
            paint.typeface = regular
            paint.textSize = 11f * scale
            paint.setShadowLayer(6f * scale, 0f, scale, 0x99000000.toInt())
        }
        canvas.drawText(metrics, 16f * scale, h - 20f * scale, paint)
        paint.clearShadowLayer()
    }

    private fun drawRoads(canvas: Canvas, w: Float, h: Float, s: Float) {
        paint.color = 0x183f5470
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * s
        for (i in 0..7) {
            val y = h * (0.18f + i * .09f)
            val path = Path().apply { moveTo(-w * .1f, y); cubicTo(w * .24f, y - 22f * s, w * .56f, y + 20f * s, w * 1.1f, y - 9f * s) }
            canvas.drawPath(path, paint)
        }
        for (i in 0..5) {
            val x = w * (0.08f + i * .18f)
            val path = Path().apply { moveTo(x, 0f); cubicTo(x - 12f * s, h * .25f, x + 14f * s, h * .62f, x - 6f * s, h) }
            canvas.drawPath(path, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawRoute(canvas: Canvas, w: Float, h: Float, s: Float) {
        val points = project(spec.coordinates, w, h, 18f * s)
        if (points.size < 2) return
        val path = Path().apply { moveTo(points.first().x, points.first().y); points.drop(1).forEach { lineTo(it.x, it.y) } }
        val routeWidth = if (transparent) 9f else 7f
        paint.color = 0x6b70c8ff
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = routeWidth * s
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(path, paint)
        paint.color = 0xff70c8ff.toInt()
        paint.strokeWidth = (routeWidth * .57f) * s
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        val start = points.first(); val end = points.last()
        paint.color = -1; canvas.drawCircle(start.x, start.y, 5f * s, paint)
        paint.color = 0xff70c8ff.toInt(); canvas.drawCircle(start.x, start.y, 3f * s, paint); canvas.drawCircle(end.x, end.y, 5f * s, paint)
    }

    private fun splitTitle(text: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        var line = ""
        for (word in text.split(' ')) {
            val next = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && paint.measureText(next) > maxWidth) { result += line; line = word } else line = next
        }
        if (line.isNotEmpty()) result += line
        return result.ifEmpty { listOf("") }
    }

    private fun project(points: List<GeoPoint>, w: Float, h: Float, inset: Float): List<android.graphics.PointF> {
        if (points.size < 2) return emptyList()
        val minX = points.minOf { it.longitude }; val maxX = points.maxOf { it.longitude }
        val minY = points.minOf { it.latitude }; val maxY = points.maxOf { it.latitude }
        val dx = (maxX - minX).coerceAtLeast(.00001); val dy = (maxY - minY).coerceAtLeast(.00001)
        val fitted = minOf((w - inset * 2) / dx, (h * .64f - inset * 2) / dy)
        val centerX = (minX + maxX) / 2; val centerY = (minY + maxY) / 2
        return points.map { android.graphics.PointF(w / 2 + ((it.longitude - centerX) * fitted).toFloat(), h * .42f - ((it.latitude - centerY) * fitted).toFloat()) }
    }
}
