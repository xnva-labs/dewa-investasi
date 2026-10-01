// build.gradle.kts (ROOT repo, sejajar dengan folder app/)
//
// Set versi ini sudah dipasangkan agar saling cocok:
//   Gradle 8.9  +  AGP 8.7.3  +  Kotlin 2.0.21  +  KSP 2.0.21-1.0.28
// Kalau mengubah versi Kotlin, versi KSP HARUS ikut diganti (angka depan KSP = versi Kotlin).

plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}
