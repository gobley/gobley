/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.kotlin

import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.PluginIds
import org.gradle.api.DomainObjectCollection
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

@OptIn(InternalGobleyGradleApi::class)
class GobleyKotlinMultiplatformExtensionDelegate(
    project: Project
) : GobleyKotlinExtensionDelegate {
    private val kotlinMultiplatformExtension: KotlinMultiplatformExtension =
        project.extensions.getByType()

    override val pluginId = PluginIds.KOTLIN_MULTIPLATFORM

    override val targets: DomainObjectCollection<KotlinTarget>
        get() = kotlinMultiplatformExtension.targets

    override val sourceSets: NamedDomainObjectCollection<KotlinSourceSet>
        get() = kotlinMultiplatformExtension.sourceSets

    override val implementationVersion: String?
        get() = kotlinMultiplatformExtension.javaClass.`package`.implementationVersion
}
