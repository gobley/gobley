/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class AbstractFileGenerationTask : DefaultTask() {
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    protected abstract val desiredOutput: Provider<String>

    init {
        outputs.upToDateWhen {
            val outputFile = outputFile.asFile.get()
            outputFile.exists() && outputFile.isFile && outputFile.readText(Charsets.UTF_8) == desiredOutput.get()
        }
    }

    @TaskAction
    fun generateFile() {
        val outputFile = outputFile.asFile.get()
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(desiredOutput.get())
    }
}