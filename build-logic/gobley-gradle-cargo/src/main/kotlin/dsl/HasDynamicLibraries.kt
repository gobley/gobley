/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.cargo.dsl

import org.gradle.api.provider.SetProperty
import java.io.File

interface HasDynamicLibraries : HasEmbeddableRustLibrary {
    /**
     * The set of directories containing the dynamic libraries required in runtime. This property is effective only for
     * Android and JVM targets.
     *
     * The Cargo plugin will automatically configure several directories as the paths to locate the dynamic libraries.
     *
     * - The Cargo build output directory (e.g., `target/<triplet>/<profile>`).
     * - The Cargo build script output directory (e.g., `target/<triplet>/<profile>/build/<crate>-<hash>/out`). Use the
     *   [`OUT_DIR`](https://doc.rust-lang.org/cargo/reference/environment-variables.html) environment variable to
     *   determine the value of this path in your build script.
     * - (Android only) The NDK library directories (e.g., `<NDK ROOT>/toolchains/llvm/prebuilt/<host>/sysroot/usr/lib/<triplet>[/<API Level>]`).
     *   These directories contain C++ runtime libraries like `libc++_shared.so` and NDK libraries like `libaaudio.so`.
     *
     * For example, the following DSL will copy `libc++_shared.so` and `<API Level>/libaaudio.so` from the NDK directory
     * to the app, without manually modifying this property.
     * ```kotlin
     * cargo {
     *   builds.android {
     *     dynamicLibraries = arrayOf("aaudio", "c++_shared")
     *   }
     * }
     * ```
     */
    val dynamicLibrarySearchPaths: SetProperty<File>

    /**
     * The names of dynamic libraries required in runtime without the prefix and the file extension. This property is
     * effective only for Android and JVM targets.
     *
     * The following DSL will copy `libfoo.so` from `/path/to/libraries`.
     * ```kotlin
     * cargo {
     *   builds.jvm {
     *     dynamicLibrarySearchPaths.add(File("/path/to/libraries"))
     *     dynamicLibraries.add("foo")
     *   }
     * }
     * ```
     */
    val dynamicLibraries: SetProperty<String>
}
