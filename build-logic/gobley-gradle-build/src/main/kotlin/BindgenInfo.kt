/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.build

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import net.peanuuutz.tomlkt.Toml
import java.io.File

@Serializable
data class CargoManifest(
    @SerialName("package") val `package`: Package,
    @SerialName("bin") val binaries: List<BinaryTarget> = listOf(),
) {
    val name get() = `package`.name
    val version get() = `package`.version
    val firstBinaryName get() = binaries.firstOrNull()?.name ?: name

    @Serializable
    data class Package(
        val name: String,
        val version: String,
    )

    @Serializable
    data class BinaryTarget(
        val name: String
    )

    companion object {
        private val toml = Toml {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        fun fromFile(file: File): CargoManifest {
            val manifestString = file.readText(Charsets.UTF_8)
            return toml.decodeFromString<CargoManifest>(manifestString)
        }
    }
}
