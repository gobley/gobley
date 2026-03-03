/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.uniffi.tasks

import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.cargo.tasks.CargoPackageTask
import gobley.gradle.uniffi.Config
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.lambdas.SerializableLambdas.spec
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

@CacheableTask
abstract class BuildUniffiBindingsTask : CargoPackageTask() {

    /**
     * The raw directory where uniffi-bindgen places all outputs before they are split.
     * Hidden from Gradle's output tracking to prevent caching conflicts.
     */
    @get:Internal
    abstract val rawOutputDirectory: DirectoryProperty

    /**
     * The explicitly separated directory for generated Kotlin (.kt) files.
     * This strictly satisfies the AGP 9 Variant API requirements.
     */
    @get:OutputDirectory
    abstract val kotlinOutputDir: DirectoryProperty

    /**
     * The explicitly separated directory for generated C-Interop (.h, .def) files.
     */
    @get:OutputDirectory
    abstract val cinteropOutputDir: DirectoryProperty

    /**
     * Gradle's native file system router for caching-safe file copying.
     */
    @get:Inject
    abstract val fsOperations: FileSystemOperations

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val bindgen: RegularFileProperty

    /**
     * Path to the optional uniffi config file.
     * If not provided, uniffi-bindgen will try to guess it from the UDL's file location.
     */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val config: RegularFileProperty

    /**
     * Paths to the optional uniffi config files.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val externalPackageConfigs: ListProperty<File>

    /**
     * Extract proc-macro metadata from a native lib (cdylib or staticlib) for this crate.
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val libraryFile: RegularFileProperty

    /**
     * Pass in a cdylib path rather than a UDL file
     */
    @get:Input
    @get:Optional
    abstract val libraryMode: Property<Boolean>

    /**
     * The library name, as defined in Cargo.toml.
     */
    @Suppress("LeakingThis")
    @get:Input
    val libraryCrateName: Provider<String> = cargoPackage.map { it.libraryCrateName }

    /**
     * When `--library` is passed, only generate bindings for one crate.
     * When `--library` is not passed, use this as the crate name instead of attempting to
     * locate and parse Cargo.toml.
     */
    @Suppress("LeakingThis")
    @get:Input
    @get:Optional
    val crateName: Provider<String> = cargoPackage.map { it.libraryCrateName }

    /**
     * Path to the UDL file, or cdylib if `library-mode` is specified
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val source: RegularFileProperty

    /**
     * Tries to run `ktlint` on the generated bindings
     */
    @get:Input
    @get:Optional
    abstract val formatCode: Property<Boolean>

    @TaskAction
    fun buildBindings() {
        @OptIn(InternalGobleyGradleApi::class)
        command(bindgen) {
            workingDirectory(root)

            // Route the raw CLI output directly to our internal directory
            if (rawOutputDirectory.isPresent) {
                arguments("--out-dir", rawOutputDirectory.get())
            }

            if (config.isPresent) {
                val configFile = config.get()
                arguments("--config", configFile)
                val config = Config(configFile.asFile)
                val crateName = config.crateName
                if (crateName != null) {
                    arguments("--crate-configs", "$crateName=$configFile")
                    val packageRoot = config.packageRoot
                    if (packageRoot != null) {
                        arguments("--crate-paths", "$crateName=$packageRoot")
                    }
                }
            }
            if (externalPackageConfigs.isPresent) {
                for (packageConfigFile in externalPackageConfigs.get()) {
                    val config = Config(packageConfigFile)
                    val crateName = config.crateName ?: continue
                    arguments("--crate-configs", "$crateName=$packageConfigFile")
                    val packageRoot = config.packageRoot ?: continue
                    arguments("--crate-paths", "$crateName=$packageRoot")
                }
            }
            if (libraryFile.isPresent) {
                arguments("--lib-file", libraryFile.get())
            }
            if (libraryMode.get()) {
                arguments("--library")
            }
            if (crateName.isPresent) {
                arguments("--crate", crateName.get())
            }
            if (formatCode.isPresent && formatCode.get()) {
                arguments("--format")
            }
            arguments(source.get())
            suppressXcodeIosToolchains()
        }.get().assertNormalExitValue()

        // Generate the def file into the raw output directory alongside the bindgen files
        val defFilePath =
            rawOutputDirectory.get().file("nativeInterop/cinterop/${libraryCrateName.get()}.def")
        val defFileFile = defFilePath.asFile
        defFileFile.parentFile?.mkdirs()
        defFileFile.writeText("staticLibraries = lib${libraryCrateName.get()}.a\n")

        // --- SPLIT OUTPUTS FOR AGP 9 COMPLIANCE ---

        // 1. Sync strictly the Kotlin files to the Variant API output directory
        fsOperations.sync {
            from(rawOutputDirectory) {
                include("**/*.kt")
            }
            into(kotlinOutputDir)
        }

        // 2. Sync strictly the C-Interop files to the native output directory
        fsOperations.sync {
            from(rawOutputDirectory) {
                include("**/*.h", "**/*.def")
            }
            into(cinteropOutputDir)
        }
    }
}