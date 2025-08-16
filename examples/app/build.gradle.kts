import gobley.gradle.GobleyHost
import gobley.gradle.rust.dsl.useRustUpLinker
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.konan.target.Architecture

plugins {
    kotlin("multiplatform")
    id("dev.gobley.rust")
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    applyDefaultHierarchyTemplate()
    androidTarget {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
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
        androidNativeArm64(),
        androidNativeArm32(),
        androidNativeX64(),
        androidNativeX86(),
    ).forEach {
        it.compilations.configureEach {
            compileTaskProvider.configure {
                val linkerFlagsArg = StringBuilder().apply {
                    // Override Konan properties to link libunwind.a
                    append("-Xoverride-konan-properties=linkerKonanFlags.")
                    append(it.konanTarget.name)
                    // Copied from https://github.com/JetBrains/kotlin/blob/6dff5659f42b0b90863d10ee503efd5a8ebb1034/kotlin-native/konan/konan.properties#L839
                    append("=-lm -lc++_static -lc++abi -landroid -llog -latomic ")
                    // Find the directory containing libunwind.a
                    val ndkHostTag = when (GobleyHost.Platform.current) {
                        GobleyHost.Platform.Windows -> "windows-x86_64"
                        GobleyHost.Platform.MacOS -> "darwin-x86_64"
                        GobleyHost.Platform.Linux -> "linux-x86_64"
                    }
                    val toolchainDir = android.ndkDirectory
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
                    append("-L${clangRuntimeDir.absolutePath}")
                }.toString()
                compilerOptions.freeCompilerArgs.add(linkerFlagsArg)
            }
        }
    }

    // TODO: Generate .def file with pkg-config automatically
    // macOS: brew install pkg-config gtk4
    // Debian: apt install pkg-config libgtk-4-dev
    //
    // headers = gtk/gtk.h
    // compilerOpts = $(pkg-config --cflags gtk4)
    // linkerOpts = $(pkg-config --libs gtk4)
    //
    // TODO: Support cross-compilation
    // arrayOf(
    //     linuxX64(),
    //     linuxArm64(),
    // ).forEach {
    //     it.binaries.executable {
    //         entryPoint = "dev.gobley.uniffi.examples.app.main"
    //     }
    //     it.compilations.getByName("main") {
    //         cinterops.register("gtk") {
    //             defFile("src/gtkMain/cinterop/gtk.def")
    //             packageName("org.gnome.gitlab.gtk")
    //         }
    //     }
    // }

    if (GobleyHost.Platform.MacOS.isCurrent) {
        arrayOf(
            macosArm64(),
            macosX64(),
        ).forEach {
            it.binaries.executable {
                entryPoint = "dev.gobley.uniffi.examples.app.main"
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
            // TODO: Test the following in a dedicated test, not in an example. See #52 for more details.
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

        // val gtkMain by creating
        // linuxMain {
        //     dependsOn(gtkMain)
        // }

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

android {
    namespace = "dev.gobley.uniffi.examples.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.gobley.uniffi.examples.app"
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
