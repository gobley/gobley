import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // 1. JetBrains kotlin("android") is intentionally gone! AGP 9 handles it.
    id("dev.gobley.cargo")
    id("dev.gobley.uniffi")
    alias(libs.plugins.kotlin.atomicfu)
    alias(libs.plugins.android.library)
}

uniffi {
    bindgenFromPath(rootProject.layout.projectDirectory.dir("crates/gobley-uniffi-bindgen"))
    generateFromLibrary {
        namespace = name.replace('-', '_')
        packageName = "coverall"
    }
}

// 2. The core Kotlin block now only handles global compiler configurations
kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17) // Use .set() for modern lazy properties
    }
}

android {
    namespace = "dev.gobley.uniffi.tests.uniffi.coverall"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk.abiFilters.add("arm64-v8a")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// 3. THE MODERN AGP 9 WAY: Map source set dependencies natively
dependencies {
    // Local Unit Tests (previously in sourceSets { test { ... } })
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotest.assertions.core)

    // Instrumented Android Tests (previously in sourceSets { androidTest { ... } })
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.kotest.assertions.core)
}