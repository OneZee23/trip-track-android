// iOS: AtlasSharePoster.swift (make, render, drawVeil, drawCaption, present, PosterActivityItem)
package com.onezee.triptrack.feature.atlas.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.feature.atlas.AtlasSharePosterInputs
import com.onezee.triptrack.feature.atlas.AtlasState
import com.onezee.triptrack.logic.FogVeilRenderer
import com.onezee.triptrack.logic.distanceKm
import com.onezee.triptrack.logic.regionCount
import com.onezee.triptrack.map.MapPoints
import com.onezee.triptrack.map.fog.FogLook
import com.onezee.triptrack.map.fog.FogRules
import com.onezee.triptrack.map.snapshot.MapSnapshots
import com.onezee.triptrack.map.vein.VeinStyle
import com.onezee.triptrack.model.DistanceUnit
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.ui.LiveMaps
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * iOS: AtlasSharePoster. «Поделиться» hands over a PICTURE, the map with the same fog as the screen,
 * and the summary line with it: the text goes where a picture does not unfold. No snapshot (no
 * network: the tiles come from it) means the text alone; refusing to share for a picture is wrong.
 *
 * The map is MapLibre's snapshotter of the day style; the fog is drawn here on the CPU with the
 * screen's numbers, at the poster's own scale: a flat colour × alpha, cut by the corridors' coverage
 * united by MAX, with the feather 0.4 × half width cut inward. MapLibre's snapshotter draws no
 * custom layer, as MKMapSnapshotter draws no overlays.
 */
object AtlasSharePoster {
    /** iOS renderPointSize 540 × 960 at scale 3; caption strip 148. */
    const val WIDTH = 540
    const val HEIGHT = 960
    const val SCALE = 3f
    const val CAPTION_HEIGHT = 148f

    /** Rendered and handed to the share sheet; false when no picture could be made. */
    suspend fun share(context: Context, state: AtlasState, strings: AppStrings, unit: DistanceUnit): Boolean {
        val words = strings.language.code
        val distance = Measure.distanceKm(state.revealed.openedKm, unit, words, strings)
        val caption = strings.posterCaption(distance, state.journal.finds.size)
        val text = summary(state, strings, unit)
        val image = AtlasSharePosterInputs.from(state, caption)?.let { make(context, it, strings) }
        present(context, image, text, strings.myMapTitle())
        return image != null
    }

    /**
     * «1 910 км открыто · 4 региона»: the kilometres of the OPENED layer, not the sum of trips; a zero
     * of seals is not printed (iOS openedSummary).
     */
    fun summary(state: AtlasState, strings: AppStrings, unit: DistanceUnit): String {
        val parts = mutableListOf(strings.mapOpenedSummary(Measure.distanceKm(state.revealed.openedKm, unit, strings.language.code, strings),
            state.exploration.regionCount))
        if (state.journal.finds.isNotEmpty()) parts += strings.mapSealsCount(state.journal.finds.size)
        return parts.joinToString(" · ")
    }

    private suspend fun make(context: Context, inputs: AtlasSharePosterInputs, strings: AppStrings): Bitmap? {
        if (!LiveMaps.available) return null
        val snapshot = withTimeoutOrNull(SNAPSHOT_TIMEOUT_MS) {
            MapSnapshots.take(context, inputs.frame, inputs.width.toInt(), inputs.height.toInt(), inputs.scale.toFloat())
        } ?: return null
        return withContext(Dispatchers.Default) {
            if (looksUnrendered(snapshot.bitmap)) return@withContext null
            render(context, snapshot.bitmap, inputs, snapshot.attribution)
        }
    }

    /**
     * iOS looksUnrendered: a snapshot without tiles is a light, even grid, and in the cleared
     * corridors it would be WHITE. Light AND even, both at once, so a real light map is not eaten.
     */
    fun looksUnrendered(bitmap: Bitmap): Boolean {
        val step = max(1, minOf(bitmap.width, bitmap.height) / 24)
        var sum = 0.0
        var sumSquares = 0.0
        var count = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val p = bitmap.getPixel(x, y)
                val l = (0.299 * ((p shr 16) and 0xFF) + 0.587 * ((p shr 8) and 0xFF) + 0.114 * (p and 0xFF)) / 255
                sum += l
                sumSquares += l * l
                count++
                x += step
            }
            y += step
        }
        if (count == 0) return true
        val mean = sum / count
        val spread = kotlin.math.sqrt(maxOf(0.0, sumSquares / count - mean * mean))
        return mean > 0.86 && spread < 0.025
    }

    /** The composition, pure of the map: the snapshot, the fog, the vein, the caption. */
    fun render(context: Context, snapshot: Bitmap, inputs: AtlasSharePosterInputs, attribution: String): Bitmap {
        val out = Bitmap.createBitmap((inputs.width * inputs.scale).toInt(), (inputs.height * inputs.scale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(Bitmap.createScaledBitmap(snapshot, out.width, out.height, true), 0f, 0f, null)
        drawVeil(canvas, inputs)
        drawCaption(context, canvas, inputs, attribution)
        return out
    }

    private fun drawVeil(canvas: Canvas, inputs: AtlasSharePosterInputs) {
        val layer = inputs.layer
        if (layer.isEmpty) return
        val rect = MapPoints.rect(inputs.frame)
        val scale = inputs.scale.toFloat()
        val zoomScale = inputs.width / rect.width
        val lod = FogVeilRenderer.lod(zoomScale)
        val centreLat = MapPoints.latitude(rect.minY + rect.height / 2)
        val metresPerPoint = MapPoints.metresPerMapPoint(centreLat) / zoomScale
        val halfWidth = FogRules.halfWidthPoints(metresPerPoint).toFloat()
        val feather = halfWidth * FogRules.FEATHER_RATIO.toFloat()
        val paths = layer.polylines(lod).filter { it.size > 1 }.map { line ->
            Path().apply {
                line.forEachIndexed { index, point ->
                    val x = ((MapPoints.x(point.longitude) - rect.minX) / rect.width * inputs.width * scale).toFloat()
                    val y = ((MapPoints.y(point.latitude) - rect.minY) / rect.height * inputs.height * scale).toFloat()
                    if (index == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
        }

        // Coverage in the red channel of an opaque bitmap: LIGHTEN over opaque pixels is a MAX per
        // channel, so overlapping corridors unite without beads at the joints.
        val coverage = Bitmap.createBitmap(canvas.width, canvas.height, Bitmap.Config.ARGB_8888)
        val cover = Canvas(coverage)
        cover.drawColor(android.graphics.Color.BLACK)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            xfermode = PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
        }
        for (k in 0..FEATHER_STEPS) {
            val radius = halfWidth - feather + feather * k / FEATHER_STEPS
            val value = 1 - smoothstep(halfWidth - feather, halfWidth, radius - feather / (2 * FEATHER_STEPS))
            val level = (value * 255).toInt().coerceIn(0, 255)
            stroke.color = android.graphics.Color.rgb(level, level, level)
            stroke.strokeWidth = 2 * radius * scale
            for (path in paths) cover.drawPath(path, stroke)
        }

        // a = alpha × (1 − coverage): the flat fog, with the coverage taken out of its alpha.
        val look = FogLook.of(inputs.appearance.model())
        val palette = look.palette
        val fog = Bitmap.createBitmap(canvas.width, canvas.height, Bitmap.Config.ARGB_8888)
        val fogCanvas = Canvas(fog)
        fogCanvas.drawColor(android.graphics.Color.argb((palette.alpha * 255).toInt(), palette.red, palette.green, palette.blue))
        val mask = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f)))
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
        fogCanvas.drawBitmap(coverage, 0f, 0f, mask)
        canvas.drawBitmap(fog, 0f, 0f, null)
        coverage.recycle()
        fog.recycle()

        // The screen draws the vein above the fog; the poster needs the same strokes.
        val vein = VeinStyle.veinColour(palette)
        val core = VeinStyle.width(lod).toFloat() * scale
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        VeinStyle.halo(lod)?.let { (width, alpha) ->
            line.strokeWidth = width.toFloat() * scale
            line.color = withAlpha(vein, alpha)
            for (path in paths) canvas.drawPath(path, line)
        }
        VeinStyle.casing(palette)?.let { casing ->
            line.strokeWidth = core + VeinStyle.CASING_EXTRA.toFloat() * scale
            line.color = casing
            for (path in paths) canvas.drawPath(path, line)
        }
        line.strokeWidth = core
        line.color = withAlpha(vein, VeinStyle.LINE_ALPHA)
        for (path in paths) canvas.drawPath(path, line)
    }

    /**
     * iOS drawCaption: the caption is CHROME over the picture, readable on any palette and map, so its
     * ramp is BLACK: 0 → 0.72 at 55 % → 0.88. Inter ExtraBold 27 shrunk to the width down to 17 (the
     * German line is a quarter longer than the Russian; an ellipsis would lose the number), and the
     * «TripTrack» mark under it. Android adds the map's attribution in the corner: the tiles' licence
     * asks for it, MapKit's snapshot carried Apple's own.
     */
    private fun drawCaption(context: Context, canvas: Canvas, inputs: AtlasSharePosterInputs, attribution: String) {
        val scale = inputs.scale.toFloat()
        val width = inputs.width.toFloat() * scale
        val height = inputs.height.toFloat() * scale
        val top = height - CAPTION_HEIGHT * scale
        val ramp = Paint().apply {
            shader = LinearGradient(0f, top, 0f, height, intArrayOf(
                android.graphics.Color.argb(0, 0, 0, 0), android.graphics.Color.argb((0.72 * 255).toInt(), 0, 0, 0),
                android.graphics.Color.argb((0.88 * 255).toInt(), 0, 0, 0)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, top, width, height, ramp)
        fun font(id: Int) = runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        val inset = 32 * scale
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font(com.onezee.triptrack.designsystem.R.font.inter_extrabold)
            color = android.graphics.Color.WHITE
        }
        var size = 27f
        while (size > 17f) {
            title.textSize = size * scale
            if (title.measureText(inputs.caption) <= width - inset * 2) break
            size -= 1f
        }
        title.textSize = size * scale
        val metrics = title.fontMetrics
        val lineHeight = metrics.descent - metrics.ascent
        canvas.drawText(inputs.caption, inset, height - 84 * scale - lineHeight / 2 - metrics.ascent, title)
        val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font(com.onezee.triptrack.designsystem.R.font.inter_semibold)
            color = android.graphics.Color.argb((0.72 * 255).toInt(), 255, 255, 255)
            textSize = 15 * scale
            letterSpacing = 0.6f / 15f
        }
        canvas.drawText("TripTrack", inset, height - 46 * scale - mark.fontMetrics.ascent, mark)
        val credit = Paint(mark).apply {
            textSize = 9 * scale
            letterSpacing = 0f
            color = android.graphics.Color.argb((0.55 * 255).toInt(), 255, 255, 255)
            textAlign = Paint.Align.RIGHT
        }
        if (attribution.isNotEmpty()) canvas.drawText(attribution, width - inset, height - 46 * scale - mark.fontMetrics.ascent, credit)
    }

    /**
     * iOS present / PosterActivityItem: a PNG file, not a bare bitmap, so the sheet names the
     * attachment and messengers send it as a photo. The summary line travels with it.
     */
    private suspend fun present(context: Context, image: Bitmap?, text: String, title: String) {
        val uri = image?.let { bitmap ->
            withContext(Dispatchers.IO) {
                runCatching {
                    val folder = File(File(context.cacheDir, "exports"), "atlas-poster").apply { deleteRecursively(); mkdirs() }
                    val file = File(folder, "TripTrack-Atlas.png")
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
                }.getOrNull()
            }
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_TITLE, title)
            if (uri != null) {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else type = "text/plain"
        }
        runCatching { context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    private fun withAlpha(argb: Int, alpha: Double): Int = ((alpha * 255).toInt().coerceIn(0, 255) shl 24) or (argb and 0xFFFFFF)

    /** Rings of the feather: the screen's smoothstep in eight steps of under two pixels each. */
    private const val FEATHER_STEPS = 8
    private const val SNAPSHOT_TIMEOUT_MS = 20_000L
}
