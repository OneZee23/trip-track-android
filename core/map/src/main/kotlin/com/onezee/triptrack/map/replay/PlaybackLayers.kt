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

    fun addTo(style: Style, playback: MapPlayback?) {
        paint = null
        style.addSource(GeoJsonSource(SOURCE))
        style.addLayer(SymbolLayer(CAR, SOURCE).withProperties(
            iconAllowOverlap(true), iconIgnorePlacement(true), iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
        ))
        update(style, playback)
    }

    fun update(style: Style, playback: MapPlayback?) {
        val source = style.getSourceAs<GeoJsonSource>(SOURCE) ?: return
        if (playback == null) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
            return
        }
        if (paint != playback.colorArgb) {
            paint = playback.colorArgb
            style.addImage(IMAGE, car(playback.colorArgb))
            style.addImage(DOT, dot(playback.colorArgb))
        }
        style.getLayerAs<SymbolLayer>(CAR)?.setProperties(
            iconImage(if (playback.collapsed) DOT else IMAGE), iconRotate(playback.course.toFloat()),
        )
        source.setGeoJson(RouteLayers.point(playback.position))
    }

    private fun paintColor(argb: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(argb, hsv)
        hsv[2] = hsv[2].coerceIn(0.22f, 0.86f)
        return Color.HSVToColor(hsv)
    }

    private fun car(argb: Int): Bitmap {
        val color = paintColor(argb)
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

    private fun dot(argb: Int): Bitmap {
        val size = (13 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val brush = Paint(Paint.ANTI_ALIAS_FLAG)
        brush.color = Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, brush)
        brush.color = paintColor(argb)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2 * density, brush)
        return bitmap
    }

    companion object {
        private const val SOURCE = "triptrack-playback"
        private const val CAR = "triptrack-playback-car"
        private const val IMAGE = "triptrack-playback-image"
        private const val DOT = "triptrack-playback-dot"
    }
}
