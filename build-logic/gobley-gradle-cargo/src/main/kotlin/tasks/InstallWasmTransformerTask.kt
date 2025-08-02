/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.cargo.tasks

import gobley.gradle.BuildConfig
import gobley.gradle.InternalGobleyGradleApi
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.OutputFile

@CacheableTask
abstract class InstallWasmTransformerTask : CargoInstallTask() {
    @OptIn(InternalGobleyGradleApi::class)
    @get:OutputFile
    val wasmTransformer = binaryCrateOutput(BuildConfig.WASM_TRANSFORMER_BIN)
}