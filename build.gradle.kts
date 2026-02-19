import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform") version "2.2.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
    id("org.jetbrains.compose") version "1.7.0"
    // Add the Java plugin so the protobuf plugin can be applied (it requires Java or Android plugin)
    // id("java")
    // Android plugin is applied conditionally below; do not apply by default so desktop-only builds don't configure Android tasks
    // id("com.android.application") version "8.11.2"
}

// Protobuf Gradle plugin removed for multiplatform root; rely on pre-generated sources in src/desktopMain/java

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    mavenCentral()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
    google()
}

kotlin {
    jvmToolchain(17)

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
                implementation("org.glassfish.jaxb:jaxb-runtime:4.0.5")
                implementation("com.konghq:unirest-java:3.14.5")
                implementation("io.netty:netty-all:4.1.68.Final")
                implementation("org.jboss.marshalling:jboss-marshalling:2.0.10.Final")
                implementation("org.jboss.marshalling:jboss-marshalling-river:2.0.10.Final")
                implementation("org.slf4j:slf4j-api:2.0.17")
                implementation("ch.qos.logback:logback-classic:1.5.18")
                implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.20.0")
                // Kermit core (multiplatform logging)
                implementation("co.touchlab:kermit:1.2.2")
                implementation("com.google.protobuf:protobuf-javalite:3.21.12")
                implementation("com.google.protobuf:protobuf-kotlin:3.21.12")
            }
            // generated Kotlin (if using a kotlin protoc plugin)
            kotlin.srcDir("build/generated/source/proto/commonMain/kotlin")
            // generated Java (protoc java builtin with 'lite' option writes into e.g. build/generated/source/proto/<variant>/java)
            kotlin.srcDir("build/generated/source/proto/main/java")
            kotlin.srcDir("build/generated/source/proto/debug/java")
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                // Depend on proto subproject that produces generated proto classes
                implementation(project(":proto"))
            }
            // Include proto subproject generated Java sources so they are compiled into desktop classes
            kotlin.srcDir("proto/build/generated/source/proto/main/java")
        }
        val desktopTest by getting {
            kotlin.srcDir("src/androidUnitTest/kotlin")
        }
    }
}

// Apply the Android plugin only when explicitly requested via -PincludeAndroid=true
if (project.findProperty("includeAndroid") == "true") {
    // Load the Android-specific configuration from a separate script to avoid unresolved references
    apply(from = "android.gradle.kts")
}

// Configure android only if the Android plugin is present
// if (plugins.hasPlugin("com.android.application")) {
//     android {
//         namespace = "com.example.charroom"
//         compileSdk = 36
//         defaultConfig {
//             applicationId = "com.example.charroom"
//             minSdk = 26
//             targetSdk = 34
//             versionCode = 2
//             versionName = "1.1"
//         }
//         compileOptions {
//             sourceCompatibility = JavaVersion.VERSION_17
//             targetCompatibility = JavaVersion.VERSION_17
//         }
//         buildTypes {
//             release {
//                 isMinifyEnabled = false
//                 proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
//             }
//         }
//     }
// }

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "CharRoom"
            packageVersion = "1.0.0"
        }
    }
}

tasks.register<Jar>("customJar") {
    archiveBaseName.set("在线聊天App")
    archiveVersion.set("1.0.0")
    // 从 commonMain/kotlin 和 desktopMain/kotlin 目录中包含所有文件
    from(kotlin.sourceSets["commonMain"].kotlin.srcDirs)
    from(kotlin.sourceSets["desktopMain"].kotlin.srcDirs)

    // 包含 desktop Java sources (in case generated sources were placed here)
    from("src/desktopMain/java") { into("") }

    // 包含 resources（关键修改，确保 resources 被打包进 jar）
    from(kotlin.sourceSets["commonMain"].resources.srcDirs)
    from(kotlin.sourceSets["desktopMain"].resources.srcDirs)
    // 兼容性：如果 resources 没有放在上面的位置，也把经典的 src/.../resources 目录作为后备
    from("src/commonMain/resources") { into("") }
    from("src/desktopMain/resources") { into("") }

    from("build/classes/kotlin/desktop/main")
    // additional possible class output locations
    from("build/classes/java/desktop/main") { into("") }
    from("build/classes/java/desktopMain") { into("") }

    // Include protobuf generated Java sources and compiled java classes so MessageProtos is packaged
    from("build/generated/source/proto/main/java") { into("") }
    from("build/generated/source/proto/debug/java") { into("") }
    // sometimes the proto outputs are under other paths; include common ones
    from("build/generated/source/proto") { into("") }
    // include compiled java classes for desktop (if present)
    from("build/classes/java/desktopMain") { into("") }

    // 包含 protobuf 生成和提取的 include 文件，以便运行时或调试时可以访问 .proto 依赖
    from("build/extracted-include-protos") { into("extracted-include-protos") }

    // Include dependencies
    doFirst {
        // Include the proto project's produced jar contents (protoJar) so generated proto classes are guaranteed
        // to be present in the fat jar. Use doFirst to defer evaluation until the task runs.
        try {
            val protoJarTask = project(":proto").tasks.named("protoJar").get()
            val protoArchive = protoJarTask.property("archiveFile").let { it as org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile> }
            val protoFile = protoArchive.get().asFile
            if (protoFile.exists()) {
                from(zipTree(protoFile))
            }
        } catch (e: Exception) {
            // If protoJar isn't available for some reason, fall back to including proto output folders
            // (do not fail the configuration here; packaging will still attempt to include other outputs)
        }

        from({
            configurations["desktopRuntimeClasspath"].filter { it.name.endsWith("jar") }.map { zipTree(it) }
        })
    }

    // Set duplicates strategy
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // Specify the main class
    manifest {
        attributes["Main-Class"] = "MainKt"
    }

    // Ensure project is assembled (compiled) before creating the custom jar so compiled .class files are present
    dependsOn("assemble")
    // Ensure proto subproject classes are built before packaging
    dependsOn(":proto:protoJar")
}
