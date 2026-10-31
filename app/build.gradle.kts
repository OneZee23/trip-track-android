import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val appVersion = Properties().apply {
    rootProject.layout.projectDirectory.file("version.properties").asFile.inputStream().use(::load)
}

// Machine-local values that must never be committed (docs/sign-in.md): a Gradle property first,
// then the environment, then local.properties, which is gitignored.
val localProperties = Properties().apply {
    rootProject.layout.projectDirectory.file("local.properties").asFile.takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun localValue(name: String): String =
    (providers.gradleProperty(name).orNull ?: providers.environmentVariable(name).orNull ?: localProperties.getProperty(name))
        ?.trim().orEmpty()

// The Web client of the Google Auth Platform project, passed to Credential Manager as the
// serverClientId. Empty means Google sign-in is not configured on this machine.
val googleWebClientId = localValue("GOOGLE_WEB_CLIENT_ID").also {
    require(it.isEmpty() || Regex("[A-Za-z0-9._-]+\\.apps\\.googleusercontent\\.com").matches(it)) {
        "GOOGLE_WEB_CLIENT_ID must be a Web client ID ending in .apps.googleusercontent.com"
    }
}
// Debug builds may talk to a local or staging backend; release builds always use production.
val debugApiBaseUrl = localValue("TRIPTRACK_API_BASE_URL").ifEmpty { "https://api.trip-track.app" }.trimEnd('/')

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
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "API_BASE_URL", "\"https://api.trip-track.app\"")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions.unitTests.isIncludeAndroidResources = true
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    bundle {
        language {
            // The in-app language picker switches to any of the 13 languages at once; Play's
            // per-language splits would leave the ones the phone did not install missing.
            enableSplit = false
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:location"))
    implementation(project(":core:designsystem"))
    implementation(libs.room.runtime)
    implementation(libs.coroutines.core)
    implementation(project(":core:model"))
    implementation(project(":core:logic"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(project(":core:sync"))
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(project(":core:map"))
    implementation(libs.serialization.json)
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.appcompat)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.runtime)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.okhttp.mockwebserver)
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
    // A file tree, not a directory: there is nothing to verify before the first recording.
    inputs.files(fileTree("src/test/screenshots")).withPropertyName("screenshots").withPathSensitivity(PathSensitivity.RELATIVE)
    if (record) outputs.upToDateWhen { false }
}
