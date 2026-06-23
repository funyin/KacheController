plugins {
    kotlin("jvm") version "1.9.0"
    kotlin("plugin.serialization") version "1.9.0"
    application
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(19))
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation(project(":kachecontroller-mongo"))
    implementation(project(":kachecontroller-cache-redis"))
    implementation(project(":kachecontroller-cache-memory"))
    implementation(project(":kachecontroller-cache-sqlite"))
}

application {
    mainClass.set("MainKt")
}


application {
    // Define the main class for the application.
    mainClass.set("MainKt")
}

tasks.test {
    useJUnit()
}

