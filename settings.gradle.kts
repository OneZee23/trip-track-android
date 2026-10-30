pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    val catalog = file("gradle/libs.versions.toml").readText()
    val foojayVersion = Regex("""foojay = "([^"]+)"""").find(catalog)!!.groupValues[1]
    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version foojayVersion
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TripTrack"
include(":app", ":core:model", ":core:logic", ":core:data", ":core:network", ":core:sync",
    ":core:location", ":core:designsystem", ":core:map")
