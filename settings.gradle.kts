pluginManagement {
    repositories {
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        gradlePluginPortal()
        mavenCentral()
        google()
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
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "CharRoom"
include("main")
include("proto")
