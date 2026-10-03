import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystorePath: String? = System.getenv("KEYSTORE_PATH")

android {
    namespace = "com.xnvalabs.smarteyex"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xnvalabs.smarteyex"
        minSdk = 31
        targetSdk = 36
        versionCode = 5
        versionName = "0.4.0"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.configureEach {
    if (name.contains("Release", ignoreCase = true)) {
        doFirst {
            require(!keystorePath.isNullOrBlank()) { "KEYSTORE_PATH wajib diisi untuk release production yang ter-sign." }
            require(!System.getenv("KEYSTORE_PASSWORD").isNullOrBlank()) { "KEYSTORE_PASSWORD wajib diisi untuk release." }
            require(!System.getenv("KEY_ALIAS").isNullOrBlank()) { "KEY_ALIAS wajib diisi untuk release." }
            require(!System.getenv("KEY_PASSWORD").isNullOrBlank()) { "KEY_PASSWORD wajib diisi untuk release." }
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    val cameraX = "1.5.3"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")

    // Keep the AndroidX graph on the API 36-compatible line.
    // Some newer transitive releases require API 37 / AGP 9.1+.
    constraints {
        implementation("androidx.core:core:1.17.0") {
            version { strictly("1.17.0") }
        }
        implementation("androidx.core:core-ktx:1.17.0") {
            version { strictly("1.17.0") }
        }
        implementation("androidx.activity:activity:1.11.0") {
            version { strictly("1.11.0") }
        }
        implementation("androidx.activity:activity-compose:1.11.0") {
            version { strictly("1.11.0") }
        }
        implementation("androidx.activity:activity-ktx:1.11.0") {
            version { strictly("1.11.0") }
        }
    }

    // Face detection on-device (model bundled in the AAR) + TFLite runtime for the embedding model.
    // The embedding model file itself (face_embedding.tflite) is NOT in this repo; see FACE_RECOGNITION.md.
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("org.tensorflow:tensorflow-lite:2.16.1")

    testImplementation("junit:junit:4.13.2")

}
