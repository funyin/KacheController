pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
    id("org.jetbrains.dokka") version "1.9.10" apply  false
    kotlin("jvm") version "1.9.0" apply false
    kotlin("multiplatform") version "1.8.21" apply false
}

rootProject.name = "KacheController"
include(":example")