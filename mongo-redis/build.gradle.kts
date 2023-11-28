plugins {
    `java-library`
    kotlin("jvm") version "1.9.0"


    kotlin("plugin.serialization") version "1.9.0"
    id("org.jetbrains.dokka") version "1.9.10"
    `maven-publish`
    signing
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

java {
    sourceCompatibility = JavaVersion.VERSION_19
    targetCompatibility = JavaVersion.VERSION_19
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("org.mongodb:mongodb-driver-kotlin-coroutine:4.11.1")
    implementation("org.mongodb:bson-kotlinx:4.11.1")
    implementation("io.lettuce:lettuce-core:6.3.0.RELEASE")
    implementation("ch.qos.logback:logback-classic:1.4.4")

//    testImplementation(kotlin("test"))
}

group = "com.funyinkash.kachecontroller"
version = "1.0-SNAPSHOT"


tasks.dokkaHtml {
    outputDirectory.set(buildDir.resolve("../docs"))
}

publishing {

    val javaDocJar = tasks.register<Jar>("javadocJar") {
        dependsOn(tasks.dokkaHtml)
        archiveClassifier.set("javadoc")
        from("docs")
    }


    publications {
        create<MavenPublication>("maven") {
//            groupId = "com.funyinkash.kachecontroller"
            artifactId = "mongo-redis"
//            version = "1.0-SNAPSHOT"

            from(components["kotlin"])
            artifact(javaDocJar)

            pom {
                name.set("mongo-redis")
                description.set("A Kotlin Library that allows performing caching and database write operations in a single operation")
                url.set("https://github.com/funyin/KacheController")
                issueManagement {
                    system.set("Github")
                    url.set("https://github.com/funyin/KacheController/issues")
                }
                packaging = "jar"
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

    repositories {
        maven {
            name = "OSSHR"
//            url = layout.buildDirectory.dir("staging-deploy").get().asFile.toURI()

//            publications.withType<MavenPublication>()["maven"].version

            val isSnapshot = version.toString().endsWith("SNAPSHOT")
            url = uri(
                if (isSnapshot)
                    "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                else
                    "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
            )
            credentials {
                username = project.properties["osshr.username"].toString()
                password = project.properties["osshr.password"].toString()
            }
        }
    }
}


signing {
    val file = File("${projectDir.parent}/${project.properties["signing.secretKeyFile"]}")
    useInMemoryPgpKeys(
        file.readText(),
        project.properties["signing.password"].toString(),
    )
    sign(publishing.publications["maven"])
}