plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Last.fm keys are injected at build time (-P or env) so no secret lives in the repo.
fun prop(name: String): String = (project.findProperty(name) as String?) ?: System.getenv(name) ?: ""

android {
    namespace = "org.melodyne.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.melodyne.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 6
        versionName = "0.5.0"

        resourceConfigurations += setOf("en")      // drop library translations
        ndk { abiFilters += "arm64-v8a" }          // one ABI keeps the APK small
        buildConfigField("String", "LASTFM_API_KEY", "\"${prop("LASTFM_API_KEY")}\"")
        buildConfigField("String", "LASTFM_API_SECRET", "\"${prop("LASTFM_API_SECRET")}\"")
    }

    signingConfigs {
        create("release") {
            // A fixed dev key (melodyne-dev.jks) keeps every build signed identically, so new APKs
            // install over old ones. It is public: swap in your own key before sharing builds widely.
            val dev = file("melodyne-dev.jks")
            storeFile = if (dev.exists()) dev else file(System.getenv("KEYSTORE_PATH") ?: "release.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "melodyne"
            keyAlias = System.getenv("KEY_ALIAS") ?: "melodyne"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "melodyne"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xno-call-assertions", "-Xno-param-assertions", "-Xno-receiver-assertions")
    }
    packaging {
        jniLibs { useLegacyPackaging = true }      // compress .so inside the APK
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.version", "/META-INF/*.kotlin_module",
            "/META-INF/com/android/build/gradle/**", "DebugProbesKt.bin",
            "kotlin-tooling-metadata.json", "/kotlin/**", "/*.properties",
        )
    }
    dependenciesInfo { includeInApk = false; includeInBundle = false }   // no opaque blobs (F-Droid friendly)
    lint { abortOnError = false; checkReleaseBuilds = false }
}

dependencies {
    // Compose without Material: foundation + ui only (Material3 would add ~1 MB).
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")

    // Audio
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.4.1")

    // Network
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.2")

    // Images + dynamic accent
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.palette:palette:1.0.0")

    testImplementation("junit:junit:4.13.2")
}
