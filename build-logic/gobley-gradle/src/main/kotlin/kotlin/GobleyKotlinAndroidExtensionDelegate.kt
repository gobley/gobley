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
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

@OptIn(InternalGobleyGradleApi::class)
class GobleyKotlinAndroidExtensionDelegate(
    project: Project
) : GobleyKotlinExtensionDelegate {
    private val kotlinAndroidExtension: KotlinAndroidProjectExtension =
        project.extensions.getByType()

    override val pluginId = PluginIds.KOTLIN_ANDROID

    override val targets: DomainObjectCollection<KotlinTarget> =
        project.container(KotlinTarget::class.java)

    override val sourceSets: GobleyKotlinSourceSetCollection
        get() = GobleyKotlinSourceSetCollection(kotlinAndroidExtension.sourceSets)

    override val implementationVersion: String?
        get() = kotlinAndroidExtension.javaClass.`package`.implementationVersion

    override val jvmTarget: KotlinTarget? = null

    override val androidTarget: KotlinTarget?
        get() = targets.firstOrNull()

    init {
        kotlinAndroidExtension.target { targets.add(this) }
    }
}

@OptIn(InternalGobleyGradleApi::class)
private fun GobleyKotlinSourceSetCollection(sourceSets: NamedDomainObjectCollection<KotlinSourceSet>): GobleyKotlinSourceSetCollection {
    return object :
        NamedDomainObjectCollection<KotlinSourceSet> by sourceSets,
        GobleyKotlinSourceSetCollection {
        override val commonMain: KotlinSourceSet get() = androidMain

        override fun androidMain(variant: Variant?): KotlinSourceSet {
            return sourceSets.getByName(
                when (variant) {
                    Variant.Debug -> "debug"
                    Variant.Release -> "release"
                    null -> "main"
                }
            )
        }

        override fun androidUnitTest(variant: Variant?): KotlinSourceSet {
            return sourceSets.getByName(
                when (variant) {
                    Variant.Debug -> "testDebug"
                    Variant.Release -> "testRelease"
                    null -> "test"
                }
            )
        }

        override val jvmMain: KotlinSourceSet get() = error("not supported")
        override val jsMain: KotlinSourceSet get() = error("not supported")
        override val wasmJsMain: KotlinSourceSet get() = error("not supported")
        override val wasmWasiMain: KotlinSourceSet get() = error("not supported")
    }
}