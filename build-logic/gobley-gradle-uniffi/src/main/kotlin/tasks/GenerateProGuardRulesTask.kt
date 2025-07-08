/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.uniffi.tasks

import gobley.gradle.tasks.AbstractFileGenerationTask
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

@CacheableTask
abstract class GenerateProGuardRulesTask : AbstractFileGenerationTask() {
    @get:Input
    @get:Optional
    abstract val packageName: Property<String>

    @Suppress("LeakingThis")
    override val desiredOutput: Provider<String> =
        packageName.orElse("").map(::generateOutput)

    companion object {
        private fun generateOutput(packageName: String?) = StringBuilder().apply {
            appendLine("-keep class com.sun.jna.** { *; }")
            appendLine(
                when (packageName) {
                    null, "" -> "-keep class * implements com.sun.jna.** { *; }"
                    else -> "-keep class ${packageName}.** implements com.sun.jna.** { *; }"
                }
            )
            appendLine("-dontwarn java.awt.**")
        }.toString()
    }
}
