/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.cargo.dsl

import gobley.gradle.Variant
import gobley.gradle.rust.targets.RustTarget
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.kotlin.dsl.domainObjectContainer
import org.gradle.kotlin.dsl.newInstance
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import kotlin.reflect.KClass

@Suppress("LeakingThis")
abstract class DefaultCargoBuild<out RustTargetT : RustTarget, out CargoBuildVariantT : DefaultCargoBuildVariant<RustTargetT, *>>(
    final override val project: Project,
    final override val rustTarget: RustTargetT,
    extension: CargoExtension,
    variantClass: KClass<out CargoBuildVariantT>,
) : CargoBuild<CargoBuildVariantT> {
    final override val debug: CargoBuildVariantT = project.objects.newInstance(
        variantClass,
        this,
        Variant.Debug,
        extension,
    )

    final override val release: CargoBuildVariantT = project.objects.newInstance(
        variantClass,
        this,
        Variant.Release,
        extension,
    )

    final override val variants: Iterable<CargoBuildVariantT> = arrayListOf(debug, release)

    override fun getName(): String = rustTarget.friendlyName

    override val kotlinTargets: NamedDomainObjectContainer<KotlinTarget> =
        project.objects.domainObjectContainer(KotlinTarget::class)

    init {
        @Suppress("LeakingThis")
        features.addAll(extension.features)
        checkCommand.convention(extension.checkCommand)
        installTargetBeforeBuild.convention(extension.installTargetBeforeBuild)
    }
}
