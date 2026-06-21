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
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("org.mongodb:mongodb-driver-kotlin-coroutine:4.11.1")
                implementation("org.mongodb:bson-kotlinx:4.11.1")
                implementation("io.lettuce:lettuce-core:6.3.0.RELEASE")
                implementation("ch.qos.logback:logback-classic:1.4.4")
            }
        }
    }
}

group = "com.funyinkash.kachecontroller"
version = "1.0.3"

publishing {
    publications.withType<MavenPublication>().configureEach {
        val pubName = name
        val javadocJar = tasks.register<Jar>("${pubName}JavadocJar") {
            dependsOn(tasks.dokkaHtmlPartial)
            archiveClassifier.set("javadoc")
            archiveAppendix.set(pubName)
            from("../docs/mongo-redis")
        }
        artifact(javadocJar)

        pom {
            name.set("KacheController")
            description.set(
                "A simple controller to add a caching layer on top of a database operations.\n" +
                "So you can perform database actions with one function without the boiler plate of the caching layer.\n" +
                "This is the mongo-redis use cases"
            )
            url.set("https://funyin.github.io/KacheController/mongo-redis/index.html")
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
