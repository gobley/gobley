package gobley.gradle.cargo.dsl

import gobley.gradle.Variant
import org.gradle.api.provider.Property

interface HasWasmVariant {
    /**
     * The variant to use for WASM builds. Defaults to [Variant.Debug]. Setting this will override
     * the `wasmVariant` properties in outer blocks.
     * For example, in the following DSL:
     * ```kotlin
     * cargo {
     *   wasmVariant = Variant.Release
     *   builds.wasm {
     *     wasmVariant = Variant.Debug
     *   }
     * }
     * ```
     * The WASM build will use the debug profile.
     *
     * **FOR LIBRARY DEVELOPERS**: Unlike [HasJvmVariant], [HasWasmVariant] does not distinguish
     * the publishing variant and the wasm variant. It is very likely that you are using
     * [Variant.Debug] for library publishing. Use Gradle properties or environment variables to
     * control this property, and set this to [Variant.Release] for publishing.
     * ```kotlin
     * cargo {
     *   wasmVariant = Variant(findProperty("my.project.wasm.variant"))
     * }
     * ```
     */
    val wasmVariant: Property<Variant>
}