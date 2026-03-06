import gobley.gradle.GobleyHost
import gobley.gradle.rust.dsl.useRustUpLinker
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.konan.target.Architecture

plugins {
    kotlin("multiplatform")
    id("dev.gobley.rust")
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    applyDefaultHierarchyTemplate()

    // 1. The fully modernized AGP 8.12+ / AGP 9+ configuration block!
    android {
        namespace = "dev.gobley.uniffi.examples.app"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = 24

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }

        packaging {
            resources {
                excludes += "/META-INF/{AL2.0,LGPL2.1}"
            }
        }
    }

    jvmToolchain(17)
    jvm()

    arrayOf(
        mingwX64(),
    ).forEach {
        it.binaries.executable {
            entryPoint = "gobley.uniffi.examples.app.main"
        }
        it.compilations.configureEach {
            useRustUpLinker()
        }
    }

    // Test using command-line
    arrayOf(
        androidNativeArm64(),
        androidNativeArm32(),
        androidNativeX64(),
        androidNativeX86(),
        linuxX64(),
        linuxArm64(),
    ).forEach {
        it.binaries.executable {
            entryPoint = "gobley.uniffi.examples.app.main"
        }
    }

    arrayOf(
        androidNativeArm32()
    ).forEach {
        it.compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions.freeCompilerArgs.add(
                    "-Xoverride-konan-properties=linkerKonanFlags.android_arm32=-lgcc -lm -lc++_static -lc++abi -landroid -llog -latomic"
                )
            }
        }
    }

    arrayOf(
        androidNativeArm64(),
        androidNativeX64(),
        androidNativeX86(),
    ).forEach {
        it.binaries.configureEach {
            val ndkHostTag = when (GobleyHost.Platform.current) {
                GobleyHost.Platform.Windows -> "windows-x86_64"
                GobleyHost.Platform.MacOS -> "darwin-x86_64"
                GobleyHost.Platform.Linux -> "linux-x86_64"
            }
            val toolchainDir = androidComponents.sdkComponents.ndkDirectory.get().asFile
                .resolve("toolchains/llvm/prebuilt")
                .resolve(ndkHostTag)
            val clangResourceDir = toolchainDir
                .resolve("lib/clang")
                .listFiles()
                ?.firstOrNull { file -> !file.name.startsWith(".") }
                ?: error("Couldn't find Clang resource directory")
            val clangRuntimeDir = clangResourceDir
                .resolve("lib/linux")
                .resolve(
                    when (it.konanTarget.architecture) {
                        Architecture.ARM64 -> "aarch64"
                        Architecture.ARM32 -> "arm"
                        Architecture.X64 -> "x86_64"
                        Architecture.X86 -> "i386"
                    }
                )
            linkerOpts("-L${clangRuntimeDir.absolutePath}")
        }
    }

    if (GobleyHost.Platform.MacOS.isCurrent) {
        arrayOf(
            macosArm64(),
            macosX64(),
        ).forEach {
            it.binaries.executable {
                entryPoint = "gobley.uniffi.examples.app.main"
            }
        }

        arrayOf(
            iosArm64(),
            iosSimulatorArm64(),
            iosX64(),
            macosArm64(),
            macosX64(),
            tvosArm64(),
            tvosSimulatorArm64(),
            tvosX64(),
            watchosSimulatorArm64(),
            watchosDeviceArm64(),
            watchosX64(),
            watchosArm64(),
            watchosArm32(),
        ).forEach {
            it.binaries.framework {
                baseName = "ExamplesAppKotlin"
                isStatic = true
                binaryOption("bundleId", "dev.gobley.uniffi.examples.app.kotlin")
                binaryOption("bundleVersion", "0")
                export(project(":examples:arithmetic-procmacro"))
                export(project(":examples:todolist"))
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":examples:arithmetic-procmacro"))
            api(project(":examples:todolist"))
        }

        commonTest {
            kotlin.srcDir(project.layout.projectDirectory.dir("../arithmetic-procmacro/src/commonTest/kotlin"))
            kotlin.srcDir(project.layout.projectDirectory.dir("../todolist/src/commonTest/kotlin"))
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotest.assertions.core)
            }
        }

        androidMain.dependencies {
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.tooling)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.androidx.activity.compose)
        }

        val cmdlineMain by creating {
            dependsOn(commonMain.get())
        }
        androidNativeMain {
            dependsOn(cmdlineMain)
        }
        linuxMain {
            dependsOn(cmdlineMain)
        }
    }
}

composeCompiler {
    targetKotlinPlatforms = setOf(KotlinPlatformType.androidJvm)
}