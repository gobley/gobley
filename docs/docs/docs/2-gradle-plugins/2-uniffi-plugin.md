---
slug: /gradle-plugins/uniffi
---

# The UniFFI plugin

## Basic usage

> :bulb: We recommend to first read the [UniFFI user guide](https://mozilla.github.io/uniffi-rs/).

The UniFFI plugin is responsible for generating Kotlin bindings from your Rust package. Here is an
example of using the UniFFI plugin to build bindings from the resulting library binary.

```kotlin
import gobley.gradle.Variant
import gobley.gradle.rust.targets.RustAndroidTarget

plugins {
    kotlin("multiplatform")
    id("dev.gobley.cargo") version "0.3.2"
    id("dev.gobley.uniffi") version "0.3.2"
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
            lift = "java.util.UUID.fromString({})"
            lower = "{}.toString()"
        }
        usePascalCaseEnumClass = true
    }
}
```

For details about each bindgen setting properties,
see [Bindgen configuration](../3-bindgen.md#bindgen-configuration).

## JNA ProGuard rules for Android

UniFFI on the Rust side generates C-compatible functions that can be called from other
languages. These functions serialize and deserialize the return values and the arguments, thus
acting as the bridge between Rust and other languages, including Kotlin. The functions and the
classes in generated Kotlin bindings internally call these UniFFI-generated functions. On
Kotlin/JVM, it uses [JNA](https://github.com/java-native-access/jna) to call the functions. On
Kotlin/Native, it uses [cinterop](https://kotlinlang.org/docs/native-c-interop.html#bindings).

JNA relies on Java reflection to interact with the Rust library. Some class and method names must be
preserved at runtime for JNA to function correctly. However, when building Android applications in
release mode, R8 is enabled by default for obfuscation, which renames these essential JNA classes
and methods, leading to runtime errors such as `UnsatisfiedLinkError`. While the official JNA
documentation provides the list of
[required ProGuard rules](https://github.com/java-native-access/jna/blob/master/www/FrequentlyAskedQuestions.md#jna-on-android)
to prevent the error, these rules are not included in the official AAR file.

To prevent such runtime issues, the UniFFI plugin generates the necessary ProGuard rules by default.
If you prefer to manually manage all ProGuard rules and disable this behavior, you can set the
`generateProguardRules` property to `false` in the `uniffi {}` block.

```kotlin
uniffi {
    generateProguardRules = false
}
``` 