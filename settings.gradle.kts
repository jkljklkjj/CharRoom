pluginManagement {
    repositories {
        // Prefer the Gradle Plugin Portal and Google first so plugin artifacts
        // (especially the Android Gradle Plugin) are resolved reliably.
        gradlePluginPortal()
        google()
        mavenCentral()
        // Keep compose dev mirror as a fallback for compose artifacts. Restrict
        // it to only serve org.jetbrains.compose artifacts so core Gradle/AGP
        // artifacts are resolved from Google/MavenCentral instead.
        maven("https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/public/p/compose/dev") {
            content {
                includeGroupByRegex("""org\.jetbrains\.compose(\..*)?""")
            }
        }
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") {
            content {
                includeGroupByRegex("""org\.jetbrains\.compose(\..*)?""")
            }
        }
        maven("https://maven.aliyun.com/repository/public") {
            content {
                // Keep mirror as fallback, but don't source compose plugin artifacts from it.
                excludeGroupByRegex("org\\.jetbrains\\.compose(\\..*)?")
                excludeGroupByRegex("org\\.jetbrains\\.kotlin(\\..*)?")
            }
        }
    }

    plugins {
        kotlin("jvm").version(extra["kotlin.version"] as String)
        id("org.jetbrains.compose").version(extra["compose.version"] as String)
        id("org.jetbrains.kotlin.plugin.compose").version(extra["kotlin.version"] as String)
        // Use AGP 8.2.2 which is compatible with the Kotlin Gradle plugin in
        // this project (Kotlin 2.3.x requires AGP >= 8.2.2).
        id("com.android.application").version("8.2.2")
        id("org.jetbrains.kotlin.android").version(extra["kotlin.version"] as String)
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Ensure AGP jars are available on the root buildscript classpath so plugins
// (notably the Kotlin Gradle plugin) that run early in configuration can see
// AGP internals like com.android.build.gradle.BaseExtension. This mitigates
// classloader visibility issues when the Kotlin plugin is already loaded by
// the root plugin classloader.
buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        // Match the AGP version used in pluginManagement so the buildscript
        // classpath can be resolved from Google / Maven Central.
        classpath("com.android.tools.build:gradle:8.13.2")
    }
}

// Ensure project dependency resolution prefers the settings repositories
// (avoid fetching core plugins/artifacts from the Compose dev mirror when
// Google / Maven Central can provide them).
dependencyResolutionManagement {
    // Use the repositories below; avoid setting repositoriesMode here because
    // the symbol may not be available in the Kotlin script classpath on some
    // Gradle/Kotlin DSL combinations.
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // Keep compose dev after the main repos so it won't be used for AGP.
        // Restrict it to org.jetbrains.compose to avoid serving AGP artifacts.
        maven("https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/public/p/compose/dev") {
            content {
                includeGroupByRegex("""org\.jetbrains\.compose(\..*)?""")
            }
        }
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") {
            content {
                includeGroupByRegex("""org\.jetbrains\.compose(\..*)?"""")
            }
        }
        maven("https://maven.aliyun.com/repository/public") {
            content {
                excludeGroupByRegex("org\\.jetbrains\\.compose(\\..*)?")
                excludeGroupByRegex("org\\.jetbrains\\.kotlin(\\..*)?")
            }
        }
    }
}

rootProject.name = "CharRoom"
include("main")
include("proto")
include("androidApp")
