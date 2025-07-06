/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.cargo.dsl

import gobley.gradle.GobleyHost
import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.Variant
import gobley.gradle.cargo.CargoPackage
import gobley.gradle.rust.dsl.RustExtension
import gobley.gradle.rust.dsl.rustVersion
import gobley.gradle.rust.targets.RustAndroidTarget
import gobley.gradle.rust.targets.RustAppleMobileTarget
import gobley.gradle.rust.targets.RustPosixTarget
import gobley.gradle.rust.targets.RustTarget
import gobley.gradle.rust.targets.RustWindowsTarget
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.internal.plugins.DslObject
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.reflect.TypeOf
import org.gradle.kotlin.dsl.container
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.mapProperty
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty
import org.jetbrains.kotlin.gradle.plugin.HasProject
import java.io.File

abstract class CargoExtension(final override val project: Project) : HasProject, HasFeatures,
    HasVariants<CargoExtensionVariant>, HasJvmVariant, HasNativeVariant {
    /**
     * The package directory.
     */
    val packageDirectory: DirectoryProperty =
        project.objects.directoryProperty().convention(project.layout.projectDirectory)

    /**
     * The directory where `cargo` and `rustup` are installed. Defaults to `~/.cargo/bin`. If `RustExtension` is
     * present, uses [RustExtension.toolchainDirectory].
     */
    internal val toolchainDirectory: Provider<File> = project.provider {
        project.extensions.findByType<RustExtension>()?.toolchainDirectory?.get()
            ?: GobleyHost.current.platform.defaultToolchainDirectory
    }

    internal val rustVersion: Provider<String> =
        project.objects.property<String>().value(project.rustVersion).apply {
            disallowChanges()
            finalizeValueOnRead()
        }

    /**
     * The parsed metadata and manifest of the package.
     */
    val cargoPackage: Provider<CargoPackage> =
        project.objects.property<CargoPackage>().value(
            packageDirectory.zip(toolchainDirectory) { pkg, toolchain ->
                CargoPackage(project, pkg, toolchain)
            },
        ).apply {
            disallowChanges()
            finalizeValueOnRead()
        }

    @Suppress("LeakingThis")
    final override val debug: CargoExtensionVariant =
        project.objects.newInstance(DefaultCargoExtensionVariant::class, Variant.Debug, this)

    @Suppress("LeakingThis")
    final override val release: CargoExtensionVariant =
        project.objects.newInstance(DefaultCargoExtensionVariant::class, Variant.Release, this)

    override val variants: Iterable<CargoExtensionVariant> = arrayListOf(debug, release)

    private val buildContainer = project.container<CargoBuild<CargoBuildVariant<RustTarget>>>()

    internal fun createOrGetBuild(rustTarget: RustTarget): DefaultCargoBuild<RustTarget, DefaultCargoBuildVariant<RustTarget, CargoBuild<*>>> {
        val build = buildContainer.findByName(rustTarget.friendlyName) ?: run {
            project.objects.newInstance(
                when (rustTarget) {
                    is RustAndroidTarget -> CargoAndroidBuild::class
                    is RustAppleMobileTarget -> CargoAppleMobileBuild::class
                    is RustPosixTarget -> CargoPosixBuild::class
                    is RustWindowsTarget -> CargoWindowsBuild::class
                },
                rustTarget,
                this,
            ).apply(buildContainer::add)
        }
        @Suppress("UNCHECKED_CAST")
        return build as DefaultCargoBuild<RustTarget, DefaultCargoBuildVariant<RustTarget, CargoBuild<*>>>
    }

    /**
     * The list of all available Cargo build command invocations.
     */
    val builds: CargoBuildCollection<CargoBuild<CargoBuildVariant<RustTarget>>> =
        CargoBuildCollectionImpl(buildContainer).apply {
            DslObject(this@CargoExtension).extensions.add(
                object :
                    TypeOf<CargoBuildCollection<CargoBuild<CargoBuildVariant<RustTarget>>>>() {},
                "builds",
                this,
            )
        }

    /**
     * The list of Android targets filtered by defaultConfig.ndk.abiFilters.
     */
    @InternalGobleyGradleApi
    val androidTargetsToBuild: SetProperty<RustAndroidTarget> =
        project.objects.setProperty()

    /**
     * The variant of a target specified by the parent process like Xcode.
     */
    internal val nativeTargetVariantOverride: MapProperty<RustTarget, Variant> =
        project.objects.mapProperty()

    /**
     * The Cargo command to use for linting. If you want to Clippy, set this to `clippy`.
     */
    val checkCommand: Property<String> =
        project.objects.property<String>().convention("check")

    /**
     * When `true`, the artifacts each holding a Rust dynamic library for a single platform
     * will be added to the JVM publication, which is generated by the Kotlin Multiplatform plugin.
     * The artifacts can be retrieved via [CargoJvmBuildVariant.jarTaskProvider]. This is useful for
     * easier publication.
     *
     * This property has no effect for Kotlin/JVM or Kotlin Android plugins.
     */
    val publishJvmArtifacts: Property<Boolean> =
        project.objects.property<Boolean>().convention(true)

    /**
     * When `true`, the pre-built standard library for the target will be installed via the
     * `rustup target add` command before building or linting. This is turned off for tier 3 targets
     * by default. If you are using a custom target whose tier cannot be determined, set this
     * property to `false`.
     */
    val installTargetBeforeBuild: Property<Boolean> =
        project.objects.property<Boolean>().convention(true)
}
