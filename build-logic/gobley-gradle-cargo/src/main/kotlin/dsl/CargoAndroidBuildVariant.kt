/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.cargo.dsl

import gobley.gradle.Variant
import gobley.gradle.cargo.tasks.FindDynamicLibrariesTask
import gobley.gradle.cargo.utils.register
import gobley.gradle.rust.targets.RustAndroidTarget
import org.gradle.api.Project
import javax.inject.Inject

@Suppress("LeakingThis")
abstract class CargoAndroidBuildVariant @Inject constructor(
    project: Project,
    build: CargoAndroidBuild,
    variant: Variant,
    extension: CargoExtension,
) : DefaultCargoBuildVariant<RustAndroidTarget, CargoAndroidBuild>(project, build, variant, extension),
    CargoMobileBuildVariant<RustAndroidTarget>,
    CargoNativeBuildVariant<RustAndroidTarget>,
    HasDynamicLibraries {
    init {
        dynamicLibraries.addAll(build.dynamicLibraries)
        dynamicLibrarySearchPaths.addAll(build.dynamicLibrarySearchPaths)
        embedRustLibrary.convention(build.embedRustLibrary)
    }

    val findDynamicLibrariesTaskProvider = project.tasks.register<FindDynamicLibrariesTask>({
        +this@CargoAndroidBuildVariant
    }) {
        rustTarget.set(this@CargoAndroidBuildVariant.rustTarget)
        libraryNames.set(this@CargoAndroidBuildVariant.dynamicLibraries)
        searchPaths.set(this@CargoAndroidBuildVariant.dynamicLibrarySearchPaths)
    }
}
