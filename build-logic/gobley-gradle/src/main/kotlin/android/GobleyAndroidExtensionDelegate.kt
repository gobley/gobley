/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.android

import com.android.build.api.dsl.ApplicationBuildType
import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.LibraryBuildType
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.lint.LintModelWriterTask
import com.android.build.gradle.internal.tasks.ExtractProguardFiles
import com.android.build.gradle.internal.tasks.MergeConsumerProguardFilesTask
import com.android.build.gradle.tasks.MergeSourceSetFolders
import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.Variant
import gobley.gradle.getByVariant
import gobley.gradle.variant
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import java.io.File

@InternalGobleyGradleApi
interface GobleyAndroidExtensionDelegate {
    val androidSdkRoot: File
    val androidMinSdk: Int
    val androidNdkRoot: File?
    val androidNdkVersion: String?
    val abiFilters: Set<String>

    fun addMainSourceDir(
        variant: Variant? = null,
        sourceDirectory: Provider<Directory>,
    )

    fun addMainJniDir(
        project: Project,
        variant: Variant,
        jniTask: TaskProvider<*>,
        jniDirectory: Provider<Directory>,
    )

    fun addProguardFiles(
        project: Project,
        proguardFile: RegularFile,
        generationTask: TaskProvider<*>,
    )
}

@InternalGobleyGradleApi
fun GobleyAndroidExtensionDelegate(project: Project): GobleyAndroidExtensionDelegate {
    return GobleyAndroidExtensionDelegateImpl(project)
}

@OptIn(InternalGobleyGradleApi::class)
private class GobleyAndroidExtensionDelegateImpl(project: Project) :
    GobleyAndroidExtensionDelegate {
    private val androidExtension: BaseExtension = project.extensions.getByType()

    override val androidSdkRoot: File
        get() = androidExtension.sdkDirectory

    // TODO: Read <uses-sdk> from AndroidManifest.xml
    // androidExtension.sourceSets.getByName("main").manifest.srcFile
    override val androidMinSdk: Int
        get() = androidExtension.defaultConfig.minSdk ?: 21
    override val androidNdkRoot: File?
        get() = androidExtension.ndkPath?.let(::File)
    override val androidNdkVersion: String?
        get() = androidExtension.ndkVersion.takeIf(String::isNotEmpty)
    override val abiFilters: Set<String>
        get() = androidExtension.defaultConfig.ndk.abiFilters

    override fun addMainSourceDir(
        variant: Variant?,
        sourceDirectory: Provider<Directory>,
    ) {
        androidExtension.sourceSets { sourceSets ->
            val testSourceSet = if (variant != null) {
                sourceSets.getByVariant(variant)
            } else {
                sourceSets.getByName("main")
            }
            testSourceSet.java.srcDir(sourceDirectory)
        }
    }

    override fun addMainJniDir(
        project: Project,
        variant: Variant,
        jniTask: TaskProvider<*>,
        jniDirectory: Provider<Directory>
    ) {
        project.tasks.withType<MergeSourceSetFolders> {
            if (name.lowercase().contains("jni")) {
                if (variant == this.variant!!) {
                    inputs.dir(jniDirectory)
                    dependsOn(jniTask)
                }
            }
        }

        androidExtension.sourceSets { sourceSets ->
            val mainSourceSet = sourceSets.getByVariant(variant)
            mainSourceSet.jniLibs.srcDir(jniDirectory)
        }
    }

    override fun addProguardFiles(
        project: Project,
        proguardFile: RegularFile,
        generationTask: TaskProvider<*>,
    ) {
        androidExtension.buildTypes.configureEach { buildType ->
            addProguardFilesToBuildType(project, proguardFile, buildType, generationTask)
        }
    }

    private fun addProguardFilesToBuildType(
        project: Project,
        proguardFile: RegularFile,
        buildType: BuildType,
        generationTask: TaskProvider<*>,
    ) {
        // For some reason, androidExtension.buildTypes.getByName returns a internal BuildType
        // that implements both ApplicationBuildType and LibraryBuildType.

        if (buildType is ApplicationBuildType) {
            buildType.proguardFile(proguardFile)
        }

        // extractProguardFiles
        project.tasks.withType<ExtractProguardFiles> {
            dependsOn(generationTask)
        }
        // lintVitalAnalyze<variant>
        project.tasks.withType<AndroidLintAnalysisTask> {
            if (name.lowercase().contains(buildType.name.lowercase())) {
                dependsOn(generationTask)
            }
        }

        if (buildType is LibraryBuildType) {
            buildType.consumerProguardFile(proguardFile)
        }

        // merge<variant>ConsumerProguardFiles
        project.tasks.withType<MergeConsumerProguardFilesTask> {
            if (name.lowercase().contains(buildType.name.lowercase())) {
                dependsOn(generationTask)
            }
        }
        // generate<variant>LintModel
        project.tasks.withType<LintModelWriterTask> {
            if (name.lowercase().contains(buildType.name.lowercase())) {
                dependsOn(generationTask)
            }
        }
    }
}