/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.cargo.tasks

import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.cargo.profiles.CargoProfile
import gobley.gradle.rust.targets.RustTarget
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

@Suppress("LeakingThis")
@CacheableTask
abstract class CargoCheckTask : CargoPackageTask() {
    @get:Input
    abstract val profile: Property<CargoProfile>

    @get:Input
    abstract val target: Property<RustTarget>

    @get:Input
    abstract val features: SetProperty<String>

    @get:Input
    abstract val checkCommand: Property<String>

    @get:Input
    abstract val extraArguments: ListProperty<String>

    @TaskAction
    @OptIn(InternalGobleyGradleApi::class)
    fun check() {
        val profile = profile.get()
        val target = target.get()
        cargo(checkCommand.get()) {
            arguments("--profile", profile.profileName)
            arguments("--target", target.rustTriple)
            if (features.isPresent) {
                if (features.get().isNotEmpty()) {
                    arguments("--features", features.get().joinToString(","))
                }
            }
            for (extraArgument in extraArguments.get()) {
                arguments(extraArgument)
            }
            suppressXcodeIosToolchains()
        }.get().apply {
            assertNormalExitValueUsingLogger()
        }
    }
}