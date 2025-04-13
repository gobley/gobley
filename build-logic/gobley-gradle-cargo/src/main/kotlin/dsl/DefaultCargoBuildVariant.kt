/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.cargo.dsl

import gobley.gradle.Variant
import gobley.gradle.cargo.CargoPlugin
import gobley.gradle.cargo.tasks.CargoBuildTask
import gobley.gradle.cargo.tasks.CargoCheckTask
import gobley.gradle.cargo.utils.register
import gobley.gradle.rust.targets.RustTarget
import org.gradle.api.Project

@Suppress("LeakingThis")
abstract class DefaultCargoBuildVariant<out RustTargetT : RustTarget, out CargoBuildT : CargoBuild<CargoBuildVariant<RustTargetT>>>(
    final override val project: Project,
    override val build: CargoBuildT,
    final override val variant: Variant,
    extension: CargoExtension,
) : CargoBuildVariant<RustTargetT> {
    init {
        profile.convention(extension.variant(variant).profile)
        features.apply {
            addAll(extension.variant(variant).features)
            addAll(build.features)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override val rustTarget: RustTargetT = build.rustTarget as RustTargetT

    override val buildTaskProvider =
        project.tasks.register<CargoBuildTask>({ +this@DefaultCargoBuildVariant }) {
            group = CargoPlugin.TASK_GROUP
            cargoPackage.set(extension.cargoPackage)
            profile.set(this@DefaultCargoBuildVariant.profile)
            target.set(this@DefaultCargoBuildVariant.rustTarget)
            features.set(this@DefaultCargoBuildVariant.features)
        }

    override val checkTaskProvider =
        project.tasks.register<CargoCheckTask>({ +this@DefaultCargoBuildVariant }) {
            group = CargoPlugin.TASK_GROUP
            cargoPackage.set(extension.cargoPackage)
            profile.set(this@DefaultCargoBuildVariant.profile)
            target.set(this@DefaultCargoBuildVariant.rustTarget)
            features.set(this@DefaultCargoBuildVariant.features)
            checkCommand.convention(extension.checkCommand)
        }
}
