package com.onezee.triptrack.map.fog

import java.io.File
import java.security.MessageDigest
import java.util.Properties
import kotlin.test.*
import org.junit.Test

/**
 * The fog's C++ host is compiled against MapLibre headers copied by hand (the AAR ships none), and
 * MapLibre calls it through their vtable. A MapLibre upgrade that reorders the virtual methods or the
 * render parameters crashes the first frame and no compiler notices. The build's verifyMapLibreAbi
 * stops a catalog bump; this test also checks the AAR actually on the classpath was built from the
 * commit the headers come from, and that the runtime guard of FogLayer recognises it.
 */
class MapLibreAbiPinTest {
    private val root = File(System.getProperty("triptrack.rootDir") ?: "../..")
    private val pin = Properties().apply { File(root, "core/map/maplibre-abi.properties").inputStream().use(::load) }

    @Test
    fun theCatalogAsksForThePinnedMapLibre() {
        val catalog = File(root, "gradle/libs.versions.toml").readText()
        val version = Regex("""(?m)^maplibre = "([^"]+)"""").find(catalog)?.groupValues?.get(1)
        assertEquals(pin.getProperty("version"), version, "MapLibre was bumped without re-copying the custom layer headers")
        assertEquals("android-v${pin.getProperty("version")}", pin.getProperty("tag"))
        assertEquals(pin.getProperty("version"), MapLibreAbi.pinnedVersion)
    }

    @Test
    fun theCopiedHeadersAreVerbatim() {
        val dir = File(root, "core/map/src/main/cpp/maplibre/include/mln/style/layers")
        val recorded = pin.stringPropertyNames().filter { it.startsWith("sha256.") }.associate { it.removePrefix("sha256.") to pin.getProperty(it) }
        assertEquals(setOf("custom_layer_host.hpp", "custom_layer_init_parameters.hpp", "custom_layer_render_parameters.hpp"), recorded.keys)
        assertEquals(recorded.keys, dir.listFiles().orEmpty().map { it.name }.toSet())
        for ((name, hash) in recorded) {
            val actual = MessageDigest.getInstance("SHA-256").digest(File(dir, name).readBytes()).joinToString("") { "%02x".format(it) }
            assertEquals(hash, actual, "$name was edited; it must stay a verbatim copy of ${pin.getProperty("tag")}")
        }
    }

    // The resolved artifact, not just the catalog line: a transitive bump would change these.
    @Test
    fun theMapLibreOnTheClasspathIsTheOneTheHeadersCameFrom() {
        val config = Class.forName("org.maplibre.android.BuildConfig")
        assertEquals(pin.getProperty("commit"), config.getField("GIT_REVISION").get(null))
        assertEquals("MapLibre Android/${pin.getProperty("version")}", config.getField("MAPLIBRE_VERSION_STRING").get(null))
        assertEquals("opengl", config.getField("FLAVOR").get(null), "the custom layer is GL; a Vulkan build never calls it")
        assertTrue(MapLibreAbi.matches(MapLibreAbi.loaded().first, MapLibreAbi.loaded().second))
    }

    @Test
    fun theRuntimeGuardRefusesAnyOtherMapLibre() {
        val pinned = MapLibreAbi.pinnedVersion
        assertTrue(MapLibreAbi.matches("MapLibre Android/$pinned", "opengl"))
        assertFalse(MapLibreAbi.matches("MapLibre Android/$pinned", "vulkan"))
        assertFalse(MapLibreAbi.matches("MapLibre Android/13.8.0", "opengl"))
        assertFalse(MapLibreAbi.matches(null, null))
    }
}
