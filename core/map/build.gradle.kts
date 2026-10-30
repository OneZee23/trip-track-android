import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

// Where the C++ custom layer interface was copied from; see the comment in the file.
val mapLibreAbi = Properties().apply { file("maplibre-abi.properties").inputStream().use(::load) }

android {
    namespace = "com.onezee.triptrack.map"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }
    // Pinned to the NDK already in the SDK, as in the spike: the AGP default would download another.
    // NDK 28 links with 16 KB page alignment, which Play requires for Android 15+.
    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = 29
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "MAPLIBRE_ABI_VERSION", "\"${mapLibreAbi.getProperty("version")}\"")
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++20", "-fno-exceptions")
                // MapLibre links libc++ statically too; the host crosses the library boundary only
                // through its vtable, so a second static copy is safe.
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
        // No ABI filter: the fog runs on every ABI MapLibre ships (arm64-v8a, armeabi-v7a, x86,
        // x86_64), so a phone that can draw the map can draw the fog.
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // The map's public API speaks the domain types: GeoPoint, GeoBounds, RevealedLayer.
    api(project(":core:model"))
    api(project(":core:logic"))
    implementation(libs.maplibre.opengl)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}

// Fails the build when the MapLibre in the version catalog is not the one the C++ interface was
// copied from, or when a copied header no longer has the recorded hash (edited by hand).
val verifyMapLibreAbi = tasks.register("verifyMapLibreAbi") {
    group = "verification"
    description = "Checks that the copied MapLibre custom layer headers match the pinned MapLibre."
    val catalogVersion = libs.versions.maplibre.get()
    val pinned = mapLibreAbi.getProperty("version")
    val tag = mapLibreAbi.getProperty("tag")
    val headerDir = file("src/main/cpp/maplibre/include/mln/style/layers")
    val expected = mapLibreAbi.stringPropertyNames()
        .filter { it.startsWith("sha256.") }
        .associate { it.removePrefix("sha256.") to mapLibreAbi.getProperty(it) }
    val stamp = layout.buildDirectory.file("maplibre-abi/verified.txt")
    inputs.file("maplibre-abi.properties")
    inputs.dir(headerDir)
    inputs.property("catalogVersion", catalogVersion)
    outputs.file(stamp)
    doLast {
        check(catalogVersion == pinned) {
            "MapLibre is $catalogVersion in gradle/libs.versions.toml, but the fog's C++ custom layer " +
                "interface was copied from $tag. Copy the three headers from tag android-v$catalogVersion " +
                "into ${headerDir.path}, compare them (virtual method order, render parameters) and " +
                "update core/map/maplibre-abi.properties."
        }
        check(expected.isNotEmpty()) { "maplibre-abi.properties records no header hashes" }
        val copied = headerDir.listFiles { f -> f.name.endsWith(".hpp") }.orEmpty().map { it.name }.toSet()
        check(copied == expected.keys) { "Copied headers $copied differ from the recorded ${expected.keys}" }
        for ((name, hash) in expected) {
            val bytes = headerDir.resolve(name).readBytes()
            val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            check(actual == hash) {
                "$name no longer matches $tag (sha256 $actual, recorded $hash). The copy must stay " +
                    "verbatim: MapLibre calls the host through the upstream vtable, not through ours."
            }
        }
        stamp.get().asFile.writeText("$tag\n")
    }
}

tasks.named("preBuild") { dependsOn(verifyMapLibreAbi) }

// The pin test reads the catalog and the copied headers from the source tree.
tasks.withType<Test>().configureEach {
    systemProperty("triptrack.rootDir", rootDir.absolutePath)
}
