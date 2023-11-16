pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
    id("org.jetbrains.dokka") version "1.9.10" apply  false
}

rootProject.name = "KacheController"