/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.utils

import gobley.gradle.DependencyVersions
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
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.gradle.util.GradleVersion
import java.io.File
import java.util.Locale

@Suppress("UnstableApiUsage")
@InternalGobleyGradleApi
object DependencyUtils {
    private fun configureEachCommonProjectDependencies(
        configurations: ConfigurationContainer,
        action: (ProjectDependency) -> Unit,
    ) {
        configureEachCommonDependencies(configurations) { dependency ->
            if (dependency is ProjectDependency) {
                action(dependency)
            }
        }
    }

    private fun addDependencyEachCommonProjectDependencies(
        currentProject: Project,
        configurationName: String
    ) {
        configureEachCommonProjectDependencies(currentProject.configurations) { dependency ->
            currentProject.dependencies.add(
                configurationName,
                currentProject.project(dependency.versionCompatiblePath),
            )
        }
    }

    private val kindAttribute = Attribute.of("dev.gobley.kind", String::class.java)
    private const val KIND_RUST = "RUST"
    private const val KIND_UNIFFI = "UNIFFI"

    private val rustTargetAttribute = Attribute.of("dev.gobley.rust.target", String::class.java)
    private val rustVariantAttribute = Attribute.of("dev.gobley.rust.variant", String::class.java)

    private fun Configuration.addRustAttributes(
        superConfiguration: Configuration? = null,
        rustTarget: RustTarget,
        variant: Variant,
    ) {
        if (superConfiguration != null) {
            extendsFrom(superConfiguration)
        }
        attributes.attribute(kindAttribute, KIND_RUST)
        attributes.attribute(rustTargetAttribute, rustTarget.friendlyName)
        attributes.attribute(rustVariantAttribute, variant.toString())
    }

    fun createCargoConfigurations(currentProject: Project) {
        val rustRuntimeOnlyConfiguration =
            currentProject.configurations.dependencyScope("rustRuntimeOnly")
        addDependencyEachCommonProjectDependencies(currentProject, "rustRuntimeOnly")

        for (rustTarget in GobleyHost.current.platform.supportedTargets) {
            if (rustTarget !is RustJvmTarget) {
                continue
            }
            for (variant in Variant.entries) {
                currentProject.configurations.resolvable(
                    androidUnitTestRuntimeRustLibraryConfigurationName(
                        rustTarget, variant
                    )
                ) { configuration ->
                    configuration.addRustAttributes(
                        superConfiguration = rustRuntimeOnlyConfiguration.get(),
                        rustTarget = rustTarget,
                        variant = variant,
                    )
                }
                currentProject.configurations.consumable(
                    androidUnitTestConsumableRuntimeRustLibraryConfigurationName(
                        rustTarget, variant
                    )
                ) { configuration ->
                    configuration.addRustAttributes(
                        superConfiguration = rustRuntimeOnlyConfiguration.get(),
                        rustTarget = rustTarget,
                        variant = variant,
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

    private fun registerAndroidUnitTestLibraryToClassPaths(
        currentProject: Project,
        configuration: Configuration,
    ) {
        val variant = Variant(configuration.attributes.getAttribute(rustVariantAttribute)!!)
        val dependencies = configuration.incoming
        val dependencyJars =
            currentProject.files(dependencies.artifacts.resolvedArtifacts.map { artifacts ->
                artifacts.mapNotNull { artifact ->
                    artifact.file.takeIf {
                        artifact.variant.attributes.getAttribute(kindAttribute) == KIND_RUST
                    }
                }
            })
        val composePreviewVariant = GradleUtils.getComposePreviewVariant(currentProject.gradle)
        PluginUtils.withKotlinPlugin(currentProject) { delegate ->
            if (delegate.androidTarget != null) {
                if (variant == composePreviewVariant) {
                    with(delegate.sourceSets.androidMain(variant)) {
                        dependencies {
                            runtimeOnly(dependencyJars)
                        }
                    }
                }
                with(delegate.sourceSets.androidUnitTest(variant)) {
                    dependencies {
                        runtimeOnly(dependencyJars)
                    }
                }
            }
        }
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

    private fun Configuration.addUniffiAttributes(superConfiguration: Configuration) {
        extendsFrom(superConfiguration)
        attributes.attribute(kindAttribute, KIND_UNIFFI)
    }

    fun createUniFfiConfigurations(currentProject: Project) {
        val uniFfiImplementationConfiguration =
            currentProject.configurations.dependencyScope("uniFfiImplementation")
        addDependencyEachCommonProjectDependencies(currentProject, "uniFfiImplementation")

        currentProject.configurations.resolvable("uniFfiConfiguration") { configuration ->
            configuration.addUniffiAttributes(
                superConfiguration = uniFfiImplementationConfiguration.get(),
            )
        }
        currentProject.configurations.consumable("uniFfiConfigurationConsumable") { configuration ->
            configuration.addUniffiAttributes(
                superConfiguration = uniFfiImplementationConfiguration.get(),
            )
        }
    }

    fun addMergedUniffiConfigArtifact(
        currentProject: Project,
        uniFfiConfigTask: TaskProvider<*>,
    ) {
        currentProject.artifacts.add("uniFfiConfigurationConsumable", uniFfiConfigTask)
    }

    fun getExternalPackageUniFfiConfigurations(currentProject: Project): Provider<List<File>>? {
        val configuration = currentProject.configurations.findByName("uniFfiConfiguration")
            ?: return null
        val dependencies = configuration.incoming
        return dependencies.artifacts.resolvedArtifacts.map { artifacts ->
            artifacts.mapNotNull { artifact ->
                artifact.file.takeIf {
                    artifact.variant.attributes.getAttribute(kindAttribute) == KIND_UNIFFI
                }
            }
        }
    }

    fun resolveUniFfiDependencies(currentProject: Project) {
        val composePreviewVariant = GradleUtils.getComposePreviewVariant(currentProject.gradle)
        PluginUtils.withKotlinPlugin(currentProject) { delegate ->
            if (delegate.androidTarget != null) {
                if (composePreviewVariant != null) {
                    with(delegate.sourceSets.androidMain(composePreviewVariant)) {
                        dependencies {
                            runtimeOnly("net.java.dev.jna:jna") {
                                version {
                                    it.prefer(DependencyVersions.JNA)
                                }
                            }
                        }
                    }
                }
                with(delegate.sourceSets.androidUnitTest) {
                    dependencies {
                        runtimeOnly("net.java.dev.jna:jna") {
                            version {
                                it.prefer(DependencyVersions.JNA)
                            }
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

    @Suppress("DEPRECATION")
    private val ProjectDependency.versionCompatiblePath: String
        get() {
            val currentBaseVersion = GradleVersion.current().baseVersion
            return when {
                currentBaseVersion >= GradleVersion.version("8.11") -> path
                else -> dependencyProject.path
            }
        }
}