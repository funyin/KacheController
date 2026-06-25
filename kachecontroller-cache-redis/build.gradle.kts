import java.util.Properties
import org.gradle.plugins.signing.SigningExtension

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.dokka")
    `maven-publish`
    id("com.gradleup.nmcp") version "0.0.9"
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

kotlin {
    jvm()
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":kachecontroller-core"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(project(":kachecontroller-core"))
                api("io.lettuce:lettuce-core:6.5.0.RELEASE")
                implementation("ch.qos.logback:logback-classic:1.4.4")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
                implementation("io.mockk:mockk:1.13.8")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
            }
        }
    }

    tasks.named<Test>("jvmTest") {
        useJUnitPlatform()
    }
}

group = "com.funyinkash"
version = "1.0.6"

publishing {
    publications.withType<MavenPublication>().configureEach {
        val pubName = name
        artifactId = when (pubName) {
            "kotlinMultiplatform" -> "kachecontroller-cache-redis"
            else -> "kachecontroller-cache-redis-$pubName"
        }
        val javadocJar = tasks.register<Jar>("${pubName}JavadocJar") {
            dependsOn(tasks.dokkaHtmlPartial)
            archiveClassifier.set("javadoc")
            archiveAppendix.set(pubName)
            from("../docs/kachecontroller-cache-redis")
        }
        artifact(javadocJar)
        pom {
            name.set("KacheController Redis Cache")
            description.set("Redis cache adapter for KacheController")
            url.set("https://funyin.github.io/KacheController/kachecontroller-cache-redis/index.html")
            issueManagement {
                system.set("Github")
                url.set("https://github.com/funyin/KacheController/issues")
            }
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

nmcp {
    publishAllPublications {
        username.set(localProperties.getProperty("osshr.username"))
        password.set(localProperties.getProperty("osshr.password"))
        publicationType.set("AUTOMATIC")
    }
}

val signingKeyFilePath = localProperties.getProperty("signing.secretKeyFile")
if (!signingKeyFilePath.isNullOrBlank()) {
    val signingKeyFile = rootProject.file(signingKeyFilePath)
    if (signingKeyFile.exists()) {
        apply(plugin = "signing")
        configure<SigningExtension> {
            useInMemoryPgpKeys(
                signingKeyFile.readText(),
                localProperties.getProperty("signing.password") ?: "",
            )
            sign(publishing.publications)
        }
    }
}
