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
    // TODO: Add watchOS and tvOS targets
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
    );

    override val friendlyName = name

    override val supportedKotlinPlatformTypes = arrayOf(KotlinPlatformType.native)

    override fun tier(rustVersion: String?): Int {
        return tier
    }

    override fun outputFileName(crateName: String, crateType: CrateType): String? =
        crateType.outputFileNameForMacOS(crateName)

    override fun toString() = rustTriple
}
