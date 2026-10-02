/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.cargo.tasks

import gobley.gradle.rust.CrateType
import gobley.gradle.rust.targets.RustTarget
import org.gradle.api.DefaultTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

@CacheableTask
abstract class FindDynamicLibrariesTask : DefaultTask() {
    @get:Input
    abstract val rustTarget: Property<RustTarget>

    @get:Input
    abstract val libraryNames: SetProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val searchPaths: ListProperty<File>

    @get:OutputFile
    abstract val libraryPathsCacheFile: RegularFileProperty

    @Suppress("LeakingThis")
    @Internal
    val libraryPaths: Provider<Set<File>> = libraryPathsCacheFile.map {
        it.asFile.readText().split(' ').filter(String::isNotEmpty).map(::File).toSet()
    }

    @get:Inject
    abstract val projectLayout: ProjectLayout

    @TaskAction
    fun copyDynamicLibraries() {
        val rustTarget = rustTarget.get()
        val searchPaths = searchPaths.get() + projectLayout.projectDirectory.asFile

        val libraryPaths = libraryNames.get().mapNotNull { libraryName ->
            val libraryPath = File(libraryName)
            if (libraryPath.isAbsolute) {
                return@mapNotNull libraryPath.takeIf(File::exists)
            }

            searchPaths.firstNotNullOfOrNull { searchPath ->
                searchPath
                    .resolve(
                        rustTarget.outputFileName(
                            libraryName,
                            CrateType.SystemDynamicLibrary,
                        )!!,
                    )
                    .takeIf(File::exists)
                    ?: searchPath
                        .resolve(libraryName)
                        .takeIf(File::exists)
            }
        }

        libraryPathsCacheFile.get().asFile.run {
            parentFile?.mkdirs()
            writeText(libraryPaths.joinToString(" ") { it.absolutePath })
        }
    }
}
