package com.onezee.triptrack.map.fog

import com.onezee.triptrack.map.BuildConfig

/**
 * The MapLibre the fog's C++ host was compiled for (core/map/maplibre-abi.properties) against the one
 * actually loaded.
 *
 * The build and MapLibreAbiPinTest stop a mismatch long before a phone sees it; this is the last line,
 * for a dependency that drags another MapLibre into the app through conflict resolution. A host
 * called through a foreign vtable crashes the first frame, so a mismatch installs no fog at all.
 */
internal object MapLibreAbi {
    val pinnedVersion: String = BuildConfig.MAPLIBRE_ABI_VERSION

    /** Read by reflection: as a compile-time constant it would be inlined from the compile classpath. */
    fun loaded(): Pair<String?, String?> = try {
        val config = Class.forName("org.maplibre.android.BuildConfig")
        config.getField("MAPLIBRE_VERSION_STRING").get(null) as? String to config.getField("FLAVOR").get(null) as? String
    } catch (_: ReflectiveOperationException) {
        null to null
    }

    /** The custom layer is GL: a Vulkan build of the same version would not call it at all. */
    fun matches(versionString: String?, flavor: String?): Boolean =
        versionString == "MapLibre Android/$pinnedVersion" && flavor == "opengl"

    val matchesLoaded: Boolean by lazy { loaded().let { (version, flavor) -> matches(version, flavor) } }
}
