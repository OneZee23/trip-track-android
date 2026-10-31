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
// the tests read the same system properties the plugin sets. Record and verify on macOS;
// Linux CI still renders the screens with verification off.
val roborazziRecord = providers.gradleProperty("roborazzi.test.record").map { it == "true" }.orElse(false)
val roborazziVerify = providers.gradleProperty("roborazzi.test.verify").map { it == "true" }.orElse(true)

tasks.withType<Test>().configureEach {
    providers.systemProperty("robolectric.dependency.dir").orNull?.let {
        systemProperty("robolectric.dependency.dir", it)
    }
    val record = roborazziRecord.get()
    val verify = !record && roborazziVerify.get()
    val renderOnly = !record && !verify
    // Both switches off makes captureRoboImage a no-op, including its drawing. CI captures into
    // build/ instead: draw-time crashes still fail the test, and committed baselines stay intact.
    systemProperty("roborazzi.test.record", (record || renderOnly).toString())
    systemProperty("roborazzi.test.verify", verify.toString())
    if (renderOnly) {
        val renderedScreenshots = layout.buildDirectory.dir("screenshots/$name")
        systemProperty("roborazzi.output.dir", renderedScreenshots.get().asFile.absolutePath)
        systemProperty("roborazzi.record.filePathStrategy", "relativePathFromRoborazziContextOutputDirectory")
        outputs.dir(renderedScreenshots).withPropertyName("renderedScreenshots")
    }
    inputs.property("roborazziRecord", record)
    inputs.property("roborazziVerify", verify)
    // A file tree, not a directory: there is nothing to verify before the first recording.
    inputs.files(fileTree("src/test/screenshots")).withPropertyName("screenshots").withPathSensitivity(PathSensitivity.RELATIVE)
    if (record) outputs.upToDateWhen { false }
}
