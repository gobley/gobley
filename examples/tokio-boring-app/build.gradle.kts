import gobley.gradle.GobleyHost
import gobley.gradle.cargo.dsl.android
import gobley.gradle.cargo.dsl.appleMobile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("dev.gobley.cargo")
    id("dev.gobley.uniffi")
    alias(libs.plugins.kotlin.atomicfu)
    // 1. Switched from android.application to android.kotlin.multiplatform.library
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    id(libs.plugins.kotlin.serialization.get().pluginId)
}

uniffi {
    bindgenFromPath(rootProject.layout.projectDirectory.dir("crates/gobley-uniffi-bindgen"))
    generateFromLibrary()
}

if (GobleyHost.Platform.Windows.isCurrent) {
    cargo {
        builds.android {
            variants {
                buildTaskProvider.configure {
                    additionalEnvironment.put("CMAKE_GENERATOR", "Ninja")
                }
                checkTaskProvider.configure {
                    additionalEnvironment.put("CMAKE_GENERATOR", "Ninja")
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

kotlin {
    // 2. The new Unified Android Library block
    android {
        namespace = "dev.gobley.uniffi.examples.tokioboringapp"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = 24

        // Libraries don't use applicationId, versionCode, or versionName.
        // Those move to your actual Android App module.

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }

        packaging {
            resources {
                excludes += "/META-INF/{AL2.0,LGPL2.1}"
            }
        }

//        buildTypes {
//            getByName("release") {
//                // Libraries don't usually need signingConfigs, but keeping for parity if needed
//                isMinifyEnabled = false
//            }
//        }
    }

    // 3. Replaces both java { toolchain } and androidTarget { compilerOptions }
    jvmToolchain(17)

    if (GobleyHost.Platform.MacOS.isCurrent) {
        arrayOf(
            iosArm64(),
            iosSimulatorArm64(),
            iosX64(),
        ).forEach {
            it.binaries.framework {
                baseName = "TokioBoringAppKotlin"
                isStatic = true
                binaryOption("bundleId", "dev.gobley.uniffi.examples.tokioboringapp.kotlin")
                binaryOption("bundleVersion", "0")
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}