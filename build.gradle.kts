import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // No version on purpose:
    //  - standalone: the version comes from settings.gradle.kts pluginManagement.plugins
    //  - as a submodule: it comes from the parent build's buildscript classpath
    // Declaring a version here breaks the submodule case with
    // "plugin is already on the classpath with a different version".
    kotlin("jvm")
    `maven-publish`
}

group = "me.shovon"
version = "0.1.0"

// No repositories { } block here: a consuming build may set
// RepositoriesMode.FAIL_ON_PROJECT_REPOS, which would reject it.
// No `libs.` version catalog references either: the catalog belongs to the
// parent build and does not exist standalone.

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        // Resolve java.* against the JDK 11 API even when the daemon runs 17/21/25,
        // so newer-JDK-only methods cannot silently compile and then fail on Android.
        freeCompilerArgs.add("-Xjdk-release=11")
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "me.shovon"
            artifactId = "bd-sms-parsers"
            version = project.version.toString()
        }
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
        showCauses = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    maxParallelForks = maxOf(1, Runtime.getRuntime().availableProcessors() / 2)
}
