import org.jetbrains.dokka.gradle.DokkaTask

plugins {
    kotlin("jvm") version "1.9.0"
    id("org.jetbrains.dokka") version "1.9.10"
}

repositories {
    mavenCentral()
}

subprojects {
    tasks.withType<DokkaTask>().configureEach {
        dokkaSourceSets {
            named("main") {
                sourceRoots.from("src/jvmMain/kotlin")
            }
        }
    }
}

tasks.dokkaHtmlMultiModule {
    outputDirectory.set(buildDir.resolve("../docs"))
}