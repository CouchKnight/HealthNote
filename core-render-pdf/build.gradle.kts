import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

// The PDF backend, compiled against Apache PDFBox 2.0.27 so it can be built and tested on the
// JVM. On Android the same source is recompiled against pdfbox-android 2.0.27.0 (a port of
// this exact release) by `:core-render-android`, which rewrites only the import prefix.
// Keep src/main free of JVM-only APIs (java.awt, javax.*) for that reason; tests may use them.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api(project(":core-render"))
    implementation(libs.pdfbox)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // Rendered previews land here for eyeballing against the design.
    systemProperty("healthnote.previewDir", layout.buildDirectory.dir("previews").get().asFile.path)
}

tasks.register<JavaExec>("renderSample") {
    group = "healthnote"
    description = "Renders the design's September 2026 sample to build/sample/HealthNote-2026-09.pdf"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.couchknight.healthnote.render.pdf.RenderSampleKt")
    args(layout.buildDirectory.dir("sample").get().asFile.path)
}
