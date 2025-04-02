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
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

@OptIn(InternalGobleyGradleApi::class)
class GobleyKotlinMultiplatformExtensionDelegate(
    project: Project
) : GobleyKotlinExtensionDelegate {
    private val kotlinMultiplatformExtension: KotlinMultiplatformExtension =
        project.extensions.getByType()

    override val pluginId = PluginIds.KOTLIN_MULTIPLATFORM

    override val targets: DomainObjectCollection<KotlinTarget>
        get() = kotlinMultiplatformExtension.targets

    override val sourceSets: GobleyKotlinSourceSetCollection
        get() = GobleyKotlinSourceSetCollection(
            kotlinMultiplatformExtension.targets,
            kotlinMultiplatformExtension.sourceSets,
        )

    override val implementationVersion: String?
        get() = kotlinMultiplatformExtension.javaClass.`package`.implementationVersion

    override val jvmTarget: KotlinTarget?
        get() = targets.firstOrNull { it is KotlinJvmTarget }

    override val androidTarget: KotlinTarget?
        get() = targets.firstOrNull { it is KotlinAndroidTarget }
}

@OptIn(InternalGobleyGradleApi::class)
private fun GobleyKotlinSourceSetCollection(
    targets: DomainObjectCollection<KotlinTarget>,
    sourceSets: NamedDomainObjectContainer<KotlinSourceSet>,
): GobleyKotlinSourceSetCollection {
    return object :
        NamedDomainObjectCollection<KotlinSourceSet> by sourceSets,
        GobleyKotlinSourceSetCollection {
        override val commonMain: KotlinSourceSet
            get() = sourceSets.getByName("commonMain")

        private fun androidTarget(): KotlinAndroidTarget {
            return targets.filterIsInstance<KotlinAndroidTarget>().firstOrNull()
                ?: error("Android target not present")
        }

        override fun androidMain(variant: Variant?): KotlinSourceSet {
            val androidTarget = androidTarget()
            return sourceSets.maybeCreate(
                when (variant) {
                    Variant.Debug -> "${androidTarget.name}Debug"
                    Variant.Release -> "${androidTarget.name}Release"
                    null -> "androidMain"
                }
            )
        }

        override fun androidUnitTest(variant: Variant?): KotlinSourceSet {
            val androidTarget = androidTarget()
            return sourceSets.maybeCreate(
                when (variant) {
                    Variant.Debug -> "${androidTarget.name}UnitTestDebug"
                    Variant.Release -> "${androidTarget.name}UnitTestRelease"
                    null -> "${androidTarget.name}UnitTest"
                }
            )
        }

        private fun jvmTarget(): KotlinJvmTarget {
            return targets.filterIsInstance<KotlinJvmTarget>().firstOrNull()
                ?: error("JVM target not present")
        }

        override val jvmMain: KotlinSourceSet
            get() {
                val jvmTarget = jvmTarget()
                return sourceSets.getByName("${jvmTarget.name}Main")
            }
    }
}