import gobley.gradle.GobleyHost
import gobley.gradle.rust.dsl.useRustUpLinker
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("dev.gobley.rust")
    id("dev.gobley.cargo")
    id("dev.gobley.uniffi")
    alias(libs.plugins.kotlin.atomicfu)
    alias(libs.plugins.android.library)
}

uniffi {
    bindgenFromPath(rootProject.layout.projectDirectory.dir("bindgen"))
    generateFromLibrary {
        packageName = "gobley.uniffi.examples.customtypes"
        customType("Url") {
            typeName = "io.ktor.http.Url"
            imports.add("io.ktor.http.Url")
            lift = "Url({})"
            lower = "{}.toString()"
        }
    }
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }
    jvmToolchain(17)
    jvm("desktop")
    arrayOf(
        mingwX64(),
    ).forEach { nativeTarget ->
        nativeTarget.compilations.getByName("test") {
            useRustUpLinker()
        }
    }

    linuxX64()
    linuxArm64()
    if (GobleyHost.Platform.MacOS.isCurrent) {
        iosArm64()
        iosSimulatorArm64()
        iosX64()
        macosArm64()
        macosX64()
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.ktor.http)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotest.assertions.core)
            }
        }
    }
}

android {
    namespace = "dev.gobley.uniffi.examples.customtypes"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        consumerProguardFiles("proguard-rules.pro")
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
