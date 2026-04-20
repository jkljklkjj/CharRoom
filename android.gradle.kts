// android.gradle.kts - applied from root when includeAndroid=true
// The Android plugin is declared in the root project's plugins {} block, so this script only configures Android DSL.

android {
    namespace = "com.chatlite.charroom"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.chatlite.charroom"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    // Example Android dependency; adjust as needed
    implementation("com.google.android.gms:play-services-pal:22.1.0")
}

// Hook the existing Kotlin Multiplatform layout into Android's sourceSets so files under
// `src/androidMain` are compiled by the Android application plugin as part of the app.
afterEvaluate {
    project.extensions.findByName("android")?.let { androidExt ->
        try {
            val sourceSets = androidExt::class.java.getMethod("getSourceSets").invoke(androidExt)
            val main = sourceSets::class.java.getMethod("get", String::class.java).invoke(sourceSets, "main")
            // add kotlin/java and resources dirs from src/androidMain
            main::class.java.getMethod("getJava").invoke(main)
            // add kotlin sources
            val javaSrcDirs = main::class.java.getMethod("getJava").invoke(main)
            javaSrcDirs::class.java.getMethod("srcDir", Any::class.java).invoke(javaSrcDirs, project.file("src/androidMain/kotlin"))
            // add res dir if exists
            val resDirs = main::class.java.getMethod("getRes").invoke(main)
            resDirs::class.java.getMethod("srcDir", Any::class.java).invoke(resDirs, project.file("src/androidMain/res"))
        } catch (ignored: Throwable) {
            // best-effort only
            logger.warn("Failed to attach src/androidMain to Android sourceSets: ${'$'}{ignored.message}")
        }
    }
}

