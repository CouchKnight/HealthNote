import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

// Java 17 bytecode, compiled by whatever JDK (17+) runs Gradle. Also consumed by the Android
// app (minSdk 28), so stick to java.time APIs that exist there: no Java 9+ additions such as
// Duration.toMinutesPart(), which Android only has from API 31.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
