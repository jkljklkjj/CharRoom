plugins {
    id("com.android.application") version "8.11.2"
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

