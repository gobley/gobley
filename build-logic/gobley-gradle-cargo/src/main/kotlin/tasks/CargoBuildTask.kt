/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.cargo.tasks

import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.cargo.profiles.CargoProfile
import gobley.gradle.rust.CrateType
import gobley.gradle.rust.targets.RustNativeTarget
import gobley.gradle.rust.targets.RustTarget
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction

@Suppress("LeakingThis")
@CacheableTask
abstract class CargoBuildTask : CargoPackageTask() {
    @get:Input
    abstract val profile: Property<CargoProfile>

    @get:Input
    abstract val target: Property<RustTarget>

    @get:Input
    abstract val features: SetProperty<String>

    @get:Input
    abstract val extraArguments: ListProperty<String>

    @OutputFiles
    val libraryFileByCrateType: Provider<Map<CrateType, RegularFile>> =
        profile.zip(target, ::Pair).zip(cargoPackage) { (profile, target), cargoPackage ->
            cargoPackage.libraryCrateTypes.mapNotNull { crateType ->
                crateType to cargoPackage.outputDirectory(profile, target).file(
                    target.outputFileName(cargoPackage.libraryCrateName, crateType) ?: return@mapNotNull null
                )
            }.toMap()
        }

    @get:OutputFile
    @get:Optional
    abstract val nativeStaticLibsDefFile: RegularFileProperty

    @TaskAction
    @OptIn(InternalGobleyGradleApi::class)
    fun build() {
        val profile = profile.get()
        val target = target.get()
        val result = cargo("rustc") {
            arguments("--profile", profile.profileName)
            arguments("--target", target.rustTriple)
            if (features.isPresent) {
                if (features.get().isNotEmpty()) {
                    arguments("--features", features.get().joinToString(","))
                }
            }
            arguments("--lib")
            for (extraArgument in extraArguments.get()) {
                arguments(extraArgument)
            }
            arguments("--")
            if (nativeStaticLibsDefFile.isPresent) {
                arguments("--print", "native-static-libs")
            }
            suppressXcodeIosToolchains()
            if (nativeStaticLibsDefFile.isPresent) {
                captureStandardError()
            }
        }.get().apply {
            assertNormalExitValue()
        }

        if (nativeStaticLibsDefFile.isPresent) {
            nativeStaticLibsDefFile.get().asFile.run {
                parentFile?.mkdirs()

                val linkerOptName = if (target is RustNativeTarget) {
                    "linkerOpts.${target.cinteropName}"
                } else {
                    "linkerOpts"
                }
                val linkerFlag = result.standardError!!.split('\n')
                    .map { it.trim().substringAfter("note: native-static-libs: ", "") }.firstOrNull(String::isNotEmpty)

                writeText(StringBuilder().apply {
                    if (linkerFlag != null) {
                        append("$linkerOptName = $linkerFlag\n")
                    }
                    val libraryFile = libraryFileByCrateType.get()[CrateType.SystemStaticLibrary]
                    if (libraryFile != null) {
                        append("staticLibraries = ${libraryFile.asFile.name}")
                    }
                }.toString())
            }
        }
    }
}
