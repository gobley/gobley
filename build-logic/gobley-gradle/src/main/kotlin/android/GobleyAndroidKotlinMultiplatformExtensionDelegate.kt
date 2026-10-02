/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.android

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.api.variant.HasAndroidTest
import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.Variant
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.File

@OptIn(InternalGobleyGradleApi::class)
@Suppress("UnstableApiUsage")
class GobleyAndroidKotlinMultiplatformExtensionDelegate(
    private val kotlinMultiplatformExtension: KotlinMultiplatformExtension,
    private val kotlinMultiplatformLibraryExtension: KotlinMultiplatformAndroidComponentsExtension,
) : GobleyAndroidExtensionDelegate {

    constructor(project: Project) : this(
        project.extensions.getByType<KotlinMultiplatformExtension>(),
        project.extensions.getByType<KotlinMultiplatformAndroidComponentsExtension>()
    )

    private fun getAndroidTarget(): KotlinMultiplatformAndroidLibraryTarget {
        return kotlinMultiplatformExtension.targets.getByName("android") as KotlinMultiplatformAndroidLibraryTarget
    }

    override val androidSdkRoot: File
        get() = kotlinMultiplatformLibraryExtension.sdkComponents.sdkDirectory.get().asFile

    override val androidMinSdk: Int
        get() = getAndroidTarget().minSdk ?: 21

    override val androidNdkRoot: File?
        get() = kotlinMultiplatformLibraryExtension.sdkComponents.ndkDirectory.orNull?.asFile

    override val androidNdkVersion: String?
        get() = null

    override val abiFilters: Set<String>
        get() = emptySet()

    override fun <T : Task> addGeneratedBindingsDirectory(
        project: Project,
        taskProvider: TaskProvider<T>,
        directoryMapping: (T) -> DirectoryProperty
    ) {
        // Intentionally empty: KGP handles source routing in KMP
    }

    override fun onVariants(
        project: Project,
        action: OnVariantAction,
    ) {
        kotlinMultiplatformLibraryExtension.onVariants { agpVariant ->
            val isReleaseRequest = project.providers.gradleProperty("gobley.android.release")
                .map { it.toBoolean() }
                .getOrElse(false)

            val cargoVariant = if (isReleaseRequest) Variant.Release else Variant.Debug
            val androidTestComponent = (agpVariant as? HasAndroidTest)?.androidTest

            action(
                agpVariant.name,
                cargoVariant.name,
                { task ->
                    agpVariant.sources.jniLibs?.addGeneratedSourceDirectory(task) { it.outputDir }
                },
                androidTestComponent?.let { testComp -> { task ->
                        testComp.sources.jniLibs?.addGeneratedSourceDirectory(task) { it.outputDir }
                    }
                }
            )
        }
    }

    override fun addProguardFiles(
        project: Project,
        proguardFileProvider: Provider<RegularFile>,
        generationTask: TaskProvider<*>
    ) {
        val optimization = getAndroidTarget().optimization

        optimization.keepRules.file(proguardFileProvider)
        optimization.testKeepRules.file(proguardFileProvider)

        optimization.consumerKeepRules.file(proguardFileProvider)
        optimization.consumerKeepRules.publish = true
    }
}