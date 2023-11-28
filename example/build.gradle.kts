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
    // This dependency is used by the application.
    implementation("com.google.guava:guava:31.1-jre")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("org.mongodb:mongodb-driver-kotlin-coroutine:4.11.1")
    implementation("org.mongodb:bson-kotlinx:4.11.1")
    implementation("io.lettuce:lettuce-core:6.2.2.RELEASE")
//    implementation(project(mapOf("path" to ":")))
//    implementation(project(mapOf("path" to ":")))
    implementation(project(mapOf("path" to ":mongo-redis")))
    testImplementation(kotlin("test"))
}


application {
    // Define the main class for the application.
    mainClass.set("MainKt")
}

tasks.test {
    useJUnit()
}

