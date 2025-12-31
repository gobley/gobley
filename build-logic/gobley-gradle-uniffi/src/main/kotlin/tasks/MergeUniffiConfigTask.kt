/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.uniffi.tasks

import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.uniffi.Config
import gobley.gradle.uniffi.dsl.CustomType
import kotlinx.serialization.encodeToString
import net.peanuuutz.tomlkt.Toml
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

@CacheableTask
abstract class MergeUniffiConfigTask : DefaultTask() {
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val originalConfig: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val crateName: Property<String>

    @get:Input
    @get:Optional
    abstract val packageRoot: Property<String>

    @get:Input
    @get:Optional
    abstract val packageName: Property<String>

    @get:Input
    @get:Optional
    abstract val cdylibName: Property<String>

    @get:Input
    @get:Optional
    abstract val kotlinMultiplatform: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val kotlinTargets: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val generateImmutableRecords: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val omitChecksums: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val customTypes: MapProperty<String, CustomType>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val externalPackageConfigs: ListProperty<File>

    @get:Input
    @get:Optional
    abstract val kotlinVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val disableJavaCleaner: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val useKotlinXSerialization: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val usePascalCaseEnumClass: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val jvmDynamicLibraryDependencies: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val androidDynamicLibraryDependencies: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val dynamicLibraryDependencies: ListProperty<String>

    @get:Input
    @get:Optional
    // TODO: Remove InternalGobleyGradleApi in 0.4.0
    @InternalGobleyGradleApi
    abstract val enableJnaInterfaceMapping: Property<Boolean>

    @get:OutputFile
    abstract val outputConfig: RegularFileProperty

    @TaskAction
    fun mergeConfig() {
        val originalConfig = originalConfig.orNull?.asFile?.let(::Config) ?: Config()

        @OptIn(InternalGobleyGradleApi::class)
        val result = originalConfig.copy(
            // Properties read by the Gradle plugins
            crateName = crateName.orNull,
            packageRoot = packageRoot.orNull,
            // Properties read by the bindgen
            packageName = originalConfig.packageName ?: packageName.orNull,
            cdylibName = originalConfig.cdylibName ?: cdylibName.orNull,
            kotlinMultiplatform = originalConfig.kotlinMultiplatform ?: kotlinMultiplatform.orNull,
            kotlinTargets = mergeSet(
                originalConfig.kotlinTargets,
                kotlinTargets.orNull,
            ),
            generateImmutableRecords = originalConfig.generateImmutableRecords
                ?: generateImmutableRecords.orNull,
            omitChecksums = originalConfig.omitChecksums
                ?: omitChecksums.orNull,
            customTypes = mergeMap(
                originalConfig.customTypes,
                customTypes.map {
                    it.mapValues { entry ->
                        Config.CustomType(
                            imports = entry.value.imports.orNull,
                            typeName = entry.value.typeName.orNull,
                            lift = entry.value.lift.orNull,
                            lower = entry.value.lower.orNull,
                        )
                    }
                }.orNull,
            ),
            externalPackages = mergeMap(
                originalConfig.externalPackages,
                externalPackageConfigs.orNull?.let(::retrieveExternalPackageNames),
            ),
            kotlinTargetVersion = originalConfig.kotlinTargetVersion
                ?: kotlinVersion.orNull?.takeIf { it.isNotBlank() },
            disableJavaCleaner = originalConfig.disableJavaCleaner
                ?: disableJavaCleaner.orNull,
            generateSerializableTypes = originalConfig.generateSerializableTypes
                ?: useKotlinXSerialization.orNull,
            usePascalCaseEnumClass = originalConfig.usePascalCaseEnumClass
                ?: usePascalCaseEnumClass.orNull,
            jvmDynamicLibraryDependencies = mergeSet(
                originalConfig.jvmDynamicLibraryDependencies,
                jvmDynamicLibraryDependencies.orNull,
            ),
            androidDynamicLibraryDependencies = mergeSet(
                originalConfig.androidDynamicLibraryDependencies,
                androidDynamicLibraryDependencies.orNull,
            ),
            dynamicLibraryDependencies = mergeSet(
                originalConfig.dynamicLibraryDependencies,
                dynamicLibraryDependencies.orNull,
            ),
            enableJnaInterfaceMapping = originalConfig.enableJnaInterfaceMapping
                ?: enableJnaInterfaceMapping.orNull,
        )
        outputConfig.get().asFile.writeText(toml.encodeToString(result), Charsets.UTF_8)
    }

    private fun retrieveExternalPackageNames(
        externalPackageConfigs: List<File>
    ): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (configFile in externalPackageConfigs) {
            val config = Config(configFile)
            val crateName = config.crateName ?: continue
            if (!result.contains(crateName)) {
                if (config.packageName != null) {
                    result[crateName] = config.packageName
                }
            }
            if (config.externalPackages != null) {
                for ((externalCrateName, externalPackageName) in config.externalPackages) {
                    if (!result.contains(externalCrateName)) {
                        result[externalCrateName] = externalPackageName
                    }
                }
            }
        }
        return result.toMap()
    }

    private fun <T> mergeMap(
        original: Map<String, T>?,
        new: Map<String, T>?,
    ): Map<String, T>? {
        if (original == null) return new
        if (new == null) return original

        val newMap = original.toMutableMap()
        for ((newKey, newValue) in new) {
            if (!newMap.contains(newKey)) {
                newMap[newKey] = newValue
            }
        }

        return newMap.toMap()
    }

    private fun mergeSet(lhs: List<String>?, rhs: List<String>?): List<String>? {
        if (lhs == null) return rhs
        if (rhs == null) return lhs
        return lhs + rhs
    }

    companion object {
        private val toml = Toml {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}