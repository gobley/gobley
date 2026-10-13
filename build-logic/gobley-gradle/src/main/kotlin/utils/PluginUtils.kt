/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.utils

import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.PluginIds
import gobley.gradle.android.GobleyAndroidBaseExtensionDelegate
import gobley.gradle.android.GobleyAndroidExtensionDelegate
import gobley.gradle.android.GobleyAndroidKotlinMultiplatformExtensionDelegate
import gobley.gradle.kotlin.GobleyKotlinAndroidExtensionDelegate
import gobley.gradle.kotlin.GobleyKotlinExtensionDelegate
import gobley.gradle.kotlin.GobleyKotlinJvmExtensionDelegate
import gobley.gradle.kotlin.GobleyKotlinMultiplatformExtensionDelegate
import org.gradle.api.GradleException
import org.gradle.api.Project

@InternalGobleyGradleApi
object PluginUtils {
    @InternalGobleyGradleApi
    data class PluginInfo(val pluginName: String, val id: String)

    fun ensurePluginIsApplied(project: Project, pluginName: String, id: String) {
        ensurePluginIsApplied(project, PluginInfo(pluginName, id))
    }

    fun ensurePluginIsApplied(project: Project, vararg plugins: PluginInfo) {
        if (plugins.isEmpty()) {
            return
        }
        for (plugin in plugins) {
            if (project.plugins.hasPlugin(plugin.id)) {
                return
            }
        }
        project.logger.error(requiredPluginMessage(*plugins))
        if (plugins.size == 1) {
            throw GradleException("No ${plugins[0].pluginName} Gradle plugin found")
        } else {
            throw GradleException("Some required Gradle plugins not found")
        }
    }

    private fun requiredPluginMessage(vararg plugins: PluginInfo): String {
        val message = if (plugins.size == 1) {
            "Please include the ${plugins[0].pluginName} Gradle plugin in your build configuration."
        } else {
            "Please include one of the following Gradle plugins in your build configuration."
        }
        return StringBuilder().apply {
            append(message)
            append("\n\n")
            append("plugins {\n")
            append("  // ...\n")
            var first = true
            for (plugin in plugins) {
                if (!first) {
                    append("  // or\n")
                }
                first = false
                append("  id(\"${plugin.id}\")\n")
            }
            append("  // ...\n")
            append("}\n")
        }.toString()
    }

    fun withKotlinPlugin(project: Project, action: (GobleyKotlinExtensionDelegate) -> Unit) {
        project.plugins.withId(PluginIds.KOTLIN_MULTIPLATFORM) {
            action(GobleyKotlinMultiplatformExtensionDelegate(project))
        }
        project.plugins.withId(PluginIds.KOTLIN_ANDROID) {
            action(GobleyKotlinAndroidExtensionDelegate(project))
        }
        project.plugins.withId(PluginIds.KOTLIN_JVM) {
            action(GobleyKotlinJvmExtensionDelegate(project))
        }
    }

    fun withAndroidPlugin(project: Project, action: (GobleyAndroidExtensionDelegate) -> Unit) {
        project.plugins.withId(PluginIds.ANDROID_APPLICATION) {
            action(GobleyAndroidBaseExtensionDelegate(project))
        }
        project.plugins.withId(PluginIds.ANDROID_LIBRARY) {
            action(GobleyAndroidBaseExtensionDelegate(project))
        }
        project.plugins.withId(PluginIds.ANDROID_KOTLIN_MULTIPLATFORM_LIBRARY) {
            action(GobleyAndroidKotlinMultiplatformExtensionDelegate(project))
        }
    }
}
