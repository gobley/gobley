/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.rust

import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.PluginIds
import gobley.gradle.rust.dsl.RustExtension
import gobley.gradle.utils.DependencyUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create

/**
 * This plugin is for exposing the following functions.
 */
class RustPlugin : Plugin<Project> {
    companion object {
        internal const val TASK_GROUP = "rust"
    }

    private lateinit var rustExtension: RustExtension

    override fun apply(target: Project) {
        @OptIn(InternalGobleyGradleApi::class)
        if (!target.plugins.hasPlugin(PluginIds.GOBLEY_CARGO)) {
            DependencyUtils.createCargoConfigurations(target)
        }
        @OptIn(InternalGobleyGradleApi::class)
        if (!target.plugins.hasPlugin(PluginIds.GOBLEY_UNIFFI)) {
            DependencyUtils.createUniFfiConfigurations(target)
        }
        rustExtension = target.extensions.create<RustExtension>(TASK_GROUP)
        @OptIn(InternalGobleyGradleApi::class)
        target.afterEvaluate {
            if (!target.plugins.hasPlugin(PluginIds.GOBLEY_CARGO)) {
                DependencyUtils.resolveCargoDependencies(target)
            }
            if (!target.plugins.hasPlugin(PluginIds.GOBLEY_UNIFFI)) {
                DependencyUtils.resolveUniFfiDependencies(target)
            }
        }
    }
}
