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

/**
 * A delegate for the [KotlinAndroidProjectExtension] used in Android-specific projects.
 *
 * This class provides a unified interface ([GobleyKotlinExtensionDelegate]) to access
 * Kotlin-related configurations, abstracting away the differences between Android,
 * Multiplatform, and JVM-only Kotlin plugins.
 */
@OptIn(InternalGobleyGradleApi::class)
class GobleyKotlinAndroidExtensionDelegate(
    project: Project
) : GobleyKotlinExtensionDelegate {
    /** The actual Kotlin Android extension provided by the 'kotlin-android' plugin. */
    private val kotlinAndroidExtension: KotlinAndroidProjectExtension =
        project.extensions.getByType()

    override val pluginId = PluginIds.KOTLIN_ANDROID

    /**
     * Android projects usually have a single target, but we store it in a collection
     * to match the interface requirements (similar to Multiplatform).
     */
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
        // We capture the target when it's configured in the Kotlin extension
        // and add it to our internal collection.
        kotlinAndroidExtension.target { targets.add(this) }
    }
}

/**
 * Creates a [GobleyKotlinSourceSetCollection] that wraps the standard Android source sets.
 *
 * This maps generic Gobley source set requests (like commonMain) to specific Android
 * source sets (like main, debug, release) based on the build variant.
 */
@OptIn(InternalGobleyGradleApi::class)
private fun GobleyKotlinSourceSetCollection(sourceSets: NamedDomainObjectCollection<KotlinSourceSet>): GobleyKotlinSourceSetCollection {
    return object :
        NamedDomainObjectCollection<KotlinSourceSet> by sourceSets,
        GobleyKotlinSourceSetCollection {
        
        /** In Android-only projects, 'commonMain' is mapped to 'androidMain'. */
        override val commonMain: KotlinSourceSet get() = androidMain

        override fun androidMain(variant: Variant?): KotlinSourceSet {
            // Map the Gobley Variant to the standard Android source set names.
            return sourceSets.getByName(
                when (variant) {
                    Variant.Debug -> "debug"
                    Variant.Release -> "release"
                    null -> "main"
                }
            )
        }

        override fun androidUnitTest(variant: Variant?): KotlinSourceSet {
            // Map the Gobley Variant to the standard Android unit test source set names.
            return sourceSets.getByName(
                when (variant) {
                    Variant.Debug -> "testDebug"
                    Variant.Release -> "testRelease"
                    null -> "test"
                }
            )
        }

        // JVM and JS are not supported in a Kotlin Android project context through this delegate.
        override val jvmMain: KotlinSourceSet get() = error("not supported")
        override val jsMain: KotlinSourceSet get() = error("not supported")
        override val wasmJsMain: KotlinSourceSet get() = error("not supported")
        override val wasmWasiMain: KotlinSourceSet get() = error("not supported")
    }
}
