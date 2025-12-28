import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform") version "2.2.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
    id("org.jetbrains.compose") version "1.7.0"
    id("com.android.application") version "8.11.2"
}

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
    androidTarget() {
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
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
        val desktopTest by getting {
            kotlin.srcDir("src/androidUnitTest/kotlin")
        }
        val androidMain by getting {
            dependencies {
                implementation("androidx.compose.ui:ui:1.9.1")
                implementation("androidx.compose.material:material:1.9.1")
                implementation("androidx.compose.ui:ui-tooling-preview:1.9.1")
                implementation("androidx.activity:activity-compose:1.11.0")
                implementation("androidx.appcompat:appcompat:1.7.1")
                implementation("org.glassfish.jaxb:jaxb-runtime:4.0.5")
                implementation(compose.desktop.currentOs)
            }
        }
        val androidUnitTest by getting {
            kotlin.srcDir("src/androidUnitTest/kotlin")
        }
    }
}

android {
    namespace = "com.example.charroom"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.charroom"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
dependencies {
    implementation("com.google.android.gms:play-services-pal:22.1.0")
}

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

    // 包含 resources（关键修改，确保 resources 被打包进 jar）
    from(kotlin.sourceSets["commonMain"].resources.srcDirs)
    from(kotlin.sourceSets["desktopMain"].resources.srcDirs)
    // 兼容性：如果 resources 没有放在上面的位置，也把经典的 src/.../resources 目录作为后备
    from("src/commonMain/resources") { into("") }
    from("src/desktopMain/resources") { into("") }

    from("build/classes/kotlin/desktop/main")

    // Include dependencies
    doFirst {
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
}