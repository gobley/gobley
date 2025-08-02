/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.rust

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Represents a Rust crate type.
 */
@Serializable(with = CrateTypeSerializer::class)
enum class CrateType(private val actualName: String) {
    Executable("bin"),
    Library("lib"),
    StaticLibrary("rlib"),
    DynamicLibrary("dylib"),
    SystemStaticLibrary("staticlib"),
    SystemDynamicLibrary("cdylib"),
    ProceduralMacro("proc-macro");

    fun outputFileNameForMsvc(crateName: String): String? {
        return when (this) {
            Executable -> "${crateName}.exe"
            StaticLibrary -> "lib${crateName}.rlib"
            DynamicLibrary -> "${crateName}.dll"
            SystemStaticLibrary -> "${crateName}.lib"
            SystemDynamicLibrary -> "${crateName}.dll"
            else -> null
        }
    }

    fun outputFileNameForMinGW(crateName: String): String? {
        return when (this) {
            Executable -> "${crateName}.exe"
            StaticLibrary -> "lib${crateName}.rlib"
            DynamicLibrary -> "${crateName}.dll"
            // This part is different from outputFileNameForMsvc
            SystemStaticLibrary -> "lib${crateName}.a"
            SystemDynamicLibrary -> "${crateName}.dll"
            else -> null
        }
    }

    private fun outputFileNameForPosix(crateName: String, dylibExtension: String): String? {
        return when (this) {
            Executable -> crateName
            StaticLibrary -> "lib${crateName}.rlib"
            DynamicLibrary -> "lib${crateName}.${dylibExtension}"
            SystemStaticLibrary -> "lib${crateName}.a"
            SystemDynamicLibrary -> "lib${crateName}.${dylibExtension}"
            else -> null
        }
    }

    fun outputFileNameForMacOS(crateName: String): String? =
        outputFileNameForPosix(crateName, "dylib")

    fun outputFileNameForLinux(crateName: String): String? =
        outputFileNameForPosix(crateName, "so")

    fun outputFileNameForWasm(crateName: String): String? {
        return when (this) {
            Executable -> "${crateName}.wasm"
            StaticLibrary -> "lib${crateName}.rlib"
            // Not supported by wasm32-unknown-unknown
            DynamicLibrary -> null
            SystemStaticLibrary -> "lib${crateName}.a"
            SystemDynamicLibrary -> "${crateName}.wasm"
            else -> null
        }
    }

    override fun toString(): String = actualName
}

object CrateTypeSerializer : KSerializer<CrateType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("CrateType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CrateType) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): CrateType {
        val actualName = decoder.decodeString()
        return CrateType.entries.first { it.toString() == actualName }
    }
}
