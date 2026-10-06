// AGP 9+ memiliki dukungan Kotlin bawaan: plugin `org.jetbrains.kotlin.android` TIDAK boleh diterapkan lagi.
// Namun AGP hanya membawa KGP bawaan (versi lebih lama), sedangkan plugin Compose di bawah memakai Kotlin 2.4.20.
// Karena itu versi KGP dipaksa ke versi yang sama lewat classpath buildscript (sesuai panduan "Migrate to built-in Kotlin").
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    id("com.android.application") version "9.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
}
