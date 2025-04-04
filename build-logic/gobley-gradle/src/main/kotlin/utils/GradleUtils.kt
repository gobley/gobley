/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.utils

import gobley.gradle.InternalGobleyGradleApi
import org.gradle.api.Project
import org.gradle.api.invocation.Gradle
import kotlin.math.exp

@InternalGobleyGradleApi
object GradleUtils {
    private fun invokedByIde(): Boolean {
        return System.getProperty("idea.active").toBoolean()
    }

    private fun strictlyCalling(
        gradle: Gradle,
        vararg expectedTaskRequests: List<String>,
    ): Boolean {
        val taskRequests = gradle.startParameter.taskRequests.toMutableSet()
        for (expectedTaskRequest in expectedTaskRequests) {
            val taskRequest = taskRequests.firstOrNull { taskRequest ->
                expectedTaskRequest.zip(taskRequest.args).all { (expected, actual) ->
                    actual.endsWith(expected)
                }
            } ?: return false
            taskRequests.remove(taskRequest)
        }
        return taskRequests.isEmpty()
    }

    fun invokedByKotlinJvmBuild(gradle: Gradle): Boolean {
        return invokedByIde() && strictlyCalling(gradle, listOf(":classes"))
    }
}
