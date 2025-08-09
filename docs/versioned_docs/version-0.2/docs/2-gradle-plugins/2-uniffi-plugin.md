---
slug: /gradle-plugins/uniffi
---

# The UniFFI plugin

> :bulb: We recommend to first read the [UniFFI user guide](https://mozilla.github.io/uniffi-rs/).

The UniFFI plugin is responsible for generating Kotlin bindings from your Rust package. Here is an
example of using the UniFFI plugin to build bindings from the resulting library binary.

```kotlin
import gobley.gradle.Variant
import gobley.gradle.rust.targets.RustAndroidTarget

plugins {
    kotlin("multiplatform")
    id("dev.gobley.cargo") version "0.2.0"
    id("dev.gobley.uniffi") version "0.2.0"
}

uniffi {
    // Generate the bindings using library mode.
    generateFromLibrary {
        // The UDL namespace as in the UDL file. Defaults to the library crate name.
        namespace = "my_crate"
        // The Rust target of the build to use to generate the bindings. If unspecified, one of the available builds
        // will be automatically selected.
        build = RustAndroidTarget.Arm64
        // The variant of the build that makes the library to use. If unspecified, the UniFFI plugin automatically picks
        // one.
        variant = Variant.Debug
    }
}
```

If you want to generate bindings from a UDL file as well, you can specify the path using the
`generateFromUdl {}` block.

```kotlin
uniffi {
    generateFromUdl {
        namespace = "..."
        build = ...
        variant = Variant.Debug
        // The UDL file. Defaults to "${crateDirectory}/src/${crateName}.udl".
        udlFile = layout.projectDirectory.file("rust/src/my_crate.udl")
    }
}
```

If you want to run `ktlint` on the generated bindings set `formatCode` to `true`.

```kotlin
uniffi {
    formatCode = true
}
```

When you use Kotlin targets not supported by the UniFFI plugin like `js()`, `wasmJs()`, or
`wasmWasi()`, the UniFFI plugin generates stubs. This ensures that the Kotlin code is compiled
successfully for all platforms. However, all generated functions except for `RustObject(NoPointer)`
constructors will throw `kotlin.NotImplementedError`. We are trying to support as many platforms as
possible. If you need to target WASM/JS, please use these stubs until WASM/JS support is released.

## Configuring Bindgen settings using Gradle DSL

Instead of making `<manifest dir>/uniffi.toml`, you can change the bindgen settings directly inside
the `generateFromLibrary {}` block or the `generateFromUdl {}` block using Gradle DSL.

```kotlin
uniffi {
    generateFromLibrary {
        packageName = "com.example.foo"
        customType("Uuid") {
            typeName = "java.util.UUID"
            intoCustom = "java.util.UUID.fromString({})"
            fromCustom = "{}.toString()"
        }
        usePascalCaseEnumClass = true
    }
}
```

For details about each bindgen setting properties,
see [Bindgen configuration](../3-bindgen.md#bindgen-configuration).