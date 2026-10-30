plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.onezee.triptrack.designsystem"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }
    defaultConfig {
        minSdk = 29
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(project(":core:logic"))
    api(project(":core:model"))
    api(platform(libs.compose.bom))
    api(libs.compose.foundation)
    api(libs.compose.ui)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.robolectric)
}

kotlin {
    jvmToolchain(17)
}

// Roborazzi runs without its Gradle plugin, whose HTML report pulls tens of megabytes of webjars:
// the tests read the same system properties the plugin sets. Every run verifies against the
// committed images in src/test/screenshots; -Proborazzi.test.record=true records them again.
val roborazziRecord = providers.gradleProperty("roborazzi.test.record").map { it == "true" }.orElse(false)

tasks.withType<Test>().configureEach {
    providers.systemProperty("robolectric.dependency.dir").orNull?.let {
        systemProperty("robolectric.dependency.dir", it)
    }
    val record = roborazziRecord.get()
    systemProperty("roborazzi.test.record", record.toString())
    systemProperty("roborazzi.test.verify", (!record).toString())
    inputs.property("roborazziRecord", record)
    // A file tree, not a directory: there is nothing to verify before the first recording.
    inputs.files(fileTree("src/test/screenshots")).withPropertyName("screenshots").withPathSensitivity(PathSensitivity.RELATIVE)
    if (record) outputs.upToDateWhen { false }
}
