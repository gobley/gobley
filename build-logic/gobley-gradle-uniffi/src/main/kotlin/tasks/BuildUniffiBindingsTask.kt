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

    @get:Internal
    abstract val rawOutputDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val commonMainOutputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val mainOutputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val jvmMainOutputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val androidMainOutputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val nativeMainOutputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val stubMainOutputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val cinteropOutputDir: DirectoryProperty

    @get:Input
    abstract val multiplatformMode: Property<Boolean>

    @get:Inject
    abstract val fsOperations: FileSystemOperations

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val bindgen: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val config: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val externalPackageConfigs: ListProperty<File>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val libraryFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val libraryMode: Property<Boolean>

    @Suppress("LeakingThis")
    @get:Input
    val libraryCrateName: Provider<String> = cargoPackage.map { it.libraryCrateName }

    @Suppress("LeakingThis")
    @get:Input
    @get:Optional
    val crateName: Provider<String> = cargoPackage.map { it.libraryCrateName }

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val source: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val formatCode: Property<Boolean>

    @TaskAction
    fun buildBindings() {
        @OptIn(InternalGobleyGradleApi::class)
        command(bindgen) {
            workingDirectory(root)
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

        val defFilePath = rawOutputDirectory.get().file("nativeInterop/cinterop/${libraryCrateName.get()}.def")
        val defFileFile = defFilePath.asFile
        defFileFile.parentFile?.mkdirs()
        defFileFile.writeText("staticLibraries = lib${libraryCrateName.get()}.a\n")

        // --- SPLIT OUTPUTS FOR AGP 9 COMPLIANCE & PACKAGE RESOLUTION ---

        if (multiplatformMode.get()) {
            val sourceSets = listOf(
                "commonMain" to commonMainOutputDir,
                "main" to mainOutputDir,
                "jvmMain" to jvmMainOutputDir,
                "androidMain" to androidMainOutputDir,
                "nativeMain" to nativeMainOutputDir,
                "stubMain" to stubMainOutputDir
            )

            sourceSets.forEach { (sourceSetName, outputDir) ->
                fsOperations.sync {
                    from(rawOutputDirectory.dir("$sourceSetName/kotlin"))
                    into(outputDir)
                }
            }
        } else {
            // PURE ANDROID MODE:
            fsOperations.sync {
                // The bindgen still nests files under `main/kotlin`.
                // We dive into that specific folder to strip the prefix!
                from(rawOutputDirectory.dir("main/kotlin")) {
                    include("**/*.kt")
                }
                into(mainOutputDir) // Route straight to the Variant API!
            }
        }

        // Sync the C-Interop files
        fsOperations.sync {
            from(rawOutputDirectory.dir("nativeInterop/cinterop"))
            into(cinteropOutputDir)
        }
    }
}