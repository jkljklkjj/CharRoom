import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform") version "1.8.10"
    id("org.jetbrains.compose") version "1.5.0"
    id("com.android.application") version "7.4.2"
    kotlin("plugin.serialization") version "1.8.10"
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
    jvm("desktop") {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    android {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
                implementation("javax.xml.bind:jaxb-api:2.3.1")
                implementation("org.glassfish.jaxb:jaxb-runtime:2.3.1")
                implementation("com.konghq:unirest-java:3.13.6")
                implementation("io.netty:netty-all:4.1.68.Final")
                implementation("org.slf4j:slf4j-simple:1.7.32")
                implementation("org.jboss.marshalling:jboss-marshalling:2.0.10.Final")
                implementation("org.jboss.marshalling:jboss-marshalling-river:2.0.10.Final")
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
                implementation("androidx.compose.ui:ui:1.7.5")
                implementation("androidx.compose.material:material:1.7.5")
                implementation("androidx.compose.ui:ui-tooling-preview:1.7.5")
                implementation("androidx.activity:activity-compose:1.9.3")
                implementation("androidx.appcompat:appcompat:1.7.0")
                implementation("javax.xml.bind:jaxb-api:2.3.1")
                implementation("org.glassfish.jaxb:jaxb-runtime:2.3.2")
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
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.charroom"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
    implementation("com.google.android.gms:play-services-pal:20.3.0")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
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
//    from(kotlin.sourceSets["commonMain"].kotlin.srcDirs)
//    from(kotlin.sourceSets["desktopMain"].kotlin.srcDirs)
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