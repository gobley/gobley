/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.cargo.dsl

import gobley.gradle.cargo.tasks.FindDynamicLibrariesTask
import gobley.gradle.rust.targets.RustJvmTarget
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import java.io.File

interface CargoJvmBuildVariant<out RustTargetT : RustJvmTarget> :
    CargoDesktopBuildVariant<RustTargetT>,
    HasDynamicLibraries, HasJvmProperties {
    override val build: CargoJvmBuild<CargoJvmBuildVariant<RustTargetT>>
    val findDynamicLibrariesTaskProvider: TaskProvider<FindDynamicLibrariesTask>
    val libraryFiles: Provider<List<File>>
    val jarTaskProvider: TaskProvider<Jar>
}
