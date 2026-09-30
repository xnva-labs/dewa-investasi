// app/build.gradle.kts
//
// API key TIDAK ditulis di sini. Dibaca saat build dari:
//   1) environment variable FINNHUB_API_KEY  -> dipakai di GitHub Actions (GitHub Secrets)
//   2) local.properties: finnhub_api_key=...  -> dipakai untuk build lokal (file ini di-.gitignore)
// Hasilnya masuk ke BuildConfig.FINNHUB_API_KEY yang dibaca MarketDataRepository.

import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun secret(envName: String, propName: String): String =
    (System.getenv(envName)?.takeIf { it.isNotBlank() } ?: localProps.getProperty(propName) ?: "").trim()

android {
    namespace = "com.xnvalabs.investmenttracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xnvalabs.investmenttracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField(
            "String",
            "FINNHUB_API_KEY",
            "\"${secret("FINNHUB_API_KEY", "finnhub_api_key")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // --- Compose (versi diatur oleh BOM) ---
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // --- AndroidX inti ---
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // --- Room (persistensi) ---
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // --- Retrofit + Gson (harga live) ---
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // --- WorkManager (background sync) ---
    implementation("androidx.work:work-runtime-ktx:2.10.0")
}
