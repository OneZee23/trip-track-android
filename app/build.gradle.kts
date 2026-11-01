import java.util.Properties
import java.util.UUID

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

fun releaseValue(name: String): String? =
    providers.gradleProperty(name).orNull ?: providers.environmentVariable(name).orNull

fun buildConfigString(value: String): String = "\"" + value
    .replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""

val sentryDsn = releaseValue("TRIPTRACK_SENTRY_DSN")?.trim().orEmpty()
val signingValues = listOf("STORE_FILE", "STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
    .associateWith { releaseValue("TRIPTRACK_UPLOAD_$it") }
val hasUploadKey = signingValues.values.all { !it.isNullOrBlank() }
require(signingValues.values.all { it.isNullOrBlank() } || hasUploadKey) {
    "Set all four TRIPTRACK_UPLOAD_* signing values, or none for an unsigned release."
}
// Emergency device-test fallback: -PTRIPTRACK_MINIFY=false (resource shrinking follows it).
val minifyRelease = providers.gradleProperty("TRIPTRACK_MINIFY").map(String::toBooleanStrict).orElse(true)

// iOS: manual dSYM upload workflow. The UUID travels with the APK and its mapping, without an upload plugin.
abstract class SentryReleaseMetadata : DefaultTask() {
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction fun generate() {
        outputDirectory.get().asFile.apply { mkdirs() }
            .resolve("sentry-debug-meta.properties")
            .writeText("io.sentry.ProguardUuids=${UUID.randomUUID()}\n")
    }
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
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }
    signingConfigs {
        if (hasUploadKey) create("upload") {
            storeFile = file(requireNotNull(signingValues["STORE_FILE"]))
            storePassword = signingValues["STORE_PASSWORD"]
            keyAlias = signingValues["KEY_ALIAS"]
            keyPassword = signingValues["KEY_PASSWORD"]
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
            buildConfigField("String", "SENTRY_DSN", "\"\"")
        }
        release {
            isMinifyEnabled = minifyRelease.get()
            isShrinkResources = minifyRelease.get()
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasUploadKey) signingConfig = signingConfigs.getByName("upload")
            buildConfigField("String", "API_BASE_URL", "\"https://api.trip-track.app\"")
            buildConfigField("String", "SENTRY_DSN", buildConfigString(sentryDsn))
            ndk.debugSymbolLevel = "FULL"
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

androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
    val metadata = tasks.register<SentryReleaseMetadata>("generateSentryReleaseMetadata") {
        outputDirectory.set(layout.buildDirectory.dir("generated/sentry/release/assets"))
        // Each invocation gets its own mapping identity; archive APK/AAB/mapping/metadata together.
        outputs.upToDateWhen { false }
    }
    variant.sources.assets?.addGeneratedSourceDirectory(metadata, SentryReleaseMetadata::outputDirectory)
}

tasks.register<Exec>("verifyReleaseSentryWiring") {
    group = "verification"
    dependsOn("assembleRelease")
    environment("TRIPTRACK_EXPECTED_SENTRY_DSN", sentryDsn)
    commandLine("python3", rootProject.file("tools/release/check_sentry_wiring.py"),
        layout.buildDirectory.file("outputs/apk/release/app-release${if (hasUploadKey) "" else "-unsigned"}.apk").get().asFile,
        "--apkanalyzer", androidComponents.sdkComponents.sdkDirectory.get().asFile.resolve("cmdline-tools/latest/bin/apkanalyzer"))
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.sentry.android) {
        // Replay captures pixels; it has no place in the diary's diagnostics surface.
        exclude(group = "io.sentry", module = "sentry-android-replay")
    }
    implementation(libs.sentry.android.ndk)
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
    // iOS: MKMapSnapshotter. The existing MapLibre version renders static journal/garage maps.
    implementation(libs.maplibre.opengl)
    implementation(libs.serialization.json)
    implementation(libs.exifinterface)
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.appcompat)
    implementation(libs.core.splashscreen)
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

// Record and verify on macOS; Linux CI still renders the screens with verification off.
val roborazziRecord = providers.gradleProperty("roborazzi.test.record").map { it == "true" }.orElse(false)
val roborazziVerify = providers.gradleProperty("roborazzi.test.verify").map { it == "true" }.orElse(true)

tasks.withType<Test>().configureEach {
    val expectedSentryDsn = if (name.contains("Release")) sentryDsn else ""
    systemProperty("triptrack.expectedSentryDsn", expectedSentryDsn)
    inputs.property("expectedSentryDsn", expectedSentryDsn)
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
