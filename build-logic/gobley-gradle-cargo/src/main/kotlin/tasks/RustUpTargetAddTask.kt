/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.cargo.tasks

import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.rust.targets.RustTarget
import gobley.gradle.tasks.GloballyLockedTask
import gobley.gradle.tasks.globalLock
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

@CacheableTask
@OptIn(InternalGobleyGradleApi::class)
abstract class RustUpTargetAddTask : RustUpTask(), GloballyLockedTask {
    @get:Input
    abstract val rustTarget: Property<RustTarget>

    @get:Input
    abstract val rustVersion: Property<String>

    init {
        outputs.upToDateWhen {
            it as RustUpTargetAddTask
            it.isToolchainInstalled()
        }
    }

    private fun isToolchainInstalled(): Boolean {
        val tier = rustTarget.get().tier(rustVersion.get())
        // Don't rustup target add if the target tier is 3
        if (tier >= 3) {
            return true
        }

        val installedTargets = rustUp("target", "list") {
            captureStandardOutput()
        }.get().run {
            standardOutput!!
                .split('\n')
                .filter { it.endsWith(" (installed)") }
                .map { it.substringBefore(" (installed)") }
                .toSet()
        }

        return installedTargets.contains(rustTarget.get().rustTriple)
    }

    @TaskAction
    fun installToolchain() {
        globalLock("rustUpTargetAdd") {
            if (!isToolchainInstalled()) {
                rustUp("target", "add", rustTarget.get().rustTriple)
                    .get().apply {
                        assertNormalExitValueUsingLogger()
                    }
            }
        }
    }
}
