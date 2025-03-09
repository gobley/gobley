/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.rust.targets

import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.rust.CrateType
import gobley.gradle.utils.SemVerConstraintMap
import io.github.z4kn4fein.semver.constraints.toConstraint
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import java.io.Serializable

@OptIn(InternalGobleyGradleApi::class)
enum class RustPosixTarget(
    override val rustTriple: String,
    override val jnaResourcePrefix: String,
    override val cinteropName: String,
    private val tierByVersion: SemVerConstraintMap<Int>,
) : RustJvmTarget, RustNativeTarget, Serializable {
    MinGWX64(
        rustTriple = "x86_64-pc-windows-gnu",
        jnaResourcePrefix = "win32-x86-64",
        cinteropName = "mingw",
        tierByVersion = SemVerConstraintMap(1),
    ),
    MacOSX64(
        rustTriple = "x86_64-apple-darwin",
        jnaResourcePrefix = "darwin-x86-64",
        cinteropName = "osx",
        tierByVersion = SemVerConstraintMap(1),
    ),
    MacOSArm64(
        rustTriple = "aarch64-apple-darwin",
        jnaResourcePrefix = "darwin-aarch64",
        cinteropName = "osx",
        tierByVersion = SemVerConstraintMap(
            default = 2,
            // https://blog.rust-lang.org/2024/10/17/Rust-1.82.0.html
            ">=1.82".toConstraint() to 1,
        ),
    ),
    LinuxX64(
        rustTriple = "x86_64-unknown-linux-gnu",
        jnaResourcePrefix = "linux-x86-64",
        cinteropName = "linux",
        tierByVersion = SemVerConstraintMap(1),
    ),
    LinuxArm64(
        rustTriple = "aarch64-unknown-linux-gnu",
        jnaResourcePrefix = "linux-aarch64",
        cinteropName = "linux",
        tierByVersion = SemVerConstraintMap(
            default = 2,
            // https://blog.rust-lang.org/2020/12/31/Rust-1.49.0.html
            ">=1.49".toConstraint() to 1,
        ),
    );

    override val friendlyName = name

    override val supportedKotlinPlatformTypes = arrayOf(KotlinPlatformType.jvm, KotlinPlatformType.native)

    override fun tier(rustVersion: String): Int {
        return tierByVersion[rustVersion]
    }

    override fun outputFileName(crateName: String, crateType: CrateType): String? {
        return when {
            isWindows() -> crateType.outputFileNameForMinGW(crateName)
            isMacOS() -> crateType.outputFileNameForMacOS(crateName)
            isLinux() -> crateType.outputFileNameForLinux(crateName)
            else -> null
        }
    }

    override fun toString() = rustTriple

    fun isWindows() = windowsTargets.contains(this)

    fun isMacOS() = macOSTargets.contains(this)

    fun isLinux() = linuxTargets.contains(this)

    companion object {
        val windowsTargets = arrayOf(MinGWX64)
        val macOSTargets = arrayOf(MacOSX64, MacOSArm64)
        val linuxTargets = arrayOf(LinuxX64, LinuxArm64)
    }
}
