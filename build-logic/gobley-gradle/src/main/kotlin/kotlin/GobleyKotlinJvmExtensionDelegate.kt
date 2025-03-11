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
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

@OptIn(InternalGobleyGradleApi::class)
class GobleyKotlinJvmExtensionDelegate(
    project: Project
) : GobleyKotlinExtensionDelegate {
    private val kotlinJvmExtension: KotlinJvmProjectExtension =
        project.extensions.getByType()

    override val pluginId = PluginIds.KOTLIN_JVM

    override val targets: DomainObjectCollection<KotlinTarget> =
        project.container(KotlinTarget::class.java)

    override val sourceSets: NamedDomainObjectCollection<KotlinSourceSet>
        get() = kotlinJvmExtension.sourceSets

    override val implementationVersion: String?
        get() = kotlinJvmExtension.javaClass.`package`.implementationVersion

    init {
        kotlinJvmExtension.target { targets.add(this) }
    }
}
