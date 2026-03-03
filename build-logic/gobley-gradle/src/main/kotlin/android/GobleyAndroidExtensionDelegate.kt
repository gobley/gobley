/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.android

import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.Variant
import gobley.gradle.tasks.InjectJniLibsTask
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import java.io.File

typealias OnVariantAction = (
    String,
    String,
    (TaskProvider<InjectJniLibsTask>) -> Unit,
    ((TaskProvider<InjectJniLibsTask>) -> Unit)?
) -> Unit

@InternalGobleyGradleApi
interface GobleyAndroidExtensionDelegate {
    val androidSdkRoot: File
    val androidMinSdk: Int
    val androidNdkRoot: File?
    val androidNdkVersion: String?
    val abiFilters: Set<String>

    fun addMainSourceDir(
        variant: Variant? = null,
        sourceDirectory: FileCollection,
    )

    fun onVariants(
        project: Project,
        action: OnVariantAction,
    )

    fun addProguardFiles(
        project: Project,
        proguardFile: RegularFile,
        generationTask: TaskProvider<*>,
    )
}
