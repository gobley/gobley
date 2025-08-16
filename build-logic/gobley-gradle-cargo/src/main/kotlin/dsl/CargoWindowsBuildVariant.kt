/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.cargo.dsl

import gobley.gradle.Variant
import gobley.gradle.cargo.tasks.FindDynamicLibrariesTask
import gobley.gradle.cargo.utils.register
import gobley.gradle.rust.CrateType
import gobley.gradle.rust.targets.RustWindowsTarget
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.listProperty
import java.io.File
import javax.inject.Inject

@Suppress("LeakingThis")
abstract class CargoWindowsBuildVariant @Inject constructor(
    project: Project,
    build: CargoWindowsBuild,
    variant: Variant,
    extension: CargoExtension,
) : DefaultCargoBuildVariant<RustWindowsTarget, CargoWindowsBuild>(project, build, variant, extension),
    CargoJvmBuildVariant<RustWindowsTarget> {
    init {
        dynamicLibraries.addAll(build.dynamicLibraries)
        dynamicLibrarySearchPaths.addAll(build.dynamicLibrarySearchPaths)
        embedRustLibrary.convention(build.embedRustLibrary)
        resourcePrefix.convention(build.resourcePrefix)
        androidUnitTest.convention(build.androidUnitTest)
    }

    override val findDynamicLibrariesTaskProvider = project.tasks.register<FindDynamicLibrariesTask>({
        +this@CargoWindowsBuildVariant
    }) {
        rustTarget.set(this@CargoWindowsBuildVariant.rustTarget)
        libraryNames.set(this@CargoWindowsBuildVariant.dynamicLibraries)
        searchPaths.set(this@CargoWindowsBuildVariant.dynamicLibrarySearchPaths)
    }

    override val libraryFiles: Provider<List<File>> = project.objects.listProperty<File>().apply {
        add(buildTaskProvider.flatMap { task ->
            task.libraryFileByCrateType.map { it[CrateType.SystemDynamicLibrary]!!.asFile }
        })
        addAll(findDynamicLibrariesTaskProvider.flatMap { it.libraryPaths })
    }

    override val jarTaskProvider = project.tasks.register<Jar>({
        +"jvmRustRuntime"
        +this@CargoWindowsBuildVariant
    }) {
        from(libraryFiles)
        into(resourcePrefix)
        archiveClassifier.set(resourcePrefix.map {
            val prefix = it.takeIf(String::isNotEmpty) ?: rustTarget.jnaResourcePrefix
            val variantSuffix = when (variant) {
                Variant.Debug -> "-$variant"
                else -> ""
            }
            "$prefix$variantSuffix"
        })
        dependsOn(buildTaskProvider, findDynamicLibrariesTaskProvider)
    }
}
