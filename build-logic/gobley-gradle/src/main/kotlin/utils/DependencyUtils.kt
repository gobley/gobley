/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.utils

import gobley.gradle.GobleyHost
import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.Variant
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
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import java.util.Locale

@Suppress("UnstableApiUsage")
@InternalGobleyGradleApi
object DependencyUtils {
    private val rustRuntimeRustTargetAttribute = Attribute.of("rustTarget", String::class.java)
    private val rustLibraryUsageAttribute = Attribute.of("rustLibraryUsage", String::class.java)
    private val rustVariantAttribute = Attribute.of("rustVariant", String::class.java)

    private fun Configuration.addAttributes(
        superConfiguration: Configuration,
        rustTarget: RustTarget,
        usage: String,
        variant: Variant? = null,
    ) {
        extendsFrom(superConfiguration)
        attributes.attribute(rustRuntimeRustTargetAttribute, rustTarget.friendlyName)
        attributes.attribute(rustLibraryUsageAttribute, usage)
        if (variant != null) {
            attributes.attribute(rustVariantAttribute, variant.toString())
        }
    }

    fun createCargoConfigurations(currentProject: Project) {
        val rustRuntimeOnlyConfiguration =
            currentProject.configurations.dependencyScope("rustRuntimeOnly")
        for (rustTarget in GobleyHost.current.platform.supportedTargets) {
            if (rustTarget !is RustJvmTarget) {
                continue
            }
            currentProject.configurations.resolvable(
                jvmRuntimeRustLibraryConfigurationName(rustTarget)
            ) { configuration ->
                configuration.addAttributes(
                    superConfiguration = rustRuntimeOnlyConfiguration.get(),
                    rustTarget = rustTarget,
                    usage = "jvmRuntime",
                )
            }
            currentProject.configurations.consumable(
                jvmConsumableRuntimeRustLibraryConfigurationName(rustTarget)
            ) { configuration ->
                configuration.addAttributes(
                    superConfiguration = rustRuntimeOnlyConfiguration.get(),
                    rustTarget = rustTarget,
                    usage = "jvmRuntime",
                )
            }

            for (variant in Variant.entries) {
                currentProject.configurations.resolvable(
                    androidUnitTestRuntimeRustLibraryConfigurationName(
                        rustTarget, variant
                    )
                ) { configuration ->
                    configuration.addAttributes(
                        superConfiguration = rustRuntimeOnlyConfiguration.get(),
                        rustTarget = rustTarget,
                        variant = variant,
                        usage = "androidUnitTestRuntime",
                    )
                }
                currentProject.configurations.consumable(
                    androidUnitTestConsumableRuntimeRustLibraryConfigurationName(
                        rustTarget, variant
                    )
                ) { configuration ->
                    configuration.addAttributes(
                        superConfiguration = rustRuntimeOnlyConfiguration.get(),
                        rustTarget = rustTarget,
                        variant = variant,
                        usage = "androidUnitTestRuntime",
                    )
                }
            }
        }
    }

    fun resolveCargoDependencies(currentProject: Project) {
        for (rustTarget in GobleyHost.current.platform.supportedTargets) {
            if (rustTarget !is RustJvmTarget) {
                continue
            }
            val jvmRuntimeConfiguration = currentProject.configurations.findByName(
                jvmRuntimeRustLibraryConfigurationName(
                    rustTarget
                )
            ) ?: continue
            registerJvmRustLibraryToClassPaths(currentProject, jvmRuntimeConfiguration)
            for (variant in Variant.entries) {
                val androidUnitTestConfiguration = currentProject.configurations.findByName(
                    androidUnitTestRuntimeRustLibraryConfigurationName(
                        rustTarget, variant
                    )
                ) ?: continue
                registerAndroidUnitTestLibraryToClassPaths(
                    currentProject,
                    androidUnitTestConfiguration,
                )
            }
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
            if (delegate.jvmTarget != null) {
                with(delegate.sourceSets.jvmMain) {
                    dependencies {
                        runtimeOnly(dependencyJars)
                    }
                }
            }
        }
    }

    private fun registerAndroidUnitTestLibraryToClassPaths(
        currentProject: Project,
        configuration: Configuration,
    ) {
        val variant = Variant(configuration.attributes.getAttribute(rustVariantAttribute)!!)
        val dependencies = configuration.incoming
        val dependencyJars =
            currentProject.files(dependencies.artifacts.resolvedArtifacts.map { artifacts ->
                artifacts.map { it.file }
            })
        PluginUtils.withKotlinPlugin(currentProject) { delegate ->
            if (delegate.androidTarget != null) {
                with(delegate.sourceSets.androidUnitTest(variant)) {
                    dependencies {
                        runtimeOnly(dependencyJars)
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

    fun addAndroidUnitTestRuntimeRustLibraryJar(
        currentProject: Project,
        rustTarget: RustTarget,
        variant: Variant,
        jarTaskProvider: Provider<Jar>
    ) {
        val configurationName = androidUnitTestConsumableRuntimeRustLibraryConfigurationName(
            rustTarget, variant
        )
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

    private fun androidUnitTestRuntimeRustLibraryConfigurationName(
        rustTarget: RustTarget,
        variant: Variant,
    ): String {
        return StringBuilder().apply {
            append(rustTarget.friendlyName.replaceFirstChar { it.lowercase(Locale.US) })
            append("RustRuntimeAndroidUnitTest")
            append(variant.toString().uppercaseFirstChar())
        }.toString()
    }

    private fun androidUnitTestConsumableRuntimeRustLibraryConfigurationName(
        rustTarget: RustTarget,
        variant: Variant,
    ): String {
        return StringBuilder().apply {
            append(rustTarget.friendlyName.replaceFirstChar { it.lowercase(Locale.US) })
            append("RustRuntimeAndroidUnitTestConsumable")
            append(variant.toString().uppercaseFirstChar())
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