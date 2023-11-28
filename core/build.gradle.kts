plugins {
    kotlin("jvm") version "1.9.0"
    `java-library`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

java {
    sourceCompatibility = JavaVersion.VERSION_19
    targetCompatibility = JavaVersion.VERSION_19
}

tasks.test {
    useJUnit()
}

dependencies {
//    testImplementation(kotlin("test"))
//    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
//    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.3")
//    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}