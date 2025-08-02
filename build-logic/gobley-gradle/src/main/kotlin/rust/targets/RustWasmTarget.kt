/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.rust.targets

import gobley.gradle.rust.CrateType
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import java.io.Serializable

enum class RustWasmTarget(
    override val rustTriple: String,
) : RustTarget, Serializable {
    Unknown("wasm32-unknown-unknown");

    override val friendlyName = "Wasm$name"

    override val supportedKotlinPlatformTypes =
        arrayOf(KotlinPlatformType.js, KotlinPlatformType.wasm)

    override fun tier(rustVersion: String) = 2

    override fun outputFileName(crateName: String, crateType: CrateType): String? =
        crateType.outputFileNameForWasm(crateName)
}