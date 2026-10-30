plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.onezee.triptrack.sync"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }
    defaultConfig {
        minSdk = 29
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
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
    api(project(":core:data"))
    api(project(":core:network"))
    implementation(project(":core:logic"))
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    api(libs.work.runtime)
    implementation(libs.exifinterface)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
