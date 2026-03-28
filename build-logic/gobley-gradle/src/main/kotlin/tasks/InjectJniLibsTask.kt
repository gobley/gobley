package gobley.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

abstract class InjectJniLibsTask : DefaultTask() {
    @get:InputFiles
    abstract val rustLibs: ConfigurableFileCollection

    @get:InputFiles
    abstract val otherLibs: ConfigurableFileCollection

    @get:Input
    abstract val abiName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val fsOperations: FileSystemOperations

    @TaskAction
    fun action() {
        fsOperations.sync { spec ->
            spec.from(rustLibs) {
                it.into(abiName)
            }
            spec.from(otherLibs) {
                it.into(abiName)
            }
            spec.into(outputDir)
        }
    }
}