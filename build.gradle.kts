import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.Copy

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.4.2")
        classpath("com.guardsquare:proguard-gradle:7.8.2")
    }
}

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
    implementation("io.netty:netty-common:4.2.5.Final")
    implementation("io.netty:netty-buffer:4.2.5.Final")
    implementation("io.netty:netty-transport:4.2.5.Final")
    implementation("io.netty:netty-resolver:4.2.5.Final")
    implementation("io.netty:netty-codec:4.2.5.Final")
    implementation("io.netty:netty-codec-http:4.2.5.Final")
    implementation("io.netty:netty-handler:4.2.5.Final")
    implementation("org.jboss.marshalling:jboss-marshalling:2.3.0")
    implementation("org.jboss.marshalling:jboss-marshalling-river:2.3.0")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("org.slf4j:slf4j-simple:2.0.17")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.2")
    implementation("co.touchlab:kermit:2.1.0")
    implementation("io.github.oshai:kotlin-logging:6.0.3") // Kotlin官方日志库
    implementation("com.google.protobuf:protobuf-java:4.34.1")
    implementation("com.google.protobuf:protobuf-kotlin:4.34.1")
    implementation(project(":proto"))
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
}

// 源集配置
sourceSets {
    main {
        kotlin.srcDirs("src/commonMain/kotlin", "src/desktopMain/kotlin")
        resources.srcDirs("src/commonMain/resources", "src/desktopMain/resources")
    }
    create("cli") {
        kotlin.srcDirs("src/cliMain/kotlin")
        resources.srcDirs("src/commonMain/resources", "src/cliMain/resources")
        dependencies {
            implementation(sourceSets.main.get().output)
            implementation(sourceSets.main.get().compileClasspath)
        }
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

        jvmArgs += listOf(
            "-Dfile.encoding=UTF-8",
            "-Dsun.jnu.encoding=UTF-8",
            "-Duser.language=zh",
            "-Duser.country=CN",
            "-Dconsole.encoding=UTF-8"
        )

        // 使用JDK 21
        javaHome = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        }.get().metadata.installationPath.asFile.absolutePath

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
            modules("java.prefs", "jdk.unsupported")
            packageName = "chatlite"
            packageVersion = "1.0.0"
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

    val isWindows = System.getProperty("os.name").contains("Windows", ignoreCase = true)

    dependsOn(tasks.matching {
        (it.name.startsWith("package") || it.name.startsWith("jpackage")) &&
            (!isWindows || it.name != "packageReleaseMsi")
    })

    if (isWindows) {
        dependsOn("buildTrimmedMsi")
    }
}

tasks.register<Copy>("stageDesktopReleaseArtifacts") {
    group = "distribution"
    description = "Stage desktop release installers with fixed filenames"
    dependsOn("buildInstallers")
    into(layout.buildDirectory.dir("release-artifacts/desktop"))

    from(layout.buildDirectory.dir("compose/binaries")) {
        include("**/*.msi")
        rename { "chatlite.msi" }
    }

    from(layout.buildDirectory.dir("compose/binaries")) {
        include("**/*.dmg")
        rename { "chatlite.dmg" }
    }

    from(layout.buildDirectory.dir("compose/binaries")) {
        include("**/*.deb")
        rename { "chatlite.deb" }
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

// 自定义任务：使用裁剪后的最小JRE构建MSI
tasks.register("buildTrimmedMsi") {
    group = "distribution"
    description = "Build MSI with trimmed minimal JRE (reduces size by ~25MB)"
    dependsOn("packageReleaseMsi")

    doLast {
        val originalRuntime = file("build/compose/tmp/main/release/runtime")
        val trimmedRuntime = rootProject.projectDir.parentFile.resolve("minimal-jre")

        if (trimmedRuntime.exists()) {
            // 替换原runtime为裁剪版本
            originalRuntime.deleteRecursively()
            trimmedRuntime.copyRecursively(originalRuntime)

            // 重新打包MSI
            delete(file("build/compose/binaries/main-release/msi/chatlite-1.0.0.msi"))
            exec {
                workingDir = rootProject.projectDir
                if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
                    commandLine("gradlew.bat", "--rerun-tasks", "packageReleaseMsi")
                } else {
                    commandLine("./gradlew", "--rerun-tasks", "packageReleaseMsi")
                }
            }
        } else {
            logger.warn("Trimmed JRE not found at ${trimmedRuntime.absolutePath}")
        }
    }
}

tasks.register<Jar>("customJar") {
    description = "桌面端jar打包"
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

    dependsOn("assemble")
    dependsOn(":proto:protoJar")
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

tasks.register("customApk") {
    group = "distribution"
    description = "Build the default Android release APK for the androidApp module"
    dependsOn(tasks.named("customApkRelease"))
}

tasks.register<proguard.gradle.ProGuardTask>("customProguardReleaseJars") {
    description = "Run ProGuard on the desktop JAR output"
    dependsOn(":proto:jar")
    configuration("proguard-rules.pro")
    injars("build/libs/CharRoom-1.0-SNAPSHOT.jar")
    outjars("build/libs/CharRoom-1.0-SNAPSHOT-obfuscated.jar")
    libraryjars(files(configurations.compileClasspath.get().files + configurations.runtimeClasspath.get().files))
    val javaHome = System.getProperty("java.home")
    val modulesFile = file("$javaHome/lib/modules")
    val jrtFsFile = file("$javaHome/lib/jrt-fs.jar")
    val jmodsDir = file("$javaHome/jmods")
    val jdkLibs = mutableListOf<File>()
    if (modulesFile.exists()) {
        jdkLibs.add(modulesFile)
    }
    if (jrtFsFile.exists()) {
        jdkLibs.add(jrtFsFile)
    }
    if (jmodsDir.exists() && jmodsDir.isDirectory) {
        jdkLibs.addAll(jmodsDir.listFiles { file -> file.extension == "jmod" }?.toList().orEmpty())
    }
    if (jdkLibs.isNotEmpty()) {
        libraryjars(files(jdkLibs))
    }
    verbose()
    ignorewarnings()
}

tasks.register("runDesktopProguard") {
    group = "distribution"
    description = "Run the local desktop ProGuard step"
    dependsOn("customProguardReleaseJars")
}

// CLI 可执行 Fat JAR 任务
tasks.register<Jar>("cliJar") {
    description = "Build CLI chat client executable fat JAR"
    group = "cli"
    archiveBaseName.set("chatlite-cli")
    archiveVersion.set("1.0.0")

    from(sourceSets.named("cli").get().output)
    from(sourceSets.main.get().output)
    from(sourceSets.named("cli").get().allSource)

    // 打包全部运行时依赖
    from({
        configurations["cliRuntimeClasspath"].filter { it.name.endsWith(".jar") }.map { zipTree(it) }
    }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC")
        exclude("META-INF/*.kotlin_module")
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "CliMainKt"
    }
    dependsOn(tasks.named("compileKotlin"))
}

// Do not run custom ProGuard automatically on every JAR build in CI.
// Keep the task available for manual invocation if needed.
// tasks.named<Jar>("jar") {
//     finalizedBy(tasks.named("customProguardReleaseJars"))
// }
