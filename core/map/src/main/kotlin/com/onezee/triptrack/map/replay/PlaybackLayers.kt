package com.onezee.triptrack.map.replay

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import com.onezee.triptrack.map.R
import com.onezee.triptrack.map.route.RouteLayers
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection

// iOS: MapCarMarker.swift / MapCarAnnotationView. One moving source, stable layers and cached paint.
internal class PlaybackLayers(private val resources: Resources, private val density: Float) {
    private var paint: Int? = null
    private var mood: MapCarMood? = null
    private var position: com.onezee.triptrack.model.GeoPoint? = null

    fun addTo(style: Style, playback: MapPlayback?) {
        paint = null
        mood = null
        position = null
        style.addSource(GeoJsonSource(SOURCE))
        style.addLayer(SymbolLayer(CAR, SOURCE).withProperties(
            iconAllowOverlap(true), iconIgnorePlacement(true), iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
        ))
        style.addImage(PAUSE_IMAGE, pausePill())
        style.addLayer(SymbolLayer(PAUSE, SOURCE).withProperties(iconImage(PAUSE_IMAGE), iconAllowOverlap(true), iconIgnorePlacement(true),
            iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT), iconOffset(arrayOf(12f, 12f))))
        update(style, playback)
    }

    fun update(style: Style, playback: MapPlayback?) {
        val source = style.getSourceAs<GeoJsonSource>(SOURCE) ?: return
        if (playback == null) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
            position = null
            return
        }
        if (paint != playback.colorArgb || mood != playback.mood) {
            paint = playback.colorArgb
            mood = playback.mood
            val color = MapCarPaint.color(playback.colorArgb, playback.mood)
            style.addImage(IMAGE, car(color))
            style.addImage(DOT, dot(color))
        }
        style.getLayerAs<SymbolLayer>(CAR)?.setProperties(
            iconImage(if (playback.collapsed) DOT else IMAGE), iconRotate(playback.course.toFloat()),
            iconOpacity((playback.mood.alpha * playback.opacity).toFloat()),
        )
        style.getLayerAs<SymbolLayer>(PAUSE)?.setProperties(visibility(if (playback.mood == MapCarMood.paused && !playback.collapsed) Property.VISIBLE else Property.NONE))
        if (position != playback.position) {
            position = playback.position
            source.setGeoJson(RouteLayers.point(playback.position))
        }
    }

    private fun car(argb: Int): Bitmap {
        val color = argb
        val shade = Color.rgb((Color.red(color) * .78).toInt(), (Color.green(color) * .78).toInt(), (Color.blue(color) * .78).toInt())
        val size = (44 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bounds = RectF(0f, 0f, size.toFloat(), size.toFloat())
        val brush = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        // iOS: MapCarMarker.shadowDiameter, a circular shadow that does not turn with the body.
        brush.color = 0x30000000
        canvas.drawCircle(size / 2f, size / 2f, 10 * density, brush)
        for ((asset, tint) in listOf(R.drawable.map_car_body to color, R.drawable.map_car_shade to shade, R.drawable.map_car_ink to null)) {
            val mask = BitmapFactory.decodeResource(resources, asset)
            brush.colorFilter = tint?.let { PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN) }
            canvas.drawBitmap(mask, null, bounds, brush)
            mask.recycle()
        }
        return bitmap
    }

    private fun pausePill(): Bitmap {
        val bitmap = Bitmap.createBitmap((20 * density).toInt(), (14 * density).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val brush = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawRoundRect(RectF(0f, 0f, 20 * density, 14 * density), 7 * density, 7 * density, brush)
        brush.color = 0xFF1C1A17.toInt()
        canvas.drawRect(7 * density, 4 * density, 9 * density, 10 * density, brush)
        canvas.drawRect(11 * density, 4 * density, 13 * density, 10 * density, brush)
        return bitmap
    }

    private fun dot(argb: Int): Bitmap {
        val size = (13 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val brush = Paint(Paint.ANTI_ALIAS_FLAG)
        brush.color = Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, brush)
        brush.color = argb
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2 * density, brush)
        return bitmap
    }

    companion object {
        private const val SOURCE = "triptrack-playback"
        private const val CAR = "triptrack-playback-car"
        private const val IMAGE = "triptrack-playback-image"
        private const val DOT = "triptrack-playback-dot"
        private const val PAUSE = "triptrack-playback-pause"
        private const val PAUSE_IMAGE = "triptrack-playback-pause-image"
    }
}
