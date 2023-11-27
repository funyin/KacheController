plugins {
    kotlin("multiplatform") version "1.8.21"
    kotlin("plugin.serialization") version "1.9.0"
    id("org.jetbrains.dokka") version "1.9.10"
    `maven-publish`
    `signing`
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
        val jvmMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("org.mongodb:mongodb-driver-kotlin-coroutine:4.11.1")
                implementation("org.mongodb:bson-kotlinx:4.11.1")
                implementation("io.lettuce:lettuce-core:6.3.0.RELEASE")
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.funyinkash"
            artifactId = "KacheController"
            version = "1.1"

            from(components["java"])

            pom {
                name.set("KacheController")
                description.set("A Kotlin Library that allows performing caching and database write operations in a single operation")
                url.set("https://github.com/funyin/KacheController")
                issueManagement {
                    system.set("Github")
                    url.set("https://github.com/funyin/KacheController/issues")
                }
                properties.set(
                    mapOf(
                        "myProp" to "value",
                        "prop.with.dots" to "anotherValue"
                    )
                )
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("funyin")
                        name.set("Funyinoluwa Kashimawo")
                        email.set("funyin.kash@gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/funyin/KacheController.git")
                    developerConnection.set("scm:git:ssh://github.com/funyin/KacheController.git")
                    url.set("https://github.com/funyin/KacheController")
                }
            }
        }
    }
}

