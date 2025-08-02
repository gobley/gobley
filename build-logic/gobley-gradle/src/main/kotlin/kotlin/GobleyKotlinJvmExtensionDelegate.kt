/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.kotlin

import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.PluginIds
import gobley.gradle.Variant
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

    override val sourceSets: GobleyKotlinSourceSetCollection
        get() = GobleyKotlinSourceSetCollection(kotlinJvmExtension.sourceSets)

    override val implementationVersion: String?
        get() = kotlinJvmExtension.javaClass.`package`.implementationVersion

    override val jvmTarget: KotlinTarget?
        get() = targets.firstOrNull()

    override val androidTarget: KotlinTarget? = null

    init {
        kotlinJvmExtension.target { targets.add(this) }
    }
}

@OptIn(InternalGobleyGradleApi::class)
private fun GobleyKotlinSourceSetCollection(sourceSets: NamedDomainObjectCollection<KotlinSourceSet>): GobleyKotlinSourceSetCollection {
    return object :
        NamedDomainObjectCollection<KotlinSourceSet> by sourceSets,
        GobleyKotlinSourceSetCollection {
        override val commonMain get() = jvmMain
        override fun androidMain(variant: Variant?) = error("not supported")
        override fun androidUnitTest(variant: Variant?) = error("not supported")
        override val jvmMain: KotlinSourceSet get() = sourceSets.getByName("main")
        override val jsMain: KotlinSourceSet get() = error("not supported")
        override val wasmJsMain: KotlinSourceSet get() = error("not supported")
        override val wasmWasiMain: KotlinSourceSet get() = error("not supported")
    }
}