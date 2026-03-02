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
import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.Variant
import gobley.gradle.getByVariant
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.lambdas.SerializableLambdas.action
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import java.io.File

@OptIn(InternalGobleyGradleApi::class)
class GobleyAndroidCommonExtensionDelegate(
    private val project: Project,
    private val commonExtension: CommonExtension,
    private val androidComponents: AndroidComponentsExtension<*, *, *>
) : GobleyAndroidExtensionDelegate {

    // Using the raw Java class reference bypasses Gradle's strict generic type matching
    // which caused the "Extension does not exist" errors.
    constructor(project: Project) : this(
        project,
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

    override fun addMainSourceDir(
        variant: Variant?,
        sourceDirectory: Provider<Directory>,
    ) {
        // Drop the { } block and access the property directly
        val testSourceSet = if (variant != null) {
            commonExtension.sourceSets.getByVariant(variant)
        } else {
            commonExtension.sourceSets.getByName("main")
        }
        testSourceSet.java.srcDir(sourceDirectory)
    }

    override fun addProguardFiles(
        project: Project,
        proguardFile: RegularFile,
        generationTask: TaskProvider<*>,
    ) {
        commonExtension.buildTypes.configureEach { buildType ->
            addProguardFilesToBuildType(project, proguardFile, buildType, generationTask)
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
                agpVariant.name, // standard Android maps directly
                { task -> agpVariant.sources.jniLibs?.addGeneratedSourceDirectory(task) { it.outputDir } },
                (agpVariant as? com.android.build.api.variant.HasAndroidTest)?.androidTest?.let { testComp ->
                    { task -> testComp.sources.jniLibs?.addGeneratedSourceDirectory(task) { it.outputDir } }
                }
            )
        }
    }

    private fun addProguardFilesToBuildType(
        project: Project,
        proguardFile: RegularFile,
        buildType: BuildType,
        generationTask: TaskProvider<*>,
    ) {
        // Creates a FileCollection that inherently knows it depends on 'generationTask'
        val generatedFileCollection = project.files(proguardFile).builtBy(generationTask)

        // When AGP consumes these files for R8/ProGuard, Gradle automatically injects the dependency.
        if (buildType is ApplicationBuildType) {
            buildType.proguardFiles(generatedFileCollection)
        }

        if (buildType is LibraryBuildType) {
            buildType.consumerProguardFiles(generatedFileCollection)
        }
    }
}