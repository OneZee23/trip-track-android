package com.onezee.triptrack.map.label

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.onezee.triptrack.logic.RegionLabelAnnotation
import com.onezee.triptrack.map.fog.FogPalette
import com.onezee.triptrack.model.GeoBounds
import com.onezee.triptrack.model.GeoPoint
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// iOS: RegionLabelAnnotation.swift (RegionLabelView). The native text view needs neither GL nor
// tiles: its measured frame, inert hit target and single accessible caption can be checked here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RegionLabelViewTests {
    private val context get() = RuntimeEnvironment.getApplication()
    private val annotation = RegionLabelAnnotation("RU-KDA", GeoPoint(45.0, 39.0), "КРАСНОДАРСКИЙ КРАЙ", "143 км",
        GeoBounds(44.0, 46.5, 36.5, 41.0))

    @Test fun frameFitsTheTextAndSurvivesParentLayout() {
        val root = FrameLayout(context)
        val label = RegionLabelView(context, annotation, 1f)
        root.addView(label, FrameLayout.LayoutParams(-2, -2))
        label.translationX = 80f
        label.translationY = 160f
        val spec = View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)
        root.measure(spec, spec)
        root.layout(0, 0, 800, 800)
        assertTrue(label.width in 100..300, "Label width ${label.width}; name ${(label.getChildAt(0) as TextView).measuredWidth}")
        assertTrue(label.height in 15..40, "Label height ${label.height}")
        assertEquals(80f, label.translationX)
        assertEquals(160f, label.translationY)
        val bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val pixels = IntArray(800 * 800)
        bitmap.getPixels(pixels, 0, 800, 0, 0, 800, 800)
        assertTrue(pixels.any { it ushr 24 != 0 })
    }

    @Test fun captionIsOneAccessibleElementAndNeverTakesTheRoadTap() {
        val label = RegionLabelView(context, annotation, 1f)
        assertEquals("КРАСНОДАРСКИЙ КРАЙ, 143 км", label.contentDescription.toString())
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, label.importantForAccessibility)
        assertFalse(label.isClickable)
        for (index in 0 until label.childCount) {
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, label.getChildAt(index).importantForAccessibility)
            assertFalse(label.getChildAt(index).isClickable)
        }
    }

    @Test fun textUsesTheFogPaletteAndFixedInterSizes() {
        val label = RegionLabelView(context, annotation, 2f)
        val name = label.getChildAt(0) as TextView
        val km = label.getChildAt(1) as TextView
        assertEquals(22f, name.textSize)
        assertEquals(18f, km.textSize)
        assertEquals(.08f, name.letterSpacing)
        label.setPalette(FogPalette.NIGHT)
        assertEquals(0xD9E4DCCF.toInt(), name.currentTextColor)
        label.setPalette(FogPalette.MIST)
        assertEquals(0xD91E2230.toInt(), name.currentTextColor)
    }
}
