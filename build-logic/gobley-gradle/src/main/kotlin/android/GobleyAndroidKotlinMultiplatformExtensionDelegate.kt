/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.android

import com.android.build.api.dsl.KotlinMultiplatformAndroidTarget
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.lint.LintModelWriterTask
import com.android.build.gradle.internal.tasks.ExtractProguardFiles
import com.android.build.gradle.internal.tasks.MergeConsumerProguardFilesTask
import com.android.build.gradle.internal.tasks.MergeNativeLibsTask
import com.android.build.gradle.tasks.MergeSourceSetFolders
import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.Variant
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.File
import java.util.Properties

@OptIn(InternalGobleyGradleApi::class)
@Suppress("UnstableApiUsage")
class GobleyAndroidKotlinMultiplatformExtensionDelegate(
    private val project: Project,
    private val kotlinMultiplatformExtension: KotlinMultiplatformExtension,
) : GobleyAndroidExtensionDelegate {
    constructor(project: Project) : this(project, project.extensions.getByType<KotlinMultiplatformExtension>())

    private val androidTarget: KotlinMultiplatformAndroidTarget by lazy {
        val extensionAware = kotlinMultiplatformExtension as ExtensionAware
        extensionAware.extensions.findByType(KotlinMultiplatformAndroidTarget::class.java)
            ?: error("Kotlin Multiplatform Android target not found")
    }

    override val androidSdkRoot: File
        get() = project.findAndroidSdkRoot()
            ?: throw IllegalStateException(
                "Android SDK location not found. Set `sdk.dir` in local.properties or define " +
                        "`ANDROID_HOME`/`ANDROID_SDK_ROOT`."
            )
    override val androidMinSdk: Int
        get() = androidTarget.minSdk ?: 21
    override val androidNdkRoot: File?
        get() = null
    override val androidNdkVersion: String?
        get() = null
    override val abiFilters: Set<String>
        get() = emptySet()

    override fun addMainSourceDir(
        variant: Variant?,
        sourceDirectory: Provider<Directory>,
    ) {
        val sourceSetName = when (variant) {
            Variant.Debug, Variant.Release -> "androidMain"
            null -> "androidMain"
        }
        kotlinMultiplatformExtension.sourceSets
            .findByName(sourceSetName)
            ?.kotlin
            ?.srcDir(sourceDirectory)
    }

    override fun addMainJniDir(
        project: Project,
        variant: Variant,
        jniTask: TaskProvider<*>,
        jniDirectory: Provider<Directory>,
    ) {
        val variantName = variant.name.lowercase()
        project.tasks.withType<MergeNativeLibsTask> {
            if (name.lowercase().contains(variantName)) {
                inputs.dir(jniDirectory)
                dependsOn(jniTask)
            }
        }
        project.tasks.withType<MergeSourceSetFolders> {
            val normalizedName = name.lowercase()
            if ((normalizedName.contains("jni") || normalizedName.contains("nativelibs"))
                && normalizedName.contains(variantName)
            ) {
                inputs.dir(jniDirectory)
                dependsOn(jniTask)
            }
        }
    }

    override fun addProguardFiles(
        project: Project,
        proguardFile: RegularFile,
        generationTask: TaskProvider<*>,
    ) {
        val optimization = androidTarget.optimization
        optimization.keepRules.file(proguardFile.asFile)
        optimization.testKeepRules.file(proguardFile.asFile)
        optimization.consumerKeepRules.file(proguardFile.asFile)
        optimization.consumerKeepRules.publish = true

        project.tasks.withType<ExtractProguardFiles> {
            dependsOn(generationTask)
        }
        project.tasks.withType<AndroidLintAnalysisTask> {
            dependsOn(generationTask)
        }
        project.tasks.withType<MergeConsumerProguardFilesTask> {
            dependsOn(generationTask)
        }
        project.tasks.withType<LintModelWriterTask> {
            dependsOn(generationTask)
        }
    }
}

private fun Project.findAndroidSdkRoot(): File? {
    val sdkFromProperties = rootProject.file("local.properties")
        .takeIf(File::exists)
        ?.inputStream()
        ?.use { input ->
            Properties().apply { load(input) }.getProperty("sdk.dir")
        }
        ?.takeIf(String::isNotBlank)
        ?.let(::File)
        ?.takeIf(File::exists)
    if (sdkFromProperties != null) {
        return sdkFromProperties
    }

    return sequenceOf("ANDROID_HOME", "ANDROID_SDK_ROOT")
        .mapNotNull(System::getenv)
        .filter(String::isNotBlank)
        .map(::File)
        .firstOrNull(File::exists)
}
