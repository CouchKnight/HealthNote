dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Only Android artifacts are looked up on Google's Maven, so a machine that cannot
        // reach it can still resolve everything the JVM modules need.
        google {
            content {
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.android(\\..*)?")
                includeGroupByRegex("com\\.google\\..*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "HealthNote"

// Pure Kotlin/JVM modules: build and test anywhere a JDK is available.
include(":core-model")
include(":core-render")
include(":core-render-pdf")

// Android modules need an Android SDK (and Google's Maven). They are included only when an
// SDK is configured, so the JVM modules stay buildable on machines and CI runners without one.
if (androidSdkConfigured(settingsDir)) {
    include(":core-render-android")
    include(":app")
} else {
    logger.lifecycle(
        "HealthNote: no Android SDK found (ANDROID_HOME or sdk.dir in local.properties); " +
            "building JVM modules only.",
    )
}

fun androidSdkConfigured(root: File): Boolean {
    if (!System.getenv("ANDROID_HOME").isNullOrBlank()) return true
    if (!System.getenv("ANDROID_SDK_ROOT").isNullOrBlank()) return true
    val local = File(root, "local.properties")
    return local.isFile && local.readLines().any { it.trimStart().startsWith("sdk.dir=") }
}
