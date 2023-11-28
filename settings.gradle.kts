pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.jetbrains.dokka") version "1.9.10" apply  false
    kotlin("jvm") version "1.9.0" apply false
}

rootProject.name = "KacheController"
include(":example")
include(":core")
include(":mongo-redis")
