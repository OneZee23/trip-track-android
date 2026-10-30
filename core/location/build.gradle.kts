plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.onezee.triptrack.location"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }
    defaultConfig {
        minSdk = 29
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
    api(project(":core:data"))
    api(project(":core:logic"))
    api(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)
    implementation(libs.play.services.location)

    testImplementation(testFixtures(project(":core:logic")))
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.coroutines.test)
}

tasks.withType<Test>().configureEach {
    providers.systemProperty("robolectric.dependency.dir").orNull?.let {
        systemProperty("robolectric.dependency.dir", it)
    }
}
