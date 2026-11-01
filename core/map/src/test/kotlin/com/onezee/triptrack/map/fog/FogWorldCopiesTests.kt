package com.onezee.triptrack.map.fog

import com.onezee.triptrack.map.MapPoints
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.*
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

// iOS: FogFrameEncoder.swift. JVM assertions run the production C++ range/nearest-copy functions,
// so changing native arithmetic cannot silently leave a Kotlin reference test green.
class FogWorldCopiesTests {
    private val world = MapPoints.WORLD

    private fun copies(min: Double, max: Double, left: Double, right: Double, pad: Double = 0.0): IntRange {
        val range = run(listOf(min, max, left, right, pad).map(Double::toString)).split(' ').map(String::toInt)
        return range[0]..range[1]
    }

    @Test fun ordinaryViewportDrawsOnlyTheOriginal() = assertEquals(0..0, copies(world * .5, world * .51, world * .4, world * .6))
    @Test fun eastOfTheAntimeridianDrawsTheWesternCopy() = assertEquals(-1..-1, copies(world - 20, world - 10, -30.0, 30.0))
    @Test fun westOfTheAntimeridianDrawsTheEasternCopy() = assertEquals(1..1, copies(10.0, 20.0, world - 30, world + 30))
    @Test fun severalWorldsFitWithoutACopyLimit() = assertEquals(-2..2, copies(world * .5, world * .51, -world * 2, world * 3))
    @Test fun offscreenChunksAreCulled() = assertTrue(copies(100.0, 120.0, 130.0, 160.0).isEmpty())
    @Test fun aCorridorPastTheEdgeStillPaintsIntoTheCopy() = assertEquals(-1..-1, copies(world - 10, world - 5, 0.0, 20.0, 5.0))
    @Test fun copyOffsetsFollowACameraManyWrapsAway() = assertEquals(7..7, copies(10.0, 20.0, 7 * world, 7 * world + 30))
    @Test fun revealUsesTheNearestWorldBeforeFloatConversion() {
        assertEquals(-10.0, run(listOf(world - 10, 5.0).map(Double::toString)).toDouble())
        assertEquals(world + 10, run(listOf(10.0, world - 5).map(Double::toString)).toDouble())
    }

    companion object {
        private lateinit var directory: File
        private lateinit var executable: File

        private fun command(args: List<String>): String {
            val process = ProcessBuilder(args).redirectErrorStream(true).start()
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Native geometry command timed out")
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.exitValue(), output)
            return output.trim()
        }

        @BeforeClass @JvmStatic fun compileGeometry() {
            val root = File(System.getProperty("triptrack.rootDir"))
            val source = root.resolve("core/map/src/main/cpp")
            directory = Files.createTempDirectory("fog-world-geometry").toFile()
            executable = directory.resolve("geometry")
            command(listOf("c++", "-std=c++20", "-Wall", "-Wextra", "-Werror", "-I${source.path}", "-I${source.path}/maplibre/include",
                root.resolve("core/map/src/test/cpp/fog_world_geometry.cpp").path, "-o", executable.path))
        }

        private fun run(args: List<String>): String = command(listOf(executable.path) + args)
        @AfterClass @JvmStatic fun removeGeometry() { if (::directory.isInitialized) directory.deleteRecursively() }
    }
}
