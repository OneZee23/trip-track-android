plugins {
    id("com.android.application")
}

android {
    namespace = "com.onezee.triptrack.fogspike"
    compileSdk = 36
    // Pinned to the NDK and CMake already in the SDK: the AGP defaults would download a new NDK.
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.onezee.triptrack.fogspike"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-spike"
        ndk {
            // Every phone that runs Android 10+ with Play services ships a 64-bit ARM ABI, and so
            // does the emulator on Apple silicon. Keeps the owner's APK small.
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++20", "-fno-exceptions")
                // MapLibre links libc++ statically; the layer's host object crosses the library
                // boundary only through its vtable, so a second static copy is safe.
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // The OpenGL ES build: since 12.x the default `android-sdk` artifact renders with Vulkan, and
    // a custom layer on Vulkan needs a C++ Vulkan host.
    implementation("org.maplibre.gl:android-sdk-opengl:13.7.1")
    // Candidate B buffers and unions corridors into fog polygons with holes.
    implementation("org.locationtech.jts:jts-core:1.20.0")
}
