/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.cargo.tasks

import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.tasks.CommandTask
import gobley.gradle.utils.CommandSpec
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File

@OptIn(InternalGobleyGradleApi::class)
abstract class CargoTask : CommandTask() {
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val cargo: Property<File>

    @get:Input
    @get:Optional
    abstract val nightly: Property<Boolean>

    @InternalGobleyGradleApi
    fun cargo(
        vararg argument: String,
        action: CommandSpec.() -> Unit = {},
    ) = cargo.map { it as Any }.orElse("cargo").flatMap { cargo ->
        if (cargo is File) {
            command(cargo) {
                if (nightly.orNull == true) {
                    arguments("+nightly")
                }
                arguments(*argument)
                action()
            }
        } else {
            command("cargo") {
                if (nightly.orNull == true) {
                    arguments("+nightly")
                }
                arguments(*argument)
                action()
            }
        }
    }
}
