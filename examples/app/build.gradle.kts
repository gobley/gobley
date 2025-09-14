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
        androidNativeArm32()
    ).forEach {
        it.compilations.configureEach {
            compileTaskProvider.configure {
                // Override Konan properties to make sure libgcc.a is mentioned before any other
                // Rust static libraries to prevent "multiple definition of symbols" errors.
                //
                // The original linkerKonanFlags.android_arm32 is copied from:
                // https://github.com/JetBrains/kotlin/blob/6dff5659f42b0b90863d10ee503efd5a8ebb1034/kotlin-native/konan/konan.properties#L839
                //
                // By default, the Rust compiler automatically injects the `compiler_builtins` crate
                // during build, even when `#![no_std]` is enabled. Like the `core`, `alloc`, or
                // `std` crates, object files from `compiler_builtins` will be included in static
                // libraries built with the `staticlib` crate type.
                //
                // `compiler_builtins` contains routines that can't be replaced with few
                // instructions on the target hardware, such as float-to-int conversion on ARM32.
                // This crate also contains its own implementation of `memcpy` or `strlen` for
                // platforms where `libc` is unavailable, as calling `memcpy` is the current
                // implementation of Rust's move semantics.
                //
                // `compiler_builtins` is actually a port of LLVM's compiler runtime library,
                // `compiler-rt`. When Clang compiles C/C++ code, it tries to link
                // `libclang_rt.builtins*.a`, which is the pre-built runtime library that Clang
                // uses. GCC has its own compiler runtime library, `libgcc`, as well. These three
                // libraries share almost the same functions, such as `__fixunsdfdi` or
                // `__sync_fetch_and_add_<N>`.
                //
                // Clang allows using `libgcc` instead of `compiler-rt` using the `--rtlib=`
                // compiler flag. Using this compatibility, the NDK had completely migrated from
                // using `libgcc` to `libunwind` and `libclang_rt` in r23. Rust 1.67 and older
                // versions also had used `libgcc` on Android, but as Rust 1.68 started to target
                // NDK r25, it now uses `libunwind` and `compiler_builtins`.
                //
                // However, the Konan dependencies shipped with Kotlin/Native 2.1.10 still contains
                // `libgcc`, and Kotlin/Native depends on it. When Kotlin/Native 2.1.10 links a Rust
                // static library to a Kotlin executable, it tries to mix `libgcc` and
                // `compiler_builtins`. Starting from Rust 1.60, thanks to
                // rust-lang/compiler-builtins#452, each compiler routine in `compiler_builtins` is
                // stored in its own object file. However in `libgcc`, multiple routines are often
                // grouped into fewer object files. Because the linker is invoked with the following
                // argument order:
                //
                // ```
                // <Kotlin object file>.o <linkerKonanFlags> <Rust static library> -ldl -lgcc ...
                // ```
                //
                // some compiler routines required by Kotlin are resolved by `compiler_builtins`
                // before the linker processes `-lgcc`. When the linker tries to resolve other
                // routines in `libgcc.a`, since the object file containing them also has symbols
                // already resolved using `compiler_builtins`, the linker fails with
                // "multiple definition of symbols" errors.
                //
                // Therefore, we include `-lgcc` to `linkerKonanFlags` so the linker use `libgcc`
                // before it encounters object files from `compiler_builtins`, resolving the linker
                // issue. The symbols will be resolved using `libgcc.a`. Even if the linker
                // encounters object files in `compiler_builtins`, since the symbols of the same
                // name are already resolved, the linker will just ignore them, which is the exact
                // purpose of rust-lang/compiler-builtins#452.
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
            linkerOpts("-L${clangRuntimeDir.absolutePath}")
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
    //         entryPoint = "gobley.uniffi.examples.app.main"
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

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}