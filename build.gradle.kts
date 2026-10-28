import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.Copy

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.13.2")
    }
}

plugins {
    kotlin("jvm") version "2.3.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    id("org.jetbrains.compose") version "1.10.3"
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
}

group = "com.chatlite"
val releaseVersion = providers.gradleProperty("appVersion").get()
val releaseVersionCode = providers.gradleProperty("appVersionCode").get().toInt()
val desktopOs = System.getProperty("os.name").lowercase()
val desktopArch = System.getProperty("os.arch").lowercase()
val desktopNativeClassifier = when {
    desktopOs.contains("win") && desktopArch in listOf("amd64", "x86_64") -> "windows-x86_64"
    desktopOs.contains("mac") && desktopArch in listOf("aarch64", "arm64") -> "osx-aarch_64"
    desktopOs.contains("mac") && desktopArch in listOf("amd64", "x86_64") -> "osx-x86_64"
    desktopOs.contains("linux") && desktopArch in listOf("amd64", "x86_64") -> "linux-x86_64"
    else -> error("Unsupported desktop QUIC platform: $desktopOs/$desktopArch")
}
// Rich-text dependencies can transitively bring a foreign Skiko runtime.
val desktopSkikoPlatform = desktopNativeClassifier.replace("windows-x86_64", "windows-x64")
    .replace("linux-x86_64", "linux-x64").replace("osx-x86_64", "macos-x64").replace("osx-aarch_64", "macos-arm64")
configurations.configureEach {
    listOf("windows-x64", "windows-arm64", "linux-x64", "linux-arm64", "macos-x64", "macos-arm64")
        .filter { it != desktopSkikoPlatform }
        .forEach { exclude(group = "org.jetbrains.skiko", module = "skiko-awt-runtime-$it") }
}
require(releaseVersion.matches(Regex("[1-9][0-9]*\\.[0-9]+\\.[0-9]+")) && releaseVersionCode > 0)
version = releaseVersion
val releaseSources = layout.buildDirectory.dir("generated/releaseVersion")
val generateReleaseVersion = tasks.register("generateReleaseVersion") {
    inputs.property("name", releaseVersion)
    inputs.property("code", releaseVersionCode)
    outputs.dir(releaseSources)
    doLast {
        releaseSources.get().file("core/BuildReleaseVersion.kt").asFile.apply {
            parentFile.mkdirs()
            writeText("package core\nobject BuildReleaseVersion { const val NAME = \"$releaseVersion\"; const val CODE = $releaseVersionCode }\n")
        }
    }
}

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
    implementation("io.netty:netty-common:4.2.5.Final")
    implementation("io.netty:netty-buffer:4.2.5.Final")
    implementation("io.netty:netty-transport:4.2.5.Final")
    implementation("io.netty:netty-resolver:4.2.5.Final")
    implementation("io.netty:netty-codec:4.2.5.Final")
    implementation("io.netty:netty-codec-http:4.2.5.Final")
    implementation("io.netty:netty-handler:4.2.5.Final")
    // Netty QUIC (与后端 QUIC 协议兼容)
    implementation("io.netty.incubator:netty-incubator-codec-classes-quic:0.0.75.Final")
    runtimeOnly("io.netty.incubator:netty-incubator-codec-native-quic:0.0.75.Final:$desktopNativeClassifier")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("org.slf4j:slf4j-simple:2.0.17")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.2")
    implementation("co.touchlab:kermit:2.1.0")
    implementation("io.github.oshai:kotlin-logging:6.0.3") // Kotlin官方日志库
    implementation("com.google.protobuf:protobuf-java:4.34.1")
    implementation("com.google.protobuf:protobuf-kotlin:4.34.1")
    implementation(project(":proto"))
    // QR 码生成
    implementation("io.github.alexzhirkevich:qrose:1.1.2")

    // Markdown渲染支持
    implementation("com.halilibo.compose-richtext:richtext-ui-material:1.0.0-alpha04")
    implementation("com.halilibo.compose-richtext:richtext-commonmark:1.0.0-alpha04")

    // Koin 依赖注入
    implementation("io.insert-koin:koin-core:4.2.0-RC1")
    implementation("io.insert-koin:koin-compose:4.2.0-RC1")

    // 桌面端特有依赖
    implementation("io.ktor:ktor-client-cio:3.4.3")
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")

    testImplementation(kotlin("test"))

}

// 源集配置
sourceSets {
    main {
        kotlin.srcDirs("src/commonMain/kotlin", "src/desktopMain/kotlin")
        kotlin.srcDir(generateReleaseVersion)
        resources.srcDirs("src/commonMain/resources", "src/desktopMain/resources")
        // 排除 Android 特定文件和 WebRTC call（包名不匹配）
        kotlin.exclude("**/*.android.kt", "**/call/**")
    }
    test {
        kotlin.srcDirs("src/commonTest/kotlin", "src/desktopTest/kotlin", "src/androidUnitTest/kotlin")
        kotlin.exclude("cli/**")
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

        jvmArgs += listOf(
            "-Dfile.encoding=UTF-8",
            "-Dsun.jnu.encoding=UTF-8",
            "-Dstdout.encoding=UTF-8",
            "-Dstderr.encoding=UTF-8",
            "-Duser.language=zh",
            "-Duser.country=CN",
            "-Dconsole.encoding=UTF-8"
        )

        // Compose invokes jlink itself; packaging requires a full JDK with jmods/jpackage.
        javaHome = System.getProperty("java.home")

        buildTypes {
            release {
                proguard {
                    version.set("7.8.2")
                    isEnabled = true
                    obfuscate = true // 启用混淆
                    optimize = true  // 启用最高级别代码优化
                    configurationFiles.from(project.file("proguard-rules.pro"))
                }
            }
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // Swing/AWT is required by Compose Desktop. Keep EC crypto for HTTPS/QUIC trust.
            modules(
                "java.desktop",
                "java.logging",
                "java.prefs",
                "jdk.crypto.ec",
                "jdk.unsupported"
            )
            packageName = "chatlite"
            packageVersion = releaseVersion
            vendor = "QingLiao"
            description = "CharRoom - lightweight secure cross-platform chat"
            copyright = "Copyright 2026 QingLiao"

            val iconsDir = project.file("packaging/icons")
            windows {
                menuGroup = "chatlite"
                val ico = file("${iconsDir.path}/app.ico")
                if (ico.exists()) iconFile.set(ico)
            }
            macOS {
                bundleID = "chatlite.charroom"
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

    dependsOn(when {
        desktopOs.contains("win") -> "packageReleaseMsi"
        desktopOs.contains("mac") -> "packageReleaseDmg"
        else -> "packageReleaseDeb"
    })
}

tasks.register<Sync>("stageDesktopReleaseArtifacts") {
    group = "distribution"
    description = "Stage desktop release installers with fixed filenames"
    dependsOn("buildInstallers")
    into(layout.buildDirectory.dir("release-artifacts/desktop"))

    val format = when {
        desktopOs.contains("win") -> "msi"
        desktopOs.contains("mac") -> "dmg"
        else -> "deb"
    }
    from(layout.buildDirectory.dir("compose/binaries/main-release/$format")) {
        include("*.$format")
        rename { "chatlite.$format" }
    }
    doFirst {
        val installers = layout.buildDirectory.dir("compose/binaries/main-release/$format").get().asFile
            .listFiles { f -> f.extension == format }.orEmpty()
        check(installers.size == 1) { "Expected one release installer, found ${installers.size}; clean stale installers first" }
    }
}

tasks.register<Copy>("stageAndroidReleaseArtifact") {
    group = "distribution"
    description = "Stage Android release APK with a fixed filename"
    dependsOn(":androidApp:assembleRelease")
    into(layout.buildDirectory.dir("release-artifacts/android"))

    from(project.layout.projectDirectory.dir("androidApp/build/outputs/apk/release")) {
        include("*.apk")
        rename { "chatlite.apk" }
    }
}

tasks.register("stageReleaseArtifacts") {
    group = "distribution"
    description = "Stage all release artifacts with fixed filenames"
    dependsOn("stageDesktopReleaseArtifacts", "stageAndroidReleaseArtifact")
}

tasks.register<Jar>("customJar") {
    description = "桌面端jar打包"
    archiveBaseName.set("在线聊天App")
    archiveVersion.set(releaseVersion)

    from(sourceSets.main.get().output)

    // Include all runtime dependencies into the fat JAR so java -jar works
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) }
    }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC")
        exclude("META-INF/*.kotlin_module")
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "MainKt"
    }

    dependsOn("classes", ":proto:jar")
}

val verifyDesktopRuntime = tasks.register("verifyDesktopRuntime") {
    group = "verification"
    description = "Reject foreign native libraries and unused CLI/call dependencies in desktop packages"
    doLast {
        val artifacts = configurations.runtimeClasspath.get().resolvedConfiguration.resolvedArtifacts
        val forbidden = artifacts.filter {
            val id = it.moduleVersion.id
            (id.name == "netty-incubator-codec-native-quic" && it.classifier != desktopNativeClassifier) ||
                (id.group == "org.jetbrains.skiko" && id.name.startsWith("skiko-awt-runtime-") &&
                    id.name != "skiko-awt-runtime-$desktopSkikoPlatform") ||
                id.group in listOf("dev.onvoid.webrtc", "org.jline", "org.jboss.marshalling") ||
                id.name.startsWith("kotlin-test")
        }
        check(forbidden.isEmpty()) { "Unexpected desktop runtime dependencies: $forbidden" }
        check(artifacts.count { it.name == "netty-incubator-codec-native-quic" } == 1)
        check(artifacts.count { it.name.startsWith("skiko-awt-runtime-") } == 1)
    }
}
tasks.matching { it.name in listOf("check", "createDistributable", "createReleaseDistributable", "customJar",
    "packageReleaseMsi", "packageReleaseDmg", "packageReleaseDeb", "packageMsi", "packageDmg", "packageDeb") }
    .configureEach { dependsOn(verifyDesktopRuntime) }

tasks.register("desktopSizeReport") {
    group = "distribution"
    description = "Report runtime JAR sizes and generated desktop image/installer sizes"
    dependsOn(verifyDesktopRuntime)
    doLast {
        val artifacts = configurations.runtimeClasspath.get().resolvedConfiguration.resolvedArtifacts
        val report = buildString {
            appendLine("Desktop platform: $desktopNativeClassifier")
            appendLine("Runtime dependencies: ${artifacts.sumOf { it.file.length() }} bytes")
            artifacts.sortedByDescending { it.file.length() }.forEach {
                appendLine("${it.file.length()}\t${it.moduleVersion.id}\t${it.classifier.orEmpty()}")
            }
            val binaries = layout.buildDirectory.dir("compose/binaries").get().asFile
            binaries.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { variant ->
                variant.listFiles()?.sortedBy { it.name }?.forEach { component ->
                    val bytes = if (component.isFile) component.length()
                        else component.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    appendLine("$bytes\t${component.relativeTo(projectDir)}")
                }
            }
        }
        val output = layout.buildDirectory.file("reports/packaging/desktop-size.txt").get().asFile
        output.parentFile.mkdirs()
        output.writeText(report)
        logger.lifecycle(report)
    }
}

tasks.register("customApkRelease") {
    group = "distribution"
    description = "Build the Android release APK for the androidApp module"
    dependsOn(":androidApp:assembleRelease")
}

tasks.register("customApkDebug") {
    group = "distribution"
    description = "Build the Android debug APK for the androidApp module"
    dependsOn(":androidApp:assembleDebug")
}

// detekt 静态分析配置
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(file("$rootDir/config/detekt/detekt.yml"))
    source.setFrom("src/commonMain/kotlin", "src/desktopMain/kotlin")
}

tasks.register("customApk") {
    group = "distribution"
    description = "Build the default Android release APK for the androidApp module"
    dependsOn(tasks.named("customApkRelease"))
}

tasks.register("customProguardReleaseJars") {
    group = "distribution"
    description = "Run the same ProGuard pipeline used by release installers"
    dependsOn("proguardReleaseJars")
}

tasks.register("runDesktopProguard") {
    group = "distribution"
    description = "Run the local desktop ProGuard step"
    dependsOn("customProguardReleaseJars")
}

// CLI 可执行 Fat JAR 任务
tasks.register<Copy>("cliJar") {
    group = "cli"
    description = "Build and copy the independent headless CLI executable"
    dependsOn(":cli:fatJar")
    from(project(":cli").layout.buildDirectory.dir("libs")) { include("chatlite-cli-*.jar") }
    into(layout.buildDirectory.dir("libs"))
}

// Do not run custom ProGuard automatically on every JAR build in CI.
// Keep the task available for manual invocation if needed.
// tasks.named<Jar>("jar") {
//     finalizedBy(tasks.named("customProguardReleaseJars"))
// }
