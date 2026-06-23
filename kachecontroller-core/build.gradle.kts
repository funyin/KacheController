import java.util.Properties

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.dokka")
    `maven-publish`
    signing
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
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("ch.qos.logback:logback-classic:1.4.4")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
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
            "kotlinMultiplatform" -> "kachecontroller-core"
            else -> "kachecontroller-core-$pubName"
        }
        val javadocJar = tasks.register<Jar>("${pubName}JavadocJar") {
            dependsOn(tasks.dokkaHtmlPartial)
            archiveClassifier.set("javadoc")
            archiveAppendix.set(pubName)
            from("../docs/kachecontroller-core")
        }
        artifact(javadocJar)
        pom {
            name.set("KacheController Core")
            description.set("Core caching abstractions for KacheController")
            url.set("https://funyin.github.io/KacheController/kachecontroller-core/index.html")
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
        signing {
            useInMemoryPgpKeys(
                signingKeyFile.readText(),
                localProperties.getProperty("signing.password") ?: "",
            )
            sign(publishing.publications)
        }
    }
}
