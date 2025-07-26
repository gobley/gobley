/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.uniffi.dsl

import gobley.gradle.BuildConfig
import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.cargo.dsl.CargoBinaryCrateSource
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.property

@Suppress("LeakingThis")
abstract class UniFfiExtension(internal val project: Project) {
    @OptIn(InternalGobleyGradleApi::class)
    internal val bindgenSource: Property<CargoBinaryCrateSource> =
        project.objects.property<CargoBinaryCrateSource>()
            .convention(
                CargoBinaryCrateSource.Registry(
                    packageName = BuildConfig.BINDGEN_CRATE,
                    version = BuildConfig.BINDGEN_VERSION,
                )
            )

    /**
     * Runs `ktlint` on the generated bindings
     */
    val formatCode: Property<Boolean> = project.objects.property<Boolean>()

    /**
     * When `true`, the UniFFI plugin adds required dependencies automatically. Defaults to `true`.
     * When there is another dependency to the same module with a different version, the UniFFI
     * plugin uses that version.
     */
    val addDependencies: Property<Boolean> =
        project.objects.property<Boolean>().convention(true)

    /**
     * When set to `true`, Bindings will be generated during IDE sync except for Kotlin/Native
     * targets. For Kotlin/Native targets, due to `cinterop`, bindings are always generated during
     * IDE sync.
     */
    val generateDuringSync: Property<Boolean> =
        project.objects.property<Boolean>().convention(true)

    /**
     * When `true`, the UniFFI plugin will generate a ProGuard rule file for the Android target.
     * The generated file will automatically appended to `proguardFile` or `consumerProguardFile`
     * inside the `android.buildTypes {}` block, depending on whether the current module is
     * an application project or a library project.
     */
    val generateProguardRules: Property<Boolean> =
        project.objects.property<Boolean>().convention(true)

    /**
     * Install the bindgen of the given [version] from the given [registry]. If [registry] is not specified, this will
     * download the bindgen from `crates.io`.
     */
    @OptIn(InternalGobleyGradleApi::class)
    fun bindgenFromRegistry(
        packageName: String = BuildConfig.BINDGEN_CRATE,
        version: String = BuildConfig.BINDGEN_VERSION,
        registry: String? = null,
    ) {
        bindgenSource.set(CargoBinaryCrateSource.Registry(packageName, version, registry))
    }

    /**
     * Install the bindgen located in the given [path].
     */
    fun bindgenFromPath(path: Directory) {
        bindgenSource.set(CargoBinaryCrateSource.Path(path.asFile.absolutePath))
    }

    /**
     * Download and install the bindgen from the given Git repository. If [commit] is specified, `cargo install` will
     * install the bindgen of that [commit].
     */
    fun bindgenFromGit(repository: String, commit: CargoBinaryCrateSource.Git.Commit? = null) {
        bindgenSource.set(CargoBinaryCrateSource.Git(repository, commit))
    }

    /**
     * Download and install the bindgen from the given Git repository, using the given [branch].
     */
    fun bindgenFromGitBranch(repository: String, branch: String) {
        bindgenFromGit(repository, CargoBinaryCrateSource.Git.Commit.Branch(branch))
    }

    /**
     * Download and install the bindgen from the given Git repository, using the given [tag].
     */
    fun bindgenFromGitTag(repository: String, tag: String) {
        bindgenFromGit(repository, CargoBinaryCrateSource.Git.Commit.Tag(tag))
    }

    /**
     * Download and install the bindgen from the given Git repository, using the given commit [revision].
     */
    fun bindgenFromGitRevision(repository: String, revision: String) {
        bindgenFromGit(repository, CargoBinaryCrateSource.Git.Commit.Revision(revision))
    }

    internal abstract val userProvidedBindingsGeneration: Property<BindingsGeneration>

    internal abstract val bindingsGeneration: Property<BindingsGeneration>

    init {
        bindingsGeneration.convention(
            userProvidedBindingsGeneration.orElse(
                project.provider {
                    project.objects.newInstance<BindingsGenerationFromLibrary>(project)
                        .also { userProvidedBindingsGeneration.set(it) }
                })
        )
    }

    /**
     * Generate bindings using a UDL file.
     */
    fun generateFromUdl(configure: Action<BindingsGenerationFromUdl> = Action { }) {
        val generation = userProvidedBindingsGeneration.orNull
            ?: project.objects.newInstance<BindingsGenerationFromUdl>(project)
                .also { userProvidedBindingsGeneration.set(it) }

        generation as? BindingsGenerationFromUdl
            ?: throw GradleException("A `generateFromLibrary` block has already been defined.")


        configure.execute(generation)
    }

    /**
     * Generate bindings from the build result library file.
     */
    fun generateFromLibrary(configure: Action<BindingsGenerationFromLibrary> = Action { }) {
        val generation = userProvidedBindingsGeneration.orNull
            ?: project.objects.newInstance<BindingsGenerationFromLibrary>(project)
                .also { userProvidedBindingsGeneration.set(it) }

        generation as? BindingsGenerationFromLibrary
            ?: throw GradleException("A `generateFromUdl` block has already been defined.")

        configure.execute(generation)
    }
}
