/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.cargo.dsl

import gobley.gradle.Variant
import gobley.gradle.cargo.tasks.TransformWasmTask
import gobley.gradle.cargo.utils.register
import gobley.gradle.rust.CrateType
import gobley.gradle.rust.targets.RustWasmTarget
import org.gradle.api.Project
import javax.inject.Inject

@Suppress("LeakingThis")
abstract class CargoWasmBuildVariant @Inject constructor(
    project: Project,
    build: CargoWasmBuild,
    variant: Variant,
    extension: CargoExtension,
) : DefaultCargoBuildVariant<RustWasmTarget, CargoWasmBuild>(project, build, variant, extension),
    HasEmbeddableRustLibrary {
    init {
        embedRustLibrary.convention(build.embedRustLibrary)
    }

    val transformWasmProvider = project.tasks.register<TransformWasmTask>({
        +this@CargoWasmBuildVariant
    }) {
        input.convention(buildTaskProvider.flatMap { task ->
            task.libraryFileByCrateType.map { it[CrateType.SystemDynamicLibrary]!! }
        })
        outputDirectory.convention(
            projectLayout.buildDirectory
                .dir("generated/cargo-wasm-transformation")
                .zip(profile) { dir, profile ->
                    dir
                        .dir(rustTarget.rustTriple)
                        .dir(profile.targetChildDirectoryName)
                }
        )
        crateName.convention(
            extension.cargoPackage.map { pkg ->
                pkg.libraryCrateName
            }
        )
        dependsOn(buildTaskProvider)
    }
}
