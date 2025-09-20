/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.rust.targets

import gobley.gradle.GobleyHost
import gobley.gradle.rust.CrateType
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import java.io.File
import java.io.Serializable

/**
 * Represents a Rust Android target for a single ABI supported by NDK.
 */
enum class RustAndroidTarget(
    override val rustTriple: String,
    // `RustAndroidTarget` should not implement `RustJvmTarget` because Android .so files are handled and copied
    // differently than the dynamic libraries of normal desktop JVM targets.
    //
    // override val jnaResourcePrefix: String,
    /**
     * The name of the ABI, which is used in APK files to distinguish libraries of different CPU architectures.
     */
    val androidAbiName: String,
    /**
     * The LLVM triple prefix of the ABI, which is used by the LLVM toolchain in NDK.
     */
    val ndkLlvmTriple: String = rustTriple,
    /**
     * The LLVM triple of the ABI, which is used in the NDK library directory.
     */
    val ndkLibraryTriple: String = rustTriple,
) : RustMobileTarget, /* RustJvmTarget, */ RustNativeTarget, Serializable {
    Arm64(
        rustTriple = "aarch64-linux-android",
        // jnaResourcePrefix = "android-aarch64",
        androidAbiName = "arm64-v8a",
    ),
    ArmV7(
        rustTriple = "armv7-linux-androideabi",
        // jnaResourcePrefix = "android-arm",
        androidAbiName = "armeabi-v7a",
        ndkLlvmTriple = "armv7a-linux-androideabi",
        ndkLibraryTriple = "arm-linux-androideabi",
    ),
    X64(
        rustTriple = "x86_64-linux-android",
        // jnaResourcePrefix = "android-x86-64",
        androidAbiName = "x86_64",
    ),
    X86(
        rustTriple = "i686-linux-android",
        // jnaResourcePrefix = "android-x86",
        androidAbiName = "x86",
    );

    override val friendlyName = "Android$name"

    override val supportedKotlinPlatformTypes = arrayOf(
        KotlinPlatformType.androidJvm,
        KotlinPlatformType.native,
    )

    override val cinteropName = "android"

    override fun tier(rustVersion: String) = 2

    override fun outputFileName(crateName: String, crateType: CrateType): String? =
        crateType.outputFileNameForLinux(crateName)

    override fun toString() = rustTriple

    fun ndkEnvVariables(
        sdkRoot: File,
        apiLevel: Int = 21,
        ndkVersion: String? = null,
        ndkRoot: File? = null,
    ): Map<String, Any> {
        val actualNdkRoot = tryRetrieveNdkRoot(sdkRoot, ndkVersion, ndkRoot)!!
        val toolchainDir = ndkToolchainDir(sdkRoot, ndkVersion, ndkRoot)!!
        val toolchainBinaryDir = toolchainDir.resolve("bin")
        val currentPlatform = GobleyHost.current.platform
        val clang =
            currentPlatform.chooseExeExtension(toolchainBinaryDir.resolve("${ndkLlvmTriple}$apiLevel-clang"))
        val clangCpp =
            currentPlatform.chooseExeExtension(toolchainBinaryDir.resolve("${ndkLlvmTriple}$apiLevel-clang++"))
        val ar = currentPlatform.chooseExeExtension(toolchainBinaryDir.resolve("llvm-ar"))
        val ranlib = currentPlatform.chooseExeExtension(toolchainBinaryDir.resolve("llvm-ranlib"))
        val sysroot = toolchainDir.resolve("sysroot")
        val sysrootExtraInclude = sysroot.resolve("usr/include/$ndkLibraryTriple")
        val underscoreRustTriple = rustTriple.replace('-', '_').uppercase()
        return mapOf(
            "ANDROID_HOME" to sdkRoot,
            "ANDROID_NDK_HOME" to actualNdkRoot,
            "ANDROID_NDK_ROOT" to actualNdkRoot,
            "CARGO_TARGET_${underscoreRustTriple}_LINKER" to clang,
            "CC_$rustTriple" to clang,
            "CLANG_PATH" to clang,
            "CXX_$rustTriple" to clangCpp,
            "AR_$rustTriple" to ar,
            "RANLIB_$rustTriple" to ranlib,
            "BINDGEN_EXTRA_CLANG_ARGS_$rustTriple" to "--sysroot=$sysroot -I$sysrootExtraInclude",
        )
    }

    fun ndkLibraryDirectories(
        sdkRoot: File,
        apiLevel: Int = 21,
        ndkVersion: String? = null,
        ndkRoot: File? = null,
    ): List<File> {
        val toolchainLibraryDir = ndkToolchainDir(sdkRoot, ndkVersion, ndkRoot)!!
            .resolve("sysroot/usr/lib")
            .resolve(ndkLibraryTriple)
        return listOf(
            toolchainLibraryDir,
            toolchainLibraryDir.resolve(apiLevel.toString())
        )
    }

    companion object {
        private fun ndkRootFromSdkRoot(sdkRoot: File, ndkVersion: String? = null): File? {
            return sdkRoot.resolve("ndk").run {
                ndkVersion?.let(::resolve)?.takeIf(File::exists) ?: selectLatestVersion()
            }
        }

        private fun ndkRootFromAndroidNdkRoot(): File? =
            System.getenv("ANDROID_NDK_ROOT")?.let(::File)

        private fun tryRetrieveNdkRoot(
            sdkRoot: File,
            ndkVersion: String? = null,
            ndkRoot: File? = null,
        ): File? = ndkRoot ?: ndkRootFromSdkRoot(sdkRoot, ndkVersion) ?: ndkRootFromAndroidNdkRoot()

        private val GobleyHost.Platform.ndkHostTag: String
            get() = when (this) {
                GobleyHost.Platform.Windows -> "windows-x86_64"
                GobleyHost.Platform.MacOS -> "darwin-x86_64"
                GobleyHost.Platform.Linux -> "linux-x86_64"
            }

        private fun ndkToolchainDir(
            sdkRoot: File,
            ndkVersion: String? = null,
            ndkRoot: File? = null,
        ) = tryRetrieveNdkRoot(sdkRoot, ndkVersion, ndkRoot)?.run {
            resolve("toolchains/llvm/prebuilt").resolve(GobleyHost.current.platform.ndkHostTag)
        }
    }
}

fun RustAndroidTarget(androidAbiName: String): RustAndroidTarget =
    RustAndroidTarget.entries.first { it.androidAbiName == androidAbiName }

private fun File.selectLatestVersion(): File? = listFiles()?.maxWithOrNull { file0, file1 ->
    val version0 = file0.extractVersion()
    val version1 = file1.extractVersion()
    version0.compareTo(version1)
}

private fun File.extractVersion(): List<Int> = name.split('.').mapNotNull { it.toIntOrNull() }

private fun <T : Comparable<T>> Iterable<T>.compareTo(other: Iterable<T>): Int {
    val thisIterator = iterator()
    val otherIterator = other.iterator()

    while (thisIterator.hasNext() && otherIterator.hasNext()) {
        val thisElem = thisIterator.next()
        val otherElem = otherIterator.next()

        val comparison = thisElem.compareTo(otherElem)
        if (comparison != 0) {
            return comparison
        }
    }

    return when {
        thisIterator.hasNext() -> 1
        otherIterator.hasNext() -> -1
        else -> 0
    }
}
