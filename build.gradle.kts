// Plugins are put on the root classpath here rather than declared with `apply false` in a
// plugins {} block, because the Android Gradle Plugin cannot even be resolved without Google's
// Maven. Loading every plugin from one classloader also keeps KGP and AGP able to see each other.
buildscript {
    repositories {
        google {
            content {
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.android(\\..*)?")
                includeGroupByRegex("com\\.google\\..*")
            }
        }
        mavenCentral()
    }
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
        if (gradle.rootProject.findProject(":app") != null) {
            classpath(libs.android.gradle.plugin)
            classpath(libs.compose.compiler.gradle.plugin)
        }
    }
}
