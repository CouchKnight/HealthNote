import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// The Android PDF backend. It has no hand-written copy of the emitter: the source of
// :core-render-pdf is copied in at build time with the Apache PDFBox package prefix rewritten
// to pdfbox-android's, then compiled against pdfbox-android 2.0.27.0 (a port of PDFBox 2.0.27,
// the version :core-render-pdf is built and tested against). Class and method names are the
// same in both, so the JVM tests cover the logic the device runs.
val generatePdfBoxAndroidSources by tasks.registering(Sync::class) {
    from(rootProject.file("core-render-pdf/src/main/kotlin"))
    into(layout.buildDirectory.dir("generated/pdfbox-android/kotlin"))
    filter { line ->
        line.replace("org.apache.pdfbox.", "com.tom_roush.pdfbox.")
            .replace("org.apache.fontbox.", "com.tom_roush.fontbox.")
    }
}

android {
    namespace = "io.github.couchknight.healthnote.render.android"
    compileSdk = 35
    defaultConfig {
        minSdk = 28
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets.getByName("main").java.srcDir(layout.buildDirectory.dir("generated/pdfbox-android/kotlin"))
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

tasks.named("preBuild") { dependsOn(generatePdfBoxAndroidSources) }

dependencies {
    api(project(":core-render"))
    implementation(libs.pdfbox.android)
}
