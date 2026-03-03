/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.android

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.Variant
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.File
import kotlin.jvm.java

@OptIn(InternalGobleyGradleApi::class)
@Suppress("UnstableApiUsage")
class GobleyAndroidKotlinMultiplatformExtensionDelegate(
    private val project: Project,
    private val kotlinMultiplatformExtension: KotlinMultiplatformExtension,
    private val kotlinMultiplatformLibraryExtension: KotlinMultiplatformAndroidComponentsExtension,
) : GobleyAndroidExtensionDelegate {

    constructor(project: Project) : this(
        project,
        project.extensions.getByType<KotlinMultiplatformExtension>(),
        project.extensions.getByType<KotlinMultiplatformAndroidComponentsExtension>()
    )

    private fun getAndroidTarget(): KotlinMultiplatformAndroidLibraryTarget {
        return kotlinMultiplatformExtension.targets.getByName("android") as KotlinMultiplatformAndroidLibraryTarget
    }

    // 2. Let AGP's `sdkComponents` do all the heavy lifting
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
        //Intentionally left blank
    }

    override fun onVariants(
        project: Project,
        action: OnVariantAction,
    ) {
        kotlinMultiplatformLibraryExtension.onVariants { agpVariant ->
            val cargoVariantName = if (agpVariant.name == "main" || agpVariant.name == "android") "debug" else agpVariant.name.lowercase()

            action(
                agpVariant.name,
                cargoVariantName,
                { task -> agpVariant.sources.jniLibs?.addGeneratedSourceDirectory(task) { it.outputDir } },
                (agpVariant as? com.android.build.api.variant.HasAndroidTest)?.androidTest?.let { testComp ->
                    { task -> testComp.sources.jniLibs?.addGeneratedSourceDirectory(task) { it.outputDir } }
                }
            )
        }
    }

    override fun addProguardFiles(
        project: Project,
        proguardFile: RegularFile,
        generationTask: TaskProvider<*>
    ) {
        val optimization = getAndroidTarget().optimization

        // 1. Create the file collection with the implicit task dependency
        val fileWithDependency = project.files(proguardFile).builtBy(generationTask)

        // 2. Use the DSL function .file(), NOT the property getter!
        optimization.keepRules.file(fileWithDependency)
        optimization.testKeepRules.file(fileWithDependency)

        optimization.consumerKeepRules.file(fileWithDependency)
        optimization.consumerKeepRules.publish = true
    }
}