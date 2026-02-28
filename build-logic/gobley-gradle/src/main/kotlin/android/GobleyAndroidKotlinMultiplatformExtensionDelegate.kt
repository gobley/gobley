/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.android

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.lint.LintModelWriterTask
import com.android.build.gradle.internal.tasks.ExtractProguardFiles
import com.android.build.gradle.internal.tasks.MergeConsumerProguardFilesTask
import com.android.build.gradle.tasks.MergeSourceSetFolders
import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.Variant
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.File

@OptIn(InternalGobleyGradleApi::class)
@Suppress("UnstableApiUsage")
class GobleyAndroidKotlinMultiplatformExtensionDelegate(
    private val project: Project,
    private val kotlinMultiplatformExtension: KotlinMultiplatformExtension,
) :
    GobleyAndroidExtensionDelegate {
    constructor(project: Project) : this(project, project.extensions.getByType<KotlinMultiplatformExtension>())

    private fun getAndroidTarget(): KotlinMultiplatformAndroidLibraryTarget {
        return kotlinMultiplatformExtension.targets.getByName("android") as KotlinMultiplatformAndroidLibraryTarget
    }

    override val androidSdkRoot: File
        get() = File(project.rootDir, "local.properties").let { file ->
            val properties = java.util.Properties()
            if (file.exists()) file.inputStream().use { properties.load(it) }
            properties.getProperty("sdk.dir")?.let(::File)
        } ?: File(System.getenv("ANDROID_HOME") ?: "")

    override val androidMinSdk: Int
        get() = getAndroidTarget().minSdk ?: 21

    override val androidNdkRoot: File?
        get() = File(project.rootDir, "local.properties").let { file ->
            val properties = java.util.Properties()
            if (file.exists()) file.inputStream().use { properties.load(it) }
            properties.getProperty("ndk.dir")?.let(::File)
        } ?: System.getenv("ANDROID_NDK_HOME")?.let(::File)

    override val androidNdkVersion: String?
        get() = null

    override val abiFilters: Set<String>
        get() = emptySet()

    override fun addMainSourceDir(variant: Variant?, sourceDirectory: Provider<Directory>) {
        getAndroidTarget().compilations.configureEach { compilation ->
            if (variant == null || compilation.name.equals(variant.name, ignoreCase = true)) {
                compilation.defaultSourceSet.kotlin.srcDir(sourceDirectory)
            }
        }
    }

    override fun addMainJniDir(
        project: Project,
        variant: Variant,
        jniTask: TaskProvider<*>,
        jniDirectory: Provider<Directory>
    ) {
        // Without BaseExtension, we hook into tasks directly to package the JNI libs.
        // E.g. hooking MergeSourceSetFolders
        project.tasks.withType<MergeSourceSetFolders> {
            if (name.contains("jni", ignoreCase = true) && name.contains(variant.name, ignoreCase = true)) {
                inputs.dir(jniDirectory)
                dependsOn(jniTask)
            }
        }
    }

    override fun addProguardFiles(
        project: Project,
        proguardFile: RegularFile,
        generationTask: TaskProvider<*>
    ) {
        val optimization = getAndroidTarget().optimization
        optimization.keepRules.file(proguardFile.asFile)
        optimization.testKeepRules.file(proguardFile.asFile)
        optimization.consumerKeepRules.file(proguardFile.asFile)
        optimization.consumerKeepRules.publish = true

        // extractProguardFiles
        project.tasks.withType<ExtractProguardFiles> {
            dependsOn(generationTask)
        }
        // lintVitalAnalyze<variant>
        project.tasks.withType<AndroidLintAnalysisTask> {
            dependsOn(generationTask)
        }

        // merge<variant>ConsumerProguardFiles
        project.tasks.withType<MergeConsumerProguardFilesTask> {
            dependsOn(generationTask)
        }
        // generate<variant>LintModel
        project.tasks.withType<LintModelWriterTask> {
            dependsOn(generationTask)
        }
    }
}