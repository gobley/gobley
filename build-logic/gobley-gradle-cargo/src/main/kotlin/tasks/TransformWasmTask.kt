/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.cargo.tasks

import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.tasks.CommandTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@OptIn(InternalGobleyGradleApi::class)
@CacheableTask
abstract class TransformWasmTask : CommandTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val wasmTransformer: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val input: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val crateName: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val functionImportsFile: RegularFileProperty

    @TaskAction
    fun transformWasm() {
        @OptIn(InternalGobleyGradleApi::class)
        command(wasmTransformer) {
            input.get().asFile.parentFile?.run {
                if (!exists()) {
                    mkdirs()
                }
            }
            outputDirectory.get().asFile.run {
                if (!exists()) {
                    mkdirs()
                }
            }
            val packageName = "gobley.wasm.${crateName.get().replace('-', '_')}"
            arguments("--input", input.get())
            arguments("--output", outputDirectory.get().file("$packageName.kt"))
            arguments("--package-name", packageName)
            if (functionImportsFile.isPresent) {
                arguments("--function-imports-file", functionImportsFile.get())
            }
        }.get().apply {
            assertNormalExitValue()
        }
    }
}