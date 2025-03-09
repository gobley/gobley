/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.rust.targets

import gobley.gradle.rust.CrateType
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import java.io.Serializable

/**
 * Represents a Rust Apple mobile target.
 */
enum class RustAppleMobileTarget(
    override val rustTriple: String,
    override val cinteropName: String,
    private val tier: Int,
) : RustMobileTarget, RustNativeTarget, Serializable {
    // TODO: Add visionOS targets when Kotlin/Native supports
    IosArm64(
        rustTriple = "aarch64-apple-ios",
        cinteropName = "ios",
        tier = 2,
    ),
    IosSimulatorArm64(
        rustTriple = "aarch64-apple-ios-sim",
        cinteropName = "ios",
        tier = 2,
    ),
    IosX64(
        rustTriple = "x86_64-apple-ios",
        cinteropName = "ios",
        tier = 2,
    ),
    TvOsArm64(
        rustTriple = "aarch64-apple-tvos",
        cinteropName = "tvos",
        tier = 3,
    ),
    TvOsSimulatorArm64(
        rustTriple = "aarch64-apple-tvos-sim",
        cinteropName = "tvos",
        tier = 3,
    ),
    TvOsX64(
        rustTriple = "x86_64-apple-tvos",
        cinteropName = "tvos",
        tier = 3,
    ),
    WatchOsDeviceArm64(
        rustTriple = "aarch64-apple-watchos",
        cinteropName = "watchos",
        tier = 3,
    ),
    WatchOsSimulatorArm64(
        rustTriple = "aarch64-apple-watchos-sim",
        cinteropName = "watchos",
        tier = 3,
    ),
    WatchOsX64(
        rustTriple = "x86_64-apple-watchos-sim",
        cinteropName = "watchos",
        tier = 3,
    ),
    WatchOsArm64(
        rustTriple = "arm64_32-apple-watchos",
        cinteropName = "watchos",
        tier = 3,
    ),
    WatchOsArm32(
        rustTriple = "armv7k-apple-watchos",
        cinteropName = "watchos",
        tier = 3,
    );

    override val friendlyName = name

    override val supportedKotlinPlatformTypes = arrayOf(KotlinPlatformType.native)

    override fun tier(rustVersion: String): Int {
        return tier
    }

    override fun outputFileName(crateName: String, crateType: CrateType): String? =
        crateType.outputFileNameForMacOS(crateName)

    override fun toString() = rustTriple
}
