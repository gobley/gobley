plugins {
    kotlin("multiplatform")
    id("dev.gobley.cargo")
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    @Suppress("UnstableApiUsage")
    androidLibrary {
        namespace = "dev.gobley.tests.gradle.androidkmplibrary"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = 21
    }
}
