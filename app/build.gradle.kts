import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val appVersion = Properties().apply {
    rootProject.layout.projectDirectory.file("version.properties").asFile.inputStream().use(::load)
}

android {
    namespace = "com.onezee.triptrack"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }
    defaultConfig {
        applicationId = "com.onezee.triptrack"
        minSdk = 29
        targetSdk = 36
        versionCode = appVersion.getProperty("versionCode").toInt()
        versionName = requireNotNull(appVersion.getProperty("versionName"))
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
        }
        release {
            isMinifyEnabled = false
        }
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

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.appcompat)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    // The map demo screen (src/debug) exists only in debug builds; no release screen uses the map yet.
    debugImplementation(project(":core:map"))
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.robolectric)
}

// Same Roborazzi switches as core:designsystem: verify by default, record with
// -Proborazzi.test.record=true.
val roborazziRecord = providers.gradleProperty("roborazzi.test.record").map { it == "true" }.orElse(false)

tasks.withType<Test>().configureEach {
    providers.systemProperty("robolectric.dependency.dir").orNull?.let {
        systemProperty("robolectric.dependency.dir", it)
    }
    val record = roborazziRecord.get()
    systemProperty("roborazzi.test.record", record.toString())
    systemProperty("roborazzi.test.verify", (!record).toString())
    inputs.property("roborazziRecord", record)
    inputs.dir("src/test/screenshots").optional().withPropertyName("screenshots")
    if (record) outputs.upToDateWhen { false }
}
