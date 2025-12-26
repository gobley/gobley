import gobley.gradle.GobleyHost
import gobley.gradle.cargo.dsl.android
import gobley.gradle.cargo.dsl.appleMobile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("dev.gobley.cargo")
    id("dev.gobley.uniffi")
    alias(libs.plugins.kotlin.atomicfu)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    id(libs.plugins.kotlin.serialization.get().pluginId)
}

if (GobleyHost.Platform.Windows.isCurrent) {
    afterEvaluate {
        cargo {
            // A workaround for #207
            builds.android {
                val envVariables = rustTarget.ndkEnvVariables(
                    sdkRoot = android.sdkDirectory,
                    apiLevel = android.defaultConfig.minSdk ?: 21,
                    ndkVersion = android.ndkVersion,
                    ndkRoot = android.ndkPath?.let(::File),
                ).toMutableMap()
                val envVariableNamesToModify = arrayOf(
                    "ANDROID_HOME",
                    "ANDROID_NDK_HOME",
                    "ANDROID_NDK_ROOT",
                    "CC_${rustTarget.rustTriple}",
                    "CXX_${rustTarget.rustTriple}",
                    "AR_${rustTarget.rustTriple}",
                    "RANLIB_${rustTarget.rustTriple}",
                )
                for (envVariableNameToModify in envVariableNamesToModify) {
                    var envVariable = envVariables[envVariableNameToModify]!! as File
                    if (envVariableNameToModify.startsWith("CC_")) {
                        envVariable = envVariable.parentFile!!.resolve("clang.exe")
                    }
                    if (envVariableNameToModify.startsWith("CXX_")) {
                        envVariable = envVariable.parentFile!!.resolve("clang++.exe")
                    }
                    envVariables[envVariableNameToModify] = envVariable.path.replace('\\', '/')
                }
                variants {
                    buildTaskProvider.configure {
                        additionalEnvironment.putAll(envVariables)
                    }
                    checkTaskProvider.configure {
                        additionalEnvironment.putAll(envVariables)
                    }
                }
            }
        }
    }
}
if (GobleyHost.Platform.MacOS.isCurrent) {
    cargo {
        builds.appleMobile {
            if (rustTarget.cinteropName == "ios") {
                variants {
                    buildTaskProvider.configure {
                        additionalEnvironment.put("IPHONEOS_DEPLOYMENT_TARGET", "16.0.0")
                    }
                }
            }
        }
    }
}

uniffi {
    bindgenFromPath(rootProject.layout.projectDirectory.dir("crates/gobley-uniffi-bindgen"))
    generateFromLibrary()
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    if (GobleyHost.Platform.MacOS.isCurrent) {
        arrayOf(
            iosArm64(),
            iosSimulatorArm64(),
            iosX64(),
        ).forEach {
            it.binaries.framework {
                baseName = "TokioBlake3AppKotlin"
                isStatic = true
                binaryOption("bundleId", "dev.gobley.uniffi.examples.tokioblake3app.kotlin")
                binaryOption("bundleVersion", "0")
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(libs.kotlinx.serialization.json)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}

android {
    namespace = "dev.gobley.uniffi.examples.tokioblake3app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.gobley.uniffi.examples.tokioblake3app"
        minSdk = 24
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1"
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
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}