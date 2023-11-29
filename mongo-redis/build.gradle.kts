
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
//version = "1.0-SNAPSHOT"
version = "1.0.0"


//tasks.dokkaHtml {
//    outputDirectory.set(buildDir.resolve("../docs"))
//}
//tasks.dokkaGfm{
//    outputDirectory.set(buildDir.resolve("../docs"))
//}

publishing {

    val javaDocJar = tasks.register<Jar>("javadocJar") {
        dependsOn(tasks.dokkaHtmlPartial)
        archiveClassifier.set("javadoc")
        from("../docs/mongo-redis")
    }

//    val sourcesJar = tasks.register<Jar>("sourcesJar") {
//        dependsOn(tasks.classes)
//        archiveClassifier.set("sources")
//        from(sourceSets.main)
//    }


    publications {
        create<MavenPublication>("maven") {
//            groupId = "com.funyinkash.kachecontroller"
            artifactId = "mongo-redis"
//            version = "1.0-SNAPSHOT"

            from(components["kotlin"])
            artifact(javaDocJar)
            artifact(tasks.kotlinSourcesJar)

            pom {
                name.set("KacheController")
                description.set("A simple controller to add a caching layer on top of a database operations.\n" +
                        "So you can perform database actions with one function without the boiler plate of the caching layer.\n" +
                        "This is the mongo-redis use cases")
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