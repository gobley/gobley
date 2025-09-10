/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.cargo.tasks

import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.cargo.CargoMessage
import gobley.gradle.cargo.profiles.CargoProfile
import gobley.gradle.rust.CrateType
import gobley.gradle.rust.targets.RustNativeTarget
import gobley.gradle.rust.targets.RustTarget
import gobley.gradle.utils.CommandResult
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import java.io.File

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
                    target.outputFileName(cargoPackage.libraryCrateName, crateType)
                        ?: return@mapNotNull null
                )
            }.toMap()
        }

    // TODO: Annotate this with InternalGobleyGradleApi
    @get:OutputFile
    @get:Optional
    abstract val nativeStaticLibsDefFile: RegularFileProperty

    // TODO: Annotate this with InternalGobleyGradleApi
    // TODO: Make it public in the next version
    @get:OutputFile
    @get:Optional
    internal abstract val buildScriptOutputDirectoriesFile: RegularFileProperty

    // TODO: Annotate this with InternalGobleyGradleApi
    // TODO: Make it public in the next version
    @get:Internal
    internal val buildScriptOutputDirectories: Provider<List<File>> =
        buildScriptOutputDirectoriesFile.map {
            it.asFile.readText().split(' ').filter(String::isNotEmpty).map(::File)
        }

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
            arguments("--message-format", "json")
            for (extraArgument in extraArguments.get()) {
                arguments(extraArgument)
            }
            arguments("--")
            if (nativeStaticLibsDefFile.isPresent) {
                arguments("--print", "native-static-libs")
            }
            suppressXcodeIosToolchains()
            captureStandardError()
            captureStandardOutput()
        }.get().apply {
            for (line in standardOutput!!.split('\n')) {
                val message = runCatching { CargoMessage(line) }.getOrNull()
                if (message == null) {
                    logger.lifecycle(line)
                    continue
                }
                if (message !is CargoMessage.CompilerMessage) {
                    continue
                }
                val renderedMessage = message.message.rendered?.takeIf(String::isNotBlank)
                    ?: continue
                if (
                    arrayOf(
                        "note: Link against",
                        "note: native-static-libs:",
                    ).any(renderedMessage::startsWith)
                ) continue
                when (message.message.level) {
                    "error" -> logger.error(renderedMessage)
                    "warning" -> logger.warn(renderedMessage)
                    else -> logger.lifecycle(renderedMessage)
                }
            }
            assertNormalExitValueUsingLogger(
                printStdout = false,
                printStderr = true,
            )
        }

        if (nativeStaticLibsDefFile.isPresent || buildScriptOutputDirectoriesFile.isPresent) {
            val cargoMessages = result.toCargoMessages()
            if (nativeStaticLibsDefFile.isPresent) {
                populateNativeStaticLibsDefFile(cargoMessages)
            }
            if (buildScriptOutputDirectoriesFile.isPresent) {
                populateBuildScriptOutputDirectoriesFile(cargoMessages)
            }
        }
    }

    @OptIn(InternalGobleyGradleApi::class)
    private fun CommandResult.toCargoMessages(): List<CargoMessage> {
        return standardOutput!!
            .split('\n')
            .mapNotNull { runCatching { CargoMessage(it) }.getOrNull() }
    }

    private fun populateNativeStaticLibsDefFile(messages: List<CargoMessage>) {
        val librarySearchPaths = mutableListOf<String>()
        var staticLibraries: String? = null
        for (message in messages) {
            when (message) {
                is CargoMessage.BuildScriptExecuted -> {
                    val buildScriptOutput = File(message.outDir).parentFile?.resolve("output")
                        ?.readLines(Charsets.UTF_8)
                    if (buildScriptOutput != null) {
                        for (line in buildScriptOutput) {
                            val searchPath = line
                                .substringAfter("cargo:", "")
                                .trim(':')
                                .substringAfter("rustc-link-search=", "")
                                .takeIf(String::isNotEmpty)
                                ?.replace("\\", "/")
                                ?.split('=')
                            when (searchPath?.size) {
                                1 -> librarySearchPaths.add(searchPath[0])
                                2 -> if (searchPath[1] != "crate" && searchPath[1] != "dependency") {
                                    librarySearchPaths.add(searchPath[1])
                                }
                            }
                        }
                    }

                }

                is CargoMessage.CompilerMessage -> {
                    if (staticLibraries == null) {
                        val note = message.message.rendered?.trim()
                        staticLibraries =
                            note?.trim()?.substringAfter("note: native-static-libs: ", "")
                                ?.takeIf(String::isNotEmpty)
                    }
                }

                else -> {}
            }
        }

        val target = target.get()
        val linkerOptName = if (target is RustNativeTarget) {
            "linkerOpts.${target.cinteropName}"
        } else {
            "linkerOpts"
        }
        val linkerFlag = StringBuilder().apply {
            if (librarySearchPaths.isNotEmpty()) {
                append(librarySearchPaths.joinToString(" ") { "-L$it" })
                if (staticLibraries != null) {
                    append(' ')
                }
            }
            if (staticLibraries != null) {
                append(staticLibraries)
            }
        }
        val nativeStaticLibsDefFile = nativeStaticLibsDefFile.get().asFile.apply {
            parentFile?.mkdirs()
        }
        nativeStaticLibsDefFile.writeText(StringBuilder().apply {
            if (linkerFlag.isNotEmpty()) {
                append("$linkerOptName = $linkerFlag\n")
            }
            val libraryFile = libraryFileByCrateType.get()[CrateType.SystemStaticLibrary]
            if (libraryFile != null) {
                append("staticLibraries = ${libraryFile.asFile.name}")
            }
        }.toString())
    }

    private fun populateBuildScriptOutputDirectoriesFile(messages: List<CargoMessage>) {
        val buildScriptOutputDirectoriesFile = buildScriptOutputDirectoriesFile.get().asFile.apply {
            parentFile?.mkdirs()
        }
        buildScriptOutputDirectoriesFile.outputStream().use { outputStream ->
            outputStream.writer(Charsets.UTF_8).use { writer ->
                var first = true
                for (message in messages) {
                    if (message !is CargoMessage.BuildScriptExecuted) {
                        continue
                    }
                    if (!first) {
                        writer.append(' ')
                    }
                    first = false
                    writer.write(message.outDir)
                }
            }
        }
    }
}
