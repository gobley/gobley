/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.cargo.dsl

import org.gradle.api.provider.Property

interface HasEmbeddableRustLibrary {
    /**
     * Defaults to `true`.
     *
     * When `true`, the Rust shared library is built using Cargo and embedded into the Kotlin build result.
     * For JVM targets, the shared library will included in the resulting `.jar` file. For Android targets,
     * the shared library will be included in the resulting `.aab`, `.aar`, or `.apk` file. For WASM targets,
     * the shared library will be transformed into a `.kt` file and directly compiled as a Kotlin file.
     *
     * Set this to `false` when you implement your own build logic to load the shared library, another
     * crate using UniFFI is referencing this crate (See the UniFFI external types documentation), or you
     * just don't want to make your application/library target for that platform.
     *
     * When the host does not support building for this target, this property is ignored and considered `false`.
     * When NDK ABI filters in the `android {}` block are configured to ignore this target, this property
     * is ignored as well.
     *
     * [embedRustLibrary] in [CargoBuild] is set to the convention value of [embedRustLibrary] in
     * [CargoBuildVariant]. The value in [CargoBuildVariant] is used.
     *
     * Even when [embedRustLibrary] is false, if the [CargoBuild] is chosen to be used to build UniFFI bindings,
     * Cargo build will be invoked.
     */
    val embedRustLibrary: Property<Boolean>
}
