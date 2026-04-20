import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform") version "2.3.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    id("org.jetbrains.compose") version "1.9.0"
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

// Apply Android build script early so kotlin { android() } is available when includeAndroid=true
if (project.findProperty("includeAndroid") == "true") {
    apply(from = "android.gradle.kts")
}

kotlin {
    jvmToolchain(21)

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    // Android configuration is handled by the separate Android project/script when enabled

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-core:2.3.4")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.4")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.4")
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.materialIconsExtended)
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
                implementation("org.glassfish.jaxb:jaxb-runtime:4.0.5")
                implementation("com.konghq:unirest-java:3.14.5")
                implementation("io.netty:netty-all:4.1.68.Final")
                implementation("org.jboss.marshalling:jboss-marshalling:2.0.10.Final")
                implementation("org.jboss.marshalling:jboss-marshalling-river:2.0.10.Final")
                implementation("org.slf4j:slf4j-api:2.0.17")
                implementation("ch.qos.logback:logback-classic:1.5.18")
                implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.20.0")
                implementation("co.touchlab:kermit:1.2.2")
                implementation("com.google.protobuf:protobuf-java:3.21.12")
                implementation("com.google.protobuf:protobuf-kotlin:3.21.12")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
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