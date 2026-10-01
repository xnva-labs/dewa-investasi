import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystorePath: String? = System.getenv("KEYSTORE_PATH")
val xnaiBaseUrl = providers.environmentVariable("XNAI_BASE_URL").orNull?.trim().orEmpty()

android {
    namespace = "com.xnvalabs.smarteyex"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xnvalabs.smarteyex"
        minSdk = 31
        targetSdk = 36
        versionCode = 4
        versionName = "0.3.1"
    }

    val xnaiBaseUrlQuoted = "\"" + xnaiBaseUrl.replace("\"", "\\\"") + "\""

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
            buildConfigField("String", "XNAI_BASE_URL", xnaiBaseUrlQuoted)
        }
        release {
            buildConfigField("String", "XNAI_BASE_URL", xnaiBaseUrlQuoted)
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
            require(xnaiBaseUrl.startsWith("https://")) { "XNAI_BASE_URL wajib diisi dengan URL HTTPS untuk release." }
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.activity:activity-compose:1.12.4")
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

    testImplementation("junit:junit:4.13.2")

}
