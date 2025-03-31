/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.utils

import gobley.gradle.GobleyHost
import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.PluginIds
import gobley.gradle.rust.targets.RustJvmTarget
import gobley.gradle.rust.targets.RustTarget
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.attributes.Attribute
import org.gradle.api.provider.Provider
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import java.util.Locale

@Suppress("UnstableApiUsage")
@InternalGobleyGradleApi
object DependencyUtils {
    fun createConfigurations(currentProject: Project) {
        val rustRuntimeRustTargetAttribute = Attribute.of("rustTarget", String::class.java)
        val rustRuntimeOnlyConfiguration =
            currentProject.configurations.dependencyScope("rustRuntimeOnly")
        for (rustTarget in GobleyHost.current.platform.supportedTargets) {
            if (rustTarget !is RustJvmTarget) {
                continue
            }
            val runtimeConfigurationName = jvmRuntimeRustLibraryConfigurationName(rustTarget)
            currentProject.configurations.resolvable(runtimeConfigurationName) { configuration ->
                configuration.extendsFrom(rustRuntimeOnlyConfiguration.get())
                configuration.attributes.attribute(
                    rustRuntimeRustTargetAttribute, rustTarget.friendlyName
                )
            }
            val consumableConfigurationName =
                jvmConsumableRuntimeRustLibraryConfigurationName(rustTarget)
            currentProject.configurations.consumable(consumableConfigurationName) { configuration ->
                configuration.extendsFrom(rustRuntimeOnlyConfiguration.get())
                configuration.attributes.attribute(
                    rustRuntimeRustTargetAttribute, rustTarget.friendlyName
                )
            }
        }
    }

    fun resolveJvmRustLibraryConfigurations(currentProject: Project) {
        for (rustTarget in GobleyHost.current.platform.supportedTargets) {
            if (rustTarget !is RustJvmTarget) {
                continue
            }
            val configurationName = jvmRuntimeRustLibraryConfigurationName(rustTarget)
            val configuration =
                currentProject.configurations.findByName(configurationName) ?: continue
            registerJvmRustLibraryToClassPaths(currentProject, configuration)
        }
    }

    private fun registerJvmRustLibraryToClassPaths(
        currentProject: Project,
        configuration: Configuration,
    ) {
        val dependencies = configuration.incoming
        val dependencyJars =
            currentProject.files(dependencies.artifacts.resolvedArtifacts.map { artifacts ->
                artifacts.map { it.file }
            })
        PluginUtils.withKotlinPlugin(currentProject) { delegate ->
            when (delegate.pluginId) {
                PluginIds.KOTLIN_MULTIPLATFORM -> {
                    val jvmTarget = delegate.targets.firstOrNull { it is KotlinJvmTarget }
                    if (jvmTarget != null) {
                        with(delegate.sourceSets.getByName("${jvmTarget.name}Main")) {
                            dependencies {
                                runtimeOnly(dependencyJars)
                            }
                        }
                    }
                    val androidTarget = delegate.targets.firstOrNull { it is KotlinAndroidTarget }
                    if (androidTarget != null) {
                        with(delegate.sourceSets.getByName("${androidTarget.name}UnitTest")) {
                            dependencies {
                                runtimeOnly(dependencyJars)
                            }
                        }
                    }
                }

                PluginIds.KOTLIN_JVM -> {
                    with(delegate.sourceSets.getByName("main")) {
                        dependencies {
                            runtimeOnly(dependencyJars)
                        }
                    }
                }

                PluginIds.KOTLIN_ANDROID -> {
                    with(delegate.sourceSets.getByName("test")) {
                        dependencies {
                            runtimeOnly(dependencyJars)
                        }
                    }
                }
            }
        }
    }

    fun addJvmRuntimeRustLibraryJar(
        currentProject: Project, rustTarget: RustTarget, jarTaskProvider: Provider<Jar>
    ) {
        val configurationName = jvmConsumableRuntimeRustLibraryConfigurationName(rustTarget)
        currentProject.artifacts.add(configurationName, jarTaskProvider)
    }

    private fun jvmRuntimeRustLibraryConfigurationName(
        rustTarget: RustTarget
    ): String {
        return StringBuilder().apply {
            append(rustTarget.friendlyName.replaceFirstChar { it.lowercase(Locale.US) })
            append("RustRuntimeJvm")
        }.toString()
    }

    private fun jvmConsumableRuntimeRustLibraryConfigurationName(
        rustTarget: RustTarget
    ): String {
        return StringBuilder().apply {
            append(rustTarget.friendlyName.replaceFirstChar { it.lowercase(Locale.US) })
            append("RustRuntimeJvmConsumable")
        }.toString()
    }

    fun configureEachDependentProjects(
        currentProject: Project,
        action: (Project) -> Unit,
    ) {
        // A set of projects known to be dependent on `currentProject`.
        val consumedDependentProjects = mutableSetOf(currentProject)
        // A partial inverse graph of the project dependency graph, only containing the part not connected to
        // `currentProject`.
        val unconsumedDirectDependentsByProject =
            currentProject.rootProject.allprojects.associateWith {
                mutableSetOf<Project>()
            }
        for (project in currentProject.rootProject.allprojects) {
            project.configurations.configureEach { configuration ->
                configuration.dependencies.configureEach { dependency ->
                    if (dependency is ProjectDependency) {
                        // If this dependency points to a project that is already consumed, this project is also
                        // (indirectly) dependent on currentProject.
                        if (consumedDependentProjects.contains(dependency.dependencyProject)) {
                            // Perform DFS starting at `project`.
                            val stack = arrayListOf(project)
                            while (stack.isNotEmpty()) {
                                val stackItem = stack.removeLast()
                                // Visit `stackItem` if not visited.
                                if (!consumedDependentProjects.contains(stackItem)) {
                                    consumedDependentProjects.add(stackItem)
                                    action(stackItem)
                                    // Consume items in the inverse graph as well.
                                    stack.addAll(unconsumedDirectDependentsByProject[stackItem]!!)
                                    unconsumedDirectDependentsByProject[stackItem]!!.clear()
                                }
                            }
                        } else {
                            // Otherwise, just store the dependency for future use.
                            unconsumedDirectDependentsByProject[dependency.dependencyProject]!!.add(
                                project
                            )
                        }
                    }
                }
            }
        }
    }

    fun configureEachCommonDependencies(
        configurations: ConfigurationContainer,
        action: (Dependency) -> Unit,
    ) {
        configurations.configureEach { configuration ->
            if (configuration.name == "commonMainApi" || configuration.name == "commonMainImplementation" || configuration.name == "commonMainCompileOnly") {
                configuration.dependencies.configureEach(action)
            }
        }
    }

    fun configureEachCommonProjectDependencies(
        configurations: ConfigurationContainer,
        action: (Project) -> Unit,
    ) {
        configureEachCommonDependencies(configurations) { dependency ->
            if (dependency is ProjectDependency) {
                action(dependency.dependencyProject)
            }
        }
    }
}