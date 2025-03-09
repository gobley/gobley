/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.rust.targets

import gobley.gradle.rust.CrateType
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import java.io.Serializable

enum class RustWindowsTarget(
    override val rustTriple: String,
    override val jnaResourcePrefix: String,
    private val tier: Int,
) : RustJvmTarget, Serializable {
    X64(
        rustTriple = "x86_64-pc-windows-msvc",
        jnaResourcePrefix = "win32-x86-64",
        tier = 1,
    ),
    Arm64(
        rustTriple = "aarch64-pc-windows-msvc",
        jnaResourcePrefix = "win32-aarch64",
        tier = 2,
    );

    override val friendlyName: String = "Windows$name"

    override val supportedKotlinPlatformTypes = arrayOf(KotlinPlatformType.jvm)

    override fun tier(rustVersion: String): Int {
        return tier
    }

    override fun outputFileName(crateName: String, crateType: CrateType): String? =
        crateType.outputFileNameForMsvc(crateName)

    override fun toString() = rustTriple
}
