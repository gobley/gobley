/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.uniffi.dsl

import gobley.gradle.Variant
import gobley.gradle.rust.targets.RustTarget
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.newInstance
import javax.inject.Inject

sealed class BindingsGeneration(internal val project: Project) {
    /**
     * The UDL namespace. Defaults to `"$libraryCrateName"`.
     */
    abstract val namespace: Property<String>

    /**
     * The Rust target of the build to use to generate bindings. If unspecified, one of the available builds will be
     * automatically selected.
     */
    abstract val build: Property<RustTarget>

    /**
     * The variant of the build to use to generate bindings. If unspecified, one of the available variants will be
     * automatically selected.
     */
    abstract val variant: Property<Variant>

    /**
     * Path to the optional uniffi config file.
     * If not provided, uniffi-bindgen will try to guess it.
     */
    abstract val config: RegularFileProperty

    /**
     * The package name used in the generated bindings. Defaults to `"uniffi.$namespace"`.
     */
    abstract val packageName: Property<String>

    /**
     * The name of the resulting dynamic library without the prefix (e.g. `lib`) and the file
     * extension. Defaults to the library's name when bindings are generated from it, or
     * `uniffi_<namespace>` when generated from a UDL file.
     */
    abstract val cdylibName: Property<String>

    internal abstract val customTypes: MapProperty<String, CustomType>

    /**
     * Defines a new custom type. See [the documentation](https://mozilla.github.io/uniffi-rs/0.29/types/custom_types.html#custom-types-in-the-bindings-code).
     */
    fun customType(name: String, configure: Action<CustomType> = Action { }) {
        customTypes.put(
            name,
            project.objects.newInstance<CustomType>().apply { configure(this) }
        )
    }

    /**
     * When `true`, generated data classes has `val` properties instead of `var`.
     */
    abstract val generateImmutableRecords: Property<Boolean>

    /**
     * When `true`, `com.sun.jna.internal.Cleaner` will be used instead of
     * `android.system.SystemCleaner` or `java.lang.ref.Cleaner`. Defaults to `false`. Consider
     * changing this option when your project targets JVM 1.8.
     */
    abstract val disableJavaCleaner: Property<Boolean>

    /**
     * When `true`, enum classes will use PascalCase instead of UPPER_SNAKE_CASE.
     */
    abstract val usePascalCaseEnumClass: Property<Boolean>
}

abstract class BindingsGenerationFromLibrary @Inject internal constructor(project: Project) :
    BindingsGeneration(project)

abstract class BindingsGenerationFromUdl @Inject internal constructor(project: Project) :
    BindingsGeneration(project) {
    /**
     * The UDL file. Defaults to `"${crateDirectory}/src/${crateName}.udl"`.
     */
    abstract val udlFile: RegularFileProperty
}
