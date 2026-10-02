/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.android

import com.android.build.api.dsl.ApplicationBuildType
import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryBuildType
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasAndroidTest
import gobley.gradle.InternalGobleyGradleApi
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import java.io.File

@OptIn(InternalGobleyGradleApi::class)
class GobleyAndroidCommonExtensionDelegate(
    private val commonExtension: CommonExtension,
    private val androidComponents: AndroidComponentsExtension<*, *, *>
) : GobleyAndroidExtensionDelegate {

    constructor(project: Project) : this(
        project.extensions.getByType(CommonExtension::class.java),
        project.extensions.getByType(AndroidComponentsExtension::class.java)
    )

    override val androidSdkRoot: File
        get() = androidComponents.sdkComponents.sdkDirectory.get().asFile

    override val androidMinSdk: Int
        get() = commonExtension.defaultConfig.minSdk ?: 21

    override val androidNdkRoot: File?
        get() = commonExtension.ndkPath?.let(::File)

    override val androidNdkVersion: String?
        get() = commonExtension.ndkVersion.takeIf { it.isNotEmpty() }

    override val abiFilters: Set<String>
        get() = commonExtension.defaultConfig.ndk.abiFilters

    override fun <T : Task> addGeneratedBindingsDirectory(
        project: Project,
        taskProvider: TaskProvider<T>,
        directoryMapping: (T) -> DirectoryProperty
    ) {
        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)

        androidComponents.onVariants { variant ->
            variant.sources.java?.addGeneratedSourceDirectory(taskProvider, directoryMapping)
        }
    }

    override fun addProguardFiles(
        project: Project,
        proguardFileProvider: Provider<RegularFile>,
        generationTask: TaskProvider<*>
    ) {
        commonExtension.buildTypes.configureEach { buildType ->
            addProguardFilesToBuildType(project, proguardFileProvider, buildType, generationTask)
        }
    }

    override fun onVariants(
        project: Project,
        action: OnVariantAction,
    ) {
        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)

        androidComponents.onVariants { agpVariant ->
            action(
                agpVariant.name,
                agpVariant.name,
                { task -> agpVariant.sources.jniLibs?.addGeneratedSourceDirectory(task) { it.outputDir } },
                (agpVariant as? HasAndroidTest)?.androidTest?.let { testComp ->
                    { task -> testComp.sources.jniLibs?.addGeneratedSourceDirectory(task) { it.outputDir } }
                }
            )
        }
    }

    private fun addProguardFilesToBuildType(
        project: Project,
        proguardFileProvider: Provider<RegularFile>,
        buildType: BuildType,
        generationTask: TaskProvider<*>,
    ) {
        if (buildType is ApplicationBuildType) {
            buildType.proguardFiles(proguardFileProvider)
        }

        if (buildType is LibraryBuildType) {
            buildType.consumerProguardFiles(proguardFileProvider)
        }

        project.tasks.matching { it.name == "preBuild" }.configureEach {
            it.dependsOn(generationTask)
        }
    }
}