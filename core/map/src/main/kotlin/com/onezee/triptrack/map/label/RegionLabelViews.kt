package com.onezee.triptrack.map.label

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.onezee.triptrack.designsystem.R
import com.onezee.triptrack.logic.MapPoint
import com.onezee.triptrack.logic.MapRect
import com.onezee.triptrack.logic.RegionLabelAnnotation
import com.onezee.triptrack.logic.RegionLabelLOD
import com.onezee.triptrack.map.MapPoints
import com.onezee.triptrack.map.fog.FogPalette
import com.onezee.triptrack.map.marker.MapMarker
import kotlin.math.min
import kotlin.math.roundToInt
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

// iOS: RegionLabelAnnotation.swift, MyMapRepresentable.updateLabelVisibility.
// Android views keep Inter and one TalkBack element per label, without a network glyph font.
internal class RegionLabelViews(private val mapView: MapView, private val density: Float) {
    private val overlay = FrameLayout(mapView.context).apply {
        clipChildren = false
        clipToPadding = false
    }
    private var labels: List<RegionLabelAnnotation> = emptyList()
    private var views: List<RegionLabelView> = emptyList()
    private var palette = FogPalette.MIST

    init {
        mapView.addView(overlay, FrameLayout.LayoutParams(-1, -1))
    }

    fun setLabels(value: List<RegionLabelAnnotation>) {
        if (value === labels || value == labels) return
        labels = value
        overlay.removeAllViews()
        views = value.map { label ->
            RegionLabelView(mapView.context, label, density).also {
                it.setPalette(palette)
                overlay.addView(it, FrameLayout.LayoutParams(-2, -2))
                it.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                it.layout(0, 0, it.measuredWidth, it.measuredHeight)
            }
        }
    }

    fun setPalette(value: FogPalette) {
        if (value == palette) return
        palette = value
        views.forEach { it.setPalette(value) }
    }

    fun update(map: MapLibreMap, safeTop: Double, showsUserLocation: Boolean, markers: List<MapMarker>) {
        if (views.isEmpty() || mapView.width <= 1) return
        val scale = MapPoints.pointsPerMapPoint(map.cameraPosition.zoom)
        val component = map.locationComponent
        val location = if (showsUserLocation && component.isLocationComponentActivated && component.isLocationComponentEnabled) {
            component.lastKnownLocation
        } else null
        val dot = location?.let { map.projection.toScreenLocation(LatLng(it.latitude, it.longitude)) }
            ?.let { MapPoint(it.x / density.toDouble(), it.y / density.toDouble()) }
        val viewport = MapRect(0.0, 0.0, mapView.width / density.toDouble(), mapView.height / density.toDouble())
        // Like MapKit's low annotation priority: a pin or a previously placed name gets the space.
        val occupied = markers.map { marker ->
            val p = map.projection.toScreenLocation(LatLng(marker.position.latitude, marker.position.longitude))
            val radius = 22.0
            val callout = marker.callout
            val width = maxOf(radius * 2, (callout?.width ?: 0) / density.toDouble())
            val height = (callout?.height ?: 0) / density.toDouble()
            MapRect(p.x / density - width / 2, p.y / density - radius - height, width, radius * 2 + height)
        }.toMutableList()
        labels.forEachIndexed { index, label ->
            val view = views[index]
            val size = MapPoints.rect(label.bounds)
            val point = map.projection.toScreenLocation(LatLng(label.coordinate.latitude, label.coordinate.longitude))
            val width = view.measuredWidth / density.toDouble()
            val height = view.measuredHeight / density.toDouble()
            val frame = MapRect(point.x / density - width / 2, point.y / density - height / 2, width, height)
            val visible = RegionLabelLOD.level(min(size.width, size.height) * scale) && frame.intersects(viewport) &&
                !RegionLabelLOD.yieldsToUserDot(frame, dot) && !RegionLabelLOD.yieldsToStatusBar(frame, safeTop) &&
                occupied.none { it.intersects(frame) }
            view.visibility = if (visible) View.VISIBLE else View.INVISIBLE
            if (visible) {
                occupied += frame
                view.translationX = (frame.x * density).toFloat()
                view.translationY = (frame.y * density).toFloat()
            }
        }
    }
}

// iOS: RegionLabelAnnotation.swift (RegionLabelView). Taps reach the road beneath the inert text.
internal class RegionLabelView(context: Context, label: RegionLabelAnnotation, density: Float) : LinearLayout(context) {
    private val name = HaloText(context).apply {
        text = label.name
        typeface = context.resources.getFont(R.font.inter_semibold)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, 11 * density)
    }
    private val km = HaloText(context).apply {
        text = label.kmLine
        typeface = context.resources.getFont(R.font.inter_medium)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, 9 * density)
        visibility = if (label.kmLine == null) GONE else VISIBLE
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        clipChildren = false
        clipToPadding = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = listOfNotNull(label.name, label.kmLine).joinToString(", ")
        addView(name, LayoutParams(-2, -2))
        addView(km, LayoutParams(-2, -2).apply { topMargin = density.roundToInt() })
        setPalette(FogPalette.MIST)
    }

    fun setPalette(palette: FogPalette) {
        val color = if (palette.isDark) 0xD9E4DCCF.toInt() else 0xD91E2230.toInt()
        val halo = if (palette.isDark) 0xCC1E2230.toInt() else 0xD9FFFFFF.toInt()
        for (view in listOf(name, km)) {
            view.halo = halo
            view.setTextColor(color)
            view.setShadowLayer(2 * resources.displayMetrics.density, 0f, 0f, halo)
        }
    }
}

// iOS: RegionLabelAnnotation.swift (RegionLabelView's negative 2% stroke).
private class HaloText(context: Context) : TextView(context) {
    var halo: Int = 0
    init {
        includeFontPadding = false
        letterSpacing = 0.08f
        gravity = Gravity.CENTER
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        val textLayout = layout ?: return
        val save = canvas.save()
        canvas.translate(compoundPaddingLeft.toFloat(), extendedPaddingTop.toFloat())
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = textSize * 0.02f
        paint.color = halo
        textLayout.draw(canvas)
        paint.style = Paint.Style.FILL
        paint.color = currentTextColor
        textLayout.draw(canvas)
        canvas.restoreToCount(save)
    }
}
