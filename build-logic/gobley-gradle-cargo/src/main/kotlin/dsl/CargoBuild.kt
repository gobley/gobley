/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.cargo.dsl

import gobley.gradle.rust.targets.RustTarget
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.provider.Property
import org.jetbrains.kotlin.gradle.plugin.HasProject
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

/**
 * Contains settings for Rust builds.
 */
interface CargoBuild<out CargoBuildVariantT : CargoBuildVariant<RustTarget>>
    : Named, HasProject, HasFeatures, HasVariants<CargoBuildVariantT> {
    /**
     * The Rust target triple to use to build the current target. The name of the `CargoBuild` is
     * `rustTarget.friendlyName`.
     */
    val rustTarget: RustTarget

    /**
     * The list of Kotlin targets requiring this Rust build.
     */
    val kotlinTargets: NamedDomainObjectCollection<KotlinTarget>

    /**
     * The Cargo command to use for linting. If you want to Clippy, set this to `clippy`.
     */
    val checkCommand: Property<String>

    /**
     * When `true`, the pre-built standard library for the target will be installed via the
     * `rustup target add` command before building or linting. This is turned off for tier 3 targets
     * by default. If you are using a custom target whose tier cannot be determined, set this
     * property to `false`.
     */
    val installTargetBeforeBuild: Property<Boolean>
}
