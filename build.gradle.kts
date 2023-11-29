plugins {
    kotlin("jvm") version "1.9.0"
    id("org.jetbrains.dokka") version "1.9.10"
}

repositories {
    mavenCentral()
}

tasks.dokkaHtmlMultiModule {
    outputDirectory.set(buildDir.resolve("../docs"))
}