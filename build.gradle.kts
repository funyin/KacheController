plugins {
    kotlin("jvm") version "1.9.0"
    application
    kotlin("plugin.serialization") version "1.9.0"
    id("org.jetbrains.dokka")
}

group = "com.funyin"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("org.mongodb:mongodb-driver-kotlin-coroutine:4.11.1")
    implementation("org.mongodb:bson-kotlinx:4.11.1")
    implementation("io.lettuce:lettuce-core:6.2.2.RELEASE")
    implementation("ch.qos.logback:logback-classic:1.4.4")
}

tasks.test {
    useJUnit()
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("MainKt")
}