import gobley.gradle.GobleyHost
import gobley.gradle.cargo.dsl.android
import gobley.gradle.cargo.dsl.appleMobile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension

plugins {
    kotlin("multiplatform")
    id("dev.gobley.cargo")
    id("dev.gobley.uniffi")
    alias(libs.plugins.kotlin.atomicfu)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    id(libs.plugins.kotlin.serialization.get().pluginId)
}

// 1. Define SDK versions up here so both the Kotlin block and the Windows workaround can share them safely
val androidMinSdk = 26
val androidCompileSdk = libs.versions.android.compileSdk.get().toInt()

if (GobleyHost.Platform.Windows.isCurrent) {
    afterEvaluate {
        // THE FIX: Fetch the KMP Components Extension to access the resolved SDK and NDK paths
        val androidComponents = project.extensions.getByType(KotlinMultiplatformAndroidComponentsExtension::class.java)

        cargo {
            // A workaround for #207
            builds.android {
                val envVariables = rustTarget.ndkEnvVariables(
                    // Extract the File object from the Gradle Provider
                    sdkRoot = androidComponents.sdkComponents.sdkDirectory.get().asFile,
                    apiLevel = androidMinSdk,
                    // Because AGP now gives us the exact resolved NDK directory,
                    // we don't even need to pass the ndkVersion string anymore!
                    ndkRoot = androidComponents.sdkComponents.ndkDirectory.get().asFile
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
    // 2. Unified Android DSL block (replaces both androidTarget {} and the top-level android {} block)
    android {
        namespace = "dev.gobley.uniffi.examples.tokioblake3app" // Inferred from your bundleId
        compileSdk = androidCompileSdk
        minSdk = androidMinSdk

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvmToolchain(17) // Replaces the top-level java { toolchain { ... } }

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