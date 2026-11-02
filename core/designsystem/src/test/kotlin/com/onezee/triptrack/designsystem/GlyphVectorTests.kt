package com.onezee.triptrack.designsystem

import com.onezee.triptrack.designsystem.components.AppGlyph
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlyphVectorTests {
    @Test fun everyAppGlyphHasAnSvgSourceAndDrawable() {
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "src/main/res/drawable").isDirectory && File(it, "glyphs").isDirectory }
        val svgNames = File(root, "glyphs").listFiles()!!.map { it.name }.toSet()
        val drawableNames = File(root, "src/main/res/drawable").listFiles()!!
            .filter { it.name.startsWith("glyph_") && it.extension == "xml" }
            .map { it.name }.toSet()
        val expectedSvg = AppGlyph.entries.map { it.name.toKebabCase() + ".svg" }.toSet()
        val expectedDrawables = AppGlyph.entries.map { "glyph_${it.name.toSnakeCase()}.xml" }.toSet()

        assertEquals(expectedSvg, svgNames)
        assertEquals(expectedDrawables, drawableNames)
    }

    @Test fun glyphRenderingDoesNotDrawIntoCanvas() {
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "src/main/kotlin").isDirectory && File(it, "glyphs").isDirectory }
        val glyphSource = File(root, "src/main/kotlin/com/onezee/triptrack/designsystem/components/Glyph.kt")
        assertTrue("Canvas(" !in glyphSource.readText())
        assertTrue(!File(glyphSource.parentFile, "GlyphsMore.kt").exists())
        val canvasFiles = File(root, "src/main/kotlin").walkTopDown()
            .filter { it.extension == "kt" && "Canvas(" in it.readText() }
            .map { it.relativeTo(File(root, "src/main/kotlin")).invariantSeparatorsPath }
            .toSet()
        assertEquals(emptySet(), canvasFiles)
    }

    private fun String.toKebabCase() = replace(Regex("([a-z0-9])([A-Z])"), "$1-$2").lowercase()
    private fun String.toSnakeCase() = toKebabCase().replace('-', '_')
}
