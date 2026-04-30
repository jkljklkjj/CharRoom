import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.3.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    id("org.jetbrains.compose") version "1.10.3"
}

group = "com.chatlite"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")

    // 2. 阿里云镜像作为后备，移除排除规则
    maven("https://maven.aliyun.com/repository/public")
}

kotlin {
    jvmToolchain(21)
}

// 公共依赖
dependencies {
    implementation("org.jetbrains.compose.ui:ui:1.10.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("io.ktor:ktor-client-core:3.4.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.4.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.4.3")
    implementation("org.jetbrains.compose.runtime:runtime:1.10.3")
    implementation("org.jetbrains.compose.foundation:foundation:1.10.3")
    implementation("org.jetbrains.compose.material:material:1.10.3")
    implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.glassfish.jaxb:jaxb-runtime:4.0.7")
    implementation("com.konghq:unirest-java:3.14.5")
    implementation("io.netty:netty-all:4.2.5.Final")
    implementation("org.jboss.marshalling:jboss-marshalling:2.3.0")
    implementation("org.jboss.marshalling:jboss-marshalling-river:2.3.0")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("ch.qos.logback:logback-classic:1.5.32")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.2")
    implementation("co.touchlab:kermit:2.1.0")
    implementation("com.google.protobuf:protobuf-java:4.34.1")
    implementation("com.google.protobuf:protobuf-kotlin:4.34.1")
    implementation(project(":proto"))

    // 桌面端特有依赖
    implementation("io.ktor:ktor-client-cio:3.4.3")
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
}

// 源集配置
sourceSets {
    main {
        kotlin.srcDirs("src/commonMain/kotlin", "src/desktopMain/kotlin", "shared/kotlin")
        resources.srcDirs("src/commonMain/resources", "src/desktopMain/resources")
    }
    test {
        kotlin.srcDirs("src/commonTest/kotlin", "src/desktopTest/kotlin", "src/androidUnitTest/kotlin")
        dependencies {
            implementation(kotlin("test"))
        }
    }
}

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

    from(kotlin.sourceSets["main"].kotlin.srcDirs)
    from("src/desktopMain/java") { into("") }
    from(kotlin.sourceSets["main"].resources.srcDirs)
    from("src/commonMain/resources") { into("") }
    from("src/desktopMain/resources") { into("") }
    from("build/classes/kotlin/main")
    from("build/classes/java/main") { into("") }
    from("build/extracted-include-protos") { into("extracted-include-protos") }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "MainKt"
    }

    dependsOn("assemble")
    dependsOn(":proto:protoJar")
}
