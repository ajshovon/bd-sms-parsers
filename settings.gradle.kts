// Standalone build only. When this repository is consumed as a git submodule of
// another Gradle build, that build's root settings.gradle.kts is the one Gradle
// evaluates and this file is ignored entirely.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        // Supplies the version for the deliberately version-less `kotlin("jvm")`
        // request in build.gradle.kts. See the comment there for why.
        id("org.jetbrains.kotlin.jvm") version "2.3.0"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "bd-sms-parsers"
