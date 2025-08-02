/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.cargo.dsl

import gobley.gradle.rust.targets.RustWasmTarget
import org.gradle.api.Project
import javax.inject.Inject

@Suppress("LeakingThis")
abstract class CargoWasmBuild @Inject constructor(
    project: Project,
    rustTarget: RustWasmTarget,
    extension: CargoExtension,
) : DefaultCargoBuild<RustWasmTarget, CargoWasmBuildVariant>(
    project,
    rustTarget,
    extension,
    CargoWasmBuildVariant::class,
), HasWasmVariant, HasEmbeddableRustLibrary {
    init {
        embedRustLibrary.convention(true)
    }
}
