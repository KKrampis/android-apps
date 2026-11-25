plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// CI passes VERSION_CODE (the workflow run number) so every release can update the previous one.
val ciVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
val baseVersionName = "1.0"

// Release signing comes from environment variables set by the GitHub workflow.
// When they are missing, the release build falls back to the debug key so the build still succeeds.
val keystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")
val hasReleaseKeystore = !keystorePath.isNullOrBlank() && file(keystorePath).exists()

android {
    namespace = "io.github.kkrampis.clipharbor"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.kkrampis.clipharbor"
        minSdk = 26
        targetSdk = 35
        versionCode = ciVersionCode
        versionName = "$baseVersionName.$ciVersionCode"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.md",
                "META-INF/versions/**",
                "META-INF/INDEX.LIST",
            )
        }
    }
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.4")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.5.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Reads YouTube pages anonymously, without an API key or a Google account.
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.5")

    // Offline speech recognition for transcripts. Language models are downloaded on demand.
    implementation("com.alphacephei:vosk-android:0.3.75") {
        exclude(group = "net.java.dev.jna")
    }
    implementation("net.java.dev.jna:jna:5.18.1@aar")
}
