import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    kotlin("multiplatform") version "2.3.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    id("org.jetbrains.compose") version "1.10.3"
}

group = "com.chatlite"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
    maven("https://maven.aliyun.com/repository/public") {
        content {
            excludeGroupByRegex("org\\.jetbrains\\.compose(\\..*)?")
            excludeGroup("org.jetbrains.skiko")
        }
    }
}

// Optional Android configuration: enable when includeAndroid=true
val includeAndroid = project.findProperty("includeAndroid") == "true"

// Android app is provided as a separate module (:androidApp). Do not apply Android plugin in the root project.

kotlin {
    jvmToolchain(21)

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    // Android target is intentionally not registered inside kotlin{} to avoid extension name conflicts.
    // The Android application plugin will be configured via `android.gradle.kts` (applied above) and
    // its source sets are hooked into the existing `src/androidMain` layout so Android code builds.

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-core:3.4.2")
                implementation("io.ktor:ktor-client-content-negotiation:3.4.2")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.4.2")
                implementation("org.jetbrains.compose.runtime:runtime:1.10.3")
                implementation("org.jetbrains.compose.foundation:foundation:1.10.3")
                implementation("org.jetbrains.compose.material:material:1.10.3")
                implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
                implementation("org.glassfish.jaxb:jaxb-runtime:4.0.7")
                implementation("com.konghq:unirest-java:3.14.5")
                implementation("io.netty:netty-all:4.1.68.Final")
                implementation("org.jboss.marshalling:jboss-marshalling:2.3.0")
                implementation("org.jboss.marshalling:jboss-marshalling-river:2.3.0")
                implementation("org.slf4j:slf4j-api:2.0.17")
                implementation("ch.qos.logback:logback-classic:1.5.32")
                implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.2")
                implementation("co.touchlab:kermit:2.1.0")
                implementation("com.google.protobuf:protobuf-java:4.34.1")
                implementation("com.google.protobuf:protobuf-kotlin:4.34.1")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        if (includeAndroid) {
            val androidMain by getting {
                dependencies {
                    implementation("androidx.activity:activity-compose:1.8.0")
                    implementation("androidx.core:core-ktx:1.10.1")
                }
            }
            val androidTest by getting
        }
        // androidMain will be configured when the Android build is enabled (kept out of common MPP setup)

        val desktopMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-cio:2.3.4")
                implementation(compose.desktop.currentOs)
                implementation(project(":proto"))
            }
        }
        val desktopTest by getting {
            kotlin.srcDir("src/androidUnitTest/kotlin")
        }
    }
}

// Android Gradle script is applied above when includeAndroid=true

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"
        
        // ========== ProGuard 配置（只压缩优化，不混淆） ==========
        buildTypes.release {
            proguard {
                isEnabled = true
                obfuscate = false
                optimize = false
                configurationFiles.from(project.file("proguard-rules.pro"))
            }
        }
        
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "CharRoom"
            packageVersion = "1.0.0"
            vendor = "QingLiao"
            description = "CharRoom - lightweight secure cross-platform chat"
            copyright = "Copyright 2026 QingLiao"

            val iconsDir = project.file("packaging/icons")
            windows {
                menuGroup = "CharRoom"
                val ico = file("${iconsDir.path}/app.ico")
                if (ico.exists()) iconFile.set(ico)
            }
            macOS {
                bundleID = "com.chatlite.charroom"
                val icns = file("${iconsDir.path}/app.icns")
                if (icns.exists()) iconFile.set(icns)
            }
            linux {
                val png = file("${iconsDir.path}/app.png")
                if (png.exists()) iconFile.set(png)
            }
        }
    }
}

tasks.register("buildInstallers") {
    group = "distribution"
    description = "Build native installers (DMG / MSI / DEB)"
    dependsOn(tasks.matching { it.name.startsWith("package") || it.name.startsWith("jpackage") })
}

tasks.register<Jar>("customJar") {
    archiveBaseName.set("在线聊天App")
    archiveVersion.set("1.0.0")
    
    from(kotlin.sourceSets["commonMain"].kotlin.srcDirs)
    from(kotlin.sourceSets["desktopMain"].kotlin.srcDirs)
    from("src/desktopMain/java") { into("") }
    from(kotlin.sourceSets["commonMain"].resources.srcDirs)
    from(kotlin.sourceSets["desktopMain"].resources.srcDirs)
    from("src/commonMain/resources") { into("") }
    from("src/desktopMain/resources") { into("") }
    from("build/classes/kotlin/desktop/main")
    from("build/classes/java/desktop/main") { into("") }
    from("build/classes/java/desktopMain") { into("") }
    from("build/extracted-include-protos") { into("extracted-include-protos") }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "MainKt"
    }

    dependsOn("assemble")
    dependsOn(":proto:protoJar")
}