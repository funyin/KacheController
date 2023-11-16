plugins {
    kotlin("multiplatform") version "1.8.21"
    kotlin("plugin.serialization") version "1.9.0"
    id("org.jetbrains.dokka") version "1.9.10"
}

group = "com.funyinkash"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {

    jvm {
        jvmToolchain(17)
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
    }
    sourceSets {
        val jvmMain by getting{
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("org.mongodb:mongodb-driver-kotlin-coroutine:4.11.1")
                implementation("org.mongodb:bson-kotlinx:4.11.1")
                implementation("io.lettuce:lettuce-core:6.2.2.RELEASE")
                implementation("ch.qos.logback:logback-classic:1.4.4")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

