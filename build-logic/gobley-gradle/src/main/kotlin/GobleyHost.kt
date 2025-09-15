/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle

import gobley.gradle.rust.targets.RustAndroidTarget
import gobley.gradle.rust.targets.RustAppleMobileTarget
import gobley.gradle.rust.targets.RustPosixTarget
import gobley.gradle.rust.targets.RustTarget
import gobley.gradle.rust.targets.RustWasmTarget
import gobley.gradle.rust.targets.RustWindowsTarget
import org.gradle.nativeplatform.platform.internal.Architectures
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import java.io.File
import java.io.Serializable

data class GobleyHost(val platform: Platform, val arch: Arch) : Serializable {
    enum class Platform {
        Windows, MacOS, Linux;

        fun convertExeName(name: String, extension: String = "exe"): String = when (this) {
            Windows -> "$name.$extension"
            else -> name
        }

        fun chooseExeExtension(file: File): File {
            if (this != Windows) return file
            for (extension in arrayOf("bat", "cmd")) {
                val convertedFile = File(convertExeName(file.path, extension))
                if (convertedFile.exists()) return convertedFile
            }
            return File(convertExeName(file.path))
        }

        val isCurrent: Boolean
            get() = when (this) {
                Windows -> defaultOperatingSystem.isWindows
                MacOS -> defaultOperatingSystem.isMacOsX
                Linux -> defaultOperatingSystem.isLinux
            }

        val pathSeparator: String
            get() = when (this) {
                Windows -> ";"
                else -> ":"
            }

        val homeDirectory: File
            get() = File(
                System.getenv(
                    when (this) {
                        Windows -> "USERPROFILE"
                        else -> "HOME"
                    }
                )!!
            )

        val defaultToolchainDirectory: File
            get() = homeDirectory.resolve(".cargo/bin")

        val supportedTargets: Array<RustTarget>
            get() = when (this) {
                Windows -> windowsSupportedTargets
                MacOS -> macOsSupportedTargets
                Linux -> linuxSupportedTargets
            }

        val konanName: String
            get() = when (this) {
                Windows -> "mingw"
                MacOS -> "macos"
                Linux -> "linux"
            }

        companion object {
            private val defaultOperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()
            val current: Platform = entries.firstOrNull { it.isCurrent }
                ?: throw IllegalStateException("Unsupported os: ${defaultOperatingSystem.displayName}")

            private val windowsSupportedTargets: Array<RustTarget> = arrayOf(
                RustAndroidTarget.entries,
                RustPosixTarget.windowsTargets.asList(),
                RustPosixTarget.linuxTargets.asList(),
                RustWasmTarget.entries,
                RustWindowsTarget.entries,
            ).flatMap { it }.toTypedArray()

            private val macOsSupportedTargets: Array<RustTarget> = arrayOf(
                RustAndroidTarget.entries,
                RustAppleMobileTarget.entries,
                RustPosixTarget.entries,
                RustWasmTarget.entries,
            ).flatMap { it }.toTypedArray()

            private val linuxSupportedTargets: Array<RustTarget> = arrayOf(
                RustAndroidTarget.entries,
                RustPosixTarget.windowsTargets.asList(),
                RustPosixTarget.linuxTargets.asList(),
                RustWasmTarget.entries,
            ).flatMap { it }.toTypedArray()
        }
    }

    enum class Arch {
        X64, Arm64;

        val isCurrent: Boolean
            get() = when (this) {
                X64 -> defaultArchitectureIsAmd64
                Arm64 -> defaultArchitectureIsArm64
            }

        val konanName: String
            get() = when (this) {
                X64 -> "x64"
                Arm64 -> "arm64"
            }

        companion object {
            private val defaultArchitecture = System.getProperty("os.arch")
            private val defaultArchitectureIsAmd64 =
                Architectures.X86_64.isAlias(defaultArchitecture)
            private val defaultArchitectureIsArm64 =
                Architectures.AARCH64.isAlias(defaultArchitecture)

            val current: Arch = entries.firstOrNull { it.isCurrent }
                ?: throw IllegalStateException("Unsupported os: $defaultArchitecture")
        }
    }

    val rustTarget: RustTarget
        get() = when (platform) {
            Platform.Windows -> when (arch) {
                Arch.X64 -> RustWindowsTarget.X64
                Arch.Arm64 -> RustWindowsTarget.Arm64
            }

            Platform.MacOS -> when (arch) {
                Arch.X64 -> RustPosixTarget.MacOSX64
                Arch.Arm64 -> RustPosixTarget.MacOSArm64
            }

            Platform.Linux -> when (arch) {
                Arch.X64 -> RustPosixTarget.LinuxX64
                Arch.Arm64 -> RustPosixTarget.LinuxArm64
            }
        }

    /**
     * Returns the default install locations of famous package managers in the platform.
     */
    val packageManagerInstallDirectories: List<String>
        get() = when (platform) {
            Platform.Windows -> listOf(
                /* WinGet */ "${System.getenv("LOCALAPPDATA")}\\Microsoft\\WinGet\\Links",
                /* Chocolatey */ "${System.getenv("ProgramData")}\\chocolatey\\bin",
                /* Standalone CMake */ "C:\\Program Files\\CMake\\bin"
            )

            Platform.MacOS -> listOf(
                /* Homebrew */
                when (arch) {
                    Arch.X64 -> "/usr/local/bin"
                    Arch.Arm64 -> "/opt/homebrew/bin"
                },
                /* Standalone CMake */ "/Applications/CMake.app/Contents/bin",
            )

            Platform.Linux -> listOf(
                /* Homebrew */ "/home/linuxbrew/.linuxbrew/bin",
            )
        }

    val konanName: String
        get() = "${platform.konanName}_${arch.konanName}"

    companion object {
        val current: GobleyHost = GobleyHost(Platform.current, Arch.current)
    }
}
