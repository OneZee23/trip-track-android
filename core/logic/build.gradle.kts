plugins {
    alias(libs.plugins.kotlin.jvm)
    // TrackTestKit and SyntheticDrives: the recording module replays the same synthetic drives.
    `java-test-fixtures`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.serialization.json)
    testFixturesApi(project(":core:model"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
