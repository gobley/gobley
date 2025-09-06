import com.android.build.gradle.internal.tasks.factory.dependsOn
import gobley.gradle.GobleyHost
import gobley.gradle.cargo.dsl.android
import gobley.gradle.rust.targets.RustAndroidTarget
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("android")
    id("dev.gobley.cargo")
    alias(libs.plugins.android.library)
}

// Build a library manually to test passing an absolute path to `dynamicLibraries` works well
val anotherCustomCppLibraryRoot: Directory =
    project.layout.projectDirectory.dir("another-android-linking-cpp")
val androidTargets = RustAndroidTarget.values()
val anotherCustomCppLibraryCmakeOutputDirectories = androidTargets.associateWith {
    project.layout.buildDirectory.dir("intermediates/ninja/project/debug/${it.androidAbiName}")
        .get()
}
val anotherCustomCppLibraryLocations = anotherCustomCppLibraryCmakeOutputDirectories.mapValues {
    it.value.file("libanother-android-linking-cpp.so")
}
val androidSdkCMakeDirectory = android.sdkDirectory
    .resolve("cmake")
    .listFiles()
    ?.firstOrNull { file -> file.name.startsWith("3.") }
    ?.resolve("bin") ?: error("CMake is not installed in Android SDK")
val androidSdkCMake =
    androidSdkCMakeDirectory.resolve(GobleyHost.Platform.current.convertExeName("cmake"))
val androidSdkNinja =
    androidSdkCMakeDirectory.resolve(GobleyHost.Platform.current.convertExeName("ninja"))
val anotherCustomCppLibraryBuildTasks = androidTargets.associateWith {
    val cmakeOutputDirectory = anotherCustomCppLibraryCmakeOutputDirectories[it]!!
    val libraryLocation = anotherCustomCppLibraryLocations[it]!!
    val configureTask = tasks.register<Exec>("configureCustomCppLibraryCMake${it.friendlyName}") {
        commandLine(
            androidSdkCMake,
            "-H$anotherCustomCppLibraryRoot",
            "-B$cmakeOutputDirectory",
            "-DANDROID_ABI=${it.androidAbiName}",
            "-DANDROID_PLATFORM=29",
            "-DANDROID_NDK=${android.ndkDirectory}",
            "-DCMAKE_TOOLCHAIN_FILE=${android.ndkDirectory}/build/cmake/android.toolchain.cmake",
            "-DCMAKE_MAKE_PROGRAM=$androidSdkNinja",
            "-G",
            "Ninja",
        )

        inputs.dir(anotherCustomCppLibraryRoot)
        outputs.dir(cmakeOutputDirectory)
    }

    tasks.register<Exec>("buildCustomCppLibrary${it.friendlyName}") {
        commandLine(
            androidSdkCMake,
            "--build",
            "$cmakeOutputDirectory"
        )
        dependsOn(configureTask)

        inputs.dir(cmakeOutputDirectory)
        outputs.file(libraryLocation)
    }
}

cargo {
    builds.android {
        val anotherCustomCppLibraryBuildTask = anotherCustomCppLibraryBuildTasks[rustTarget]!!
        val libraryLocation = anotherCustomCppLibraryLocations[rustTarget]!!
        dynamicLibraries.addAll("c++_shared", libraryLocation.asFile.absolutePath)
        variants {
            findDynamicLibrariesTaskProvider.dependsOn(anotherCustomCppLibraryBuildTask)
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
    sourceSets {
        androidTest {
            dependencies {
                implementation(libs.junit)
                implementation(libs.androidx.test.core)
                implementation(libs.androidx.test.runner)
            }
        }
    }
}

android {
    namespace = "dev.gobley.uniffi.tests.gradle.androidlinking"
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

    externalNativeBuild {
        cmake {
            path = File("CMakeLists.txt")
        }
    }
}
