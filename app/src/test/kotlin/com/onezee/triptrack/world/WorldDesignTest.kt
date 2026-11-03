package com.onezee.triptrack.world

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import kotlin.math.abs
import kotlin.test.assertTrue

/** What a screenshot of glyph ink measures: its box in dp, from the node's own corner. */
internal data class Ink(val left: Float, val top: Float, val width: Float, val height: Float)

/**
 * The measuring tools of the design tests of the Atlas and Places tabs: a node's box in dp, a render of the whole
 * screen, and the ink of a glyph in it. The iPhone's sizes are in points, the same numbers as dp here.
 */
abstract class WorldDesignTest : WorldScreenTest() {
    protected val pxPerDp get() = compose.activity.resources.displayMetrics.density

    protected fun node(tag: String): SemanticsNodeInteraction = compose.onNodeWithTag(tag, useUnmergedTree = true)
    protected fun bounds(tag: String): Rect = node(tag).fetchSemanticsNode().boundsInRoot
    protected fun Float.inDp() = this / pxPerDp
    protected fun screenshot(): Bitmap { compose.waitForIdle(); return compose.onRoot().captureToImage().asAndroidBitmap() }
    protected fun assertNear(expected: Float, actual: Float, tolerance: Float, message: String) =
        assertTrue(abs(expected - actual) <= tolerance, "$message: $actual is not within $tolerance of $expected")

    /**
     * The box of the pixels that differ from the corner of the scanned region by more than half of the strongest
     * difference in it: the edge of an antialiased drawing at half coverage, which is where a glyph's ink ends.
     */
    internal fun Bitmap.inkIn(box: Rect, growX: Float = 2f, growY: Float = 2f): Ink {
        val x0 = (box.left - growX * pxPerDp).toInt().coerceAtLeast(0)
        val y0 = (box.top - growY * pxPerDp).toInt().coerceAtLeast(0)
        val x1 = (box.right + growX * pxPerDp).toInt().coerceAtMost(width - 1)
        val y1 = (box.bottom + growY * pxPerDp).toInt().coerceAtMost(height - 1)
        val fill = getPixel(x0, y0)
        fun gap(pixel: Int) = abs(android.graphics.Color.red(pixel) - android.graphics.Color.red(fill)) +
            abs(android.graphics.Color.green(pixel) - android.graphics.Color.green(fill)) +
            abs(android.graphics.Color.blue(pixel) - android.graphics.Color.blue(fill))
        var strongest = 0
        for (y in y0..y1) for (x in x0..x1) strongest = maxOf(strongest, gap(getPixel(x, y)))
        var minX = Int.MAX_VALUE; var maxX = -1; var minY = Int.MAX_VALUE; var maxY = -1
        for (y in y0..y1) for (x in x0..x1) if (gap(getPixel(x, y)) * 2 > strongest) {
            minX = minOf(minX, x); maxX = maxOf(maxX, x); minY = minOf(minY, y); maxY = maxOf(maxY, y)
        }
        check(maxX >= 0) { "no ink at all in $box" }
        return Ink((minX - box.left) / pxPerDp, (minY - box.top) / pxPerDp, (maxX - minX + 1) / pxPerDp, (maxY - minY + 1) / pxPerDp)
    }

    /** The ink of the node [tag] against the iPhone's [width] by [height]. */
    protected fun assertInk(tag: String, width: Float, height: Float, tolerance: Float = 1.1f, growX: Float = 2f, growY: Float = 2f) {
        val ink = screenshot().inkIn(bounds(tag), growX, growY)
        assertTrue(abs(width - ink.width) <= tolerance && abs(height - ink.height) <= tolerance,
            "$tag ink is ${ink.width} × ${ink.height}, the iPhone's is $width × $height (within $tolerance)")
    }
}
