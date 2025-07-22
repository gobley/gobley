/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.uniffi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import net.peanuuutz.tomlkt.Toml
import java.io.File

/**
 * Configuration provided to the bindgen. Must be synced with Config in
 * `src/gen_kotlin_multiplatform/mod.rs`, but keep all properties nullable, so only properties
 * explicitly set by users are generated when merged by the plugin.
 *
 * Properties start with `__gradle_` should be set and read by the Gradle plugins only.
 */
@Serializable
internal data class Config(
    @SerialName("__gradle_crate_name") val crateName: String? = null,
    @SerialName("__gradle_package_root") val packageRoot: String? = null,
    @SerialName("package_name") val packageName: String? = null,
    @SerialName("cdylib_name") val cdylibName: String? = null,
    @SerialName("kotlin_multiplatform") val kotlinMultiplatform: Boolean? = null,
    @SerialName("kotlin_targets") val kotlinTargets: List<String>? = null,
    @SerialName("generate_immutable_records") val generateImmutableRecords: Boolean? = null,
    @SerialName("omit_checksums") val omitChecksums: Boolean? = null,
    @SerialName("custom_types") val customTypes: Map<String, CustomType>? = null,
    @SerialName("external_packages") val externalPackages: Map<String, String>? = null,
    @SerialName("kotlin_target_version") val kotlinTargetVersion: String? = null,
    @SerialName("disable_java_cleaner") val disableJavaCleaner: Boolean? = null,
    @SerialName("generate_serializable_types") val generateSerializableTypes: Boolean? = null,
    @SerialName("use_pascal_case_enum_class") val usePascalCaseEnumClass: Boolean? = null,
    @SerialName("jvm_dynamic_library_dependencies") val jvmDynamicLibraryDependencies: List<String>? = null,
    @SerialName("android_dynamic_library_dependencies") val androidDynamicLibraryDependencies: List<String>? = null,
    @SerialName("dynamic_library_dependencies") val dynamicLibraryDependencies: List<String>? = null,
) {
    @Serializable
    internal data class CustomType(
        @SerialName("imports") val imports: List<String>? = null,
        @SerialName("type_name") val typeName: String? = null,
        @SerialName("lift") val lift: String? = null,
        @SerialName("lower") val lower: String? = null,
    )

    companion object {
        val toml = Toml {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}

internal fun Config(file: File): Config {
    return Config.toml.decodeFromString<Config>(file.readText(Charsets.UTF_8))
}